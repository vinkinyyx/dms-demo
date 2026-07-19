/*
 * 订单业务服务：
 *   createOrder → 校验授权 + 计算促销 + 生成订单 + 状态历史
 *   submit / approve / reject / cancel / split
 */
package com.dms.order.service;

import com.dms.authz.dto.AuthorizationCheckRequest;
import com.dms.authz.dto.AuthorizationCheckResult;
import com.dms.authz.service.AuthorizationService;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.order.dto.OrderCreateRequest;
import com.dms.order.dto.OrderDTO;
import com.dms.order.entity.Order;
import com.dms.order.entity.OrderLine;
import com.dms.order.entity.OrderPromotionHit;
import com.dms.order.entity.OrderStatusHistory;
import com.dms.order.repository.OrderLineRepository;
import com.dms.order.repository.OrderPromotionHitRepository;
import com.dms.order.repository.OrderRepository;
import com.dms.order.repository.OrderStatusHistoryRepository;
import com.dms.promotion.dto.PromotionEvaluationResult;
import com.dms.promotion.dto.PromotionHit;
import com.dms.promotion.dto.PromotionLine;
import com.dms.promotion.service.PromotionEngine;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository lineRepository;
    private final OrderPromotionHitRepository hitRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final DocNoGenerator docNoGenerator;
    private final AuthorizationService authorizationService;
    private final PromotionEngine promotionEngine;

    @PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Transactional(readOnly = true)
    public PageResult<Order> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<Order> page = tenantId == null
                ? orderRepository.findAll(pageQuery.toPageable())
                : orderRepository.findByTenantId(tenantId, pageQuery.toPageable());
        page.getContent().forEach(o -> {
            if (o.getDealerId() != null) o.setDealerName(lookupName("dealers", o.getDealerId()));
            if (o.getRefOrderId() != null) o.setRefOrderCode(lookupCode("orders", o.getRefOrderId()));
        });
        return PageResult.of(page);
    }

    private String lookupName(String table, Long id) {
        try {
            Object r = em.createNativeQuery("SELECT name FROM " + table + " WHERE id = ?1")
                    .setParameter(1, id).getResultList().stream().findFirst().orElse(null);
            return r == null ? null : String.valueOf(r);
        } catch (Exception e) { return null; }
    }

    private String lookupCode(String table, Long id) {
        try {
            Object r = em.createNativeQuery("SELECT code FROM " + table + " WHERE id = ?1")
                    .setParameter(1, id).getResultList().stream().findFirst().orElse(null);
            return r == null ? null : String.valueOf(r);
        } catch (Exception e) { return null; }
    }

    @Transactional(readOnly = true)
    public OrderDTO get(Long id) {
        Order order = loadOrder(id);
        return OrderDTO.builder()
                .order(order)
                .lines(lineRepository.findByOrderIdOrderBySeqAsc(id))
                .promotionHits(hitRepository.findByOrderId(id))
                .build();
    }

    /**
     * 创建订单：授权校验 + 促销计算 + 落库 + 状态历史。
     */
    @Transactional
    public OrderDTO createOrder(OrderCreateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (request.getDealerId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 dealerId");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "订单明细为空");
        }

        // 1. 授权校验
        AuthorizationCheckRequest authReq = new AuthorizationCheckRequest();
        authReq.setDealerId(request.getDealerId());
        authReq.setAuthType("ORDER");
        authReq.setAtTime(LocalDate.now());
        List<AuthorizationCheckRequest.Line> authLines = new ArrayList<>();
        for (OrderCreateRequest.Line l : request.getLines()) {
            AuthorizationCheckRequest.Line al = new AuthorizationCheckRequest.Line();
            al.setProductId(l.getProductId());
            authLines.add(al);
        }
        authReq.setLines(authLines);
        List<AuthorizationCheckResult> checks = authorizationService.check(authReq);
        List<String> unauth = checks.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getAuthorized()))
                .map(r -> "商品 " + r.getProductId() + " " + r.getReason())
                .toList();
        if (!unauth.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "订单授权校验失败: " + String.join("; ", unauth));
        }

        // 2. 计算行小计
        BigDecimal amountInclTax = BigDecimal.ZERO;
        List<OrderLine> lineEntities = new ArrayList<>();
        List<PromotionLine> promoLines = new ArrayList<>();
        int seqCounter = 1;
        for (OrderCreateRequest.Line l : request.getLines()) {
            if (l.getQty() == null || l.getUnitPrice() == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "订单行 qty/unitPrice 不能为空");
            }
            BigDecimal subTotal = l.getQty().multiply(l.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            amountInclTax = amountInclTax.add(subTotal);
            OrderLine le = OrderLine.builder()
                    .productId(l.getProductId())
                    .qty(l.getQty())
                    .unitPrice(l.getUnitPrice())
                    .taxRate(l.getTaxRate())
                    .subTotal(subTotal)
                    .isGift(false)
                    .seq(l.getSeq() == null ? seqCounter++ : l.getSeq())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            lineEntities.add(le);
            promoLines.add(new PromotionLine(l.getProductId(), l.getQty(), l.getUnitPrice(), subTotal));
        }

        // 3. 促销引擎
        PromotionEvaluationResult evalResult = promotionEngine.evaluate(tenantId, request.getDealerId(), promoLines);
        if (Boolean.TRUE.equals(evalResult.getRejected())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "促销规则拒绝: " + String.join("; ", evalResult.getRejectedReasons()));
        }

        BigDecimal discount = evalResult.getDiscountTotal() == null ? BigDecimal.ZERO : evalResult.getDiscountTotal();
        BigDecimal finalAmount = amountInclTax.subtract(discount).max(BigDecimal.ZERO);

        // 4. 保存订单
        Order order = Order.builder()
                .tenantId(tenantId)
                .code(docNoGenerator.next("SO"))
                .orderType(request.getOrderType() == null ? "PURCHASE" : request.getOrderType())
                .dealerId(request.getDealerId())
                .isRed(Boolean.TRUE.equals(request.getIsRed()))
                .refOrderId(request.getRefOrderId())
                .shipAddressId(request.getShipAddressId())
                .shipSnapshot(request.getShipSnapshot() == null ? new HashMap<>() : request.getShipSnapshot())
                .status("DRAFT")
                .amountInclTax(amountInclTax)
                .discountAmount(discount)
                .finalAmount(finalAmount)
                .remark(request.getRemark())
                .expectedDate(request.getExpectedDate())
                .createdBy(TenantContext.getUserId())
                .updatedAt(OffsetDateTime.now())
                .build();
        order.ensureSnapshot();
        Order saved = orderRepository.save(order);

        for (OrderLine le : lineEntities) {
            le.setOrderId(saved.getId());
        }
        lineRepository.saveAll(lineEntities);

        // 5. 促销命中记录
        List<OrderPromotionHit> hits = new ArrayList<>();
        for (PromotionHit ph : evalResult.getHits()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("desc", ph.getDetail());
            OrderPromotionHit hit = OrderPromotionHit.builder()
                    .orderId(saved.getId())
                    .promotionId(ph.getPromotionId())
                    .ruleType(ph.getPromoType())
                    .discount(ph.getDiscount())
                    .detail(detail)
                    .giftLines(new HashMap<>())
                    .build();
            hits.add(hit);
        }
        if (!hits.isEmpty()) hitRepository.saveAll(hits);

        // 6. 状态历史
        historyRepository.save(OrderStatusHistory.builder()
                .orderId(saved.getId())
                .fromStatus(null)
                .toStatus("DRAFT")
                .action("CREATE")
                .operatorId(TenantContext.getUserId())
                .comment("订单创建")
                .build());

        return OrderDTO.builder()
                .order(saved)
                .lines(lineEntities)
                .promotionHits(hits)
                .warnings(evalResult.getWarnings())
                .build();
    }

    @Transactional
    public Order submit(Long id) {
        Order order = loadOrder(id);
        return transition(order, "DRAFT", "SUBMITTED", "SUBMIT", "提交订单", o -> o.setSubmittedAt(OffsetDateTime.now()));
    }

    @Transactional
    public Order approve(Long id) {
        Order order = loadOrder(id);
        return transition(order, "SUBMITTED", "APPROVED", "APPROVE", "审批通过", o -> o.setApprovedAt(OffsetDateTime.now()));
    }

    @Transactional
    public Order reject(Long id, String reason) {
        Order order = loadOrder(id);
        return transition(order, "SUBMITTED", "REJECTED", "REJECT", reason == null ? "审批拒绝" : reason, null);
    }

    @Transactional
    public Order cancel(Long id) {
        Order order = loadOrder(id);
        if ("COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "订单已完成，不可取消");
        }
        String from = order.getStatus();
        order.setStatus("CANCELLED");
        order.setUpdatedAt(OffsetDateTime.now());
        Order saved = orderRepository.save(order);
        recordHistory(saved.getId(), from, "CANCELLED", "CANCEL", "订单取消");
        return saved;
    }

    /**
     * 简化 split：新建一个 parent_order_id 指向原订单的空壳订单，行拆分逻辑省略。
     */
    @Transactional
    public Order split(Long id) {
        Order origin = loadOrder(id);
        Order child = Order.builder()
                .tenantId(origin.getTenantId())
                .code(docNoGenerator.next("SO"))
                .orderType(origin.getOrderType())
                .dealerId(origin.getDealerId())
                .shipAddressId(origin.getShipAddressId())
                .shipSnapshot(new HashMap<>())
                .status("DRAFT")
                .parentOrderId(origin.getId())
                .amountInclTax(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .createdBy(TenantContext.getUserId())
                .updatedAt(OffsetDateTime.now())
                .build();
        child.ensureSnapshot();
        Order saved = orderRepository.save(child);
        recordHistory(saved.getId(), null, "DRAFT", "SPLIT", "拆分自订单 " + origin.getCode());
        log.info("订单 {} 简化拆分，生成子单 {}", origin.getCode(), saved.getCode());
        return saved;
    }

    // ------- 内部辅助 -------

    private Order transition(Order order, String expectFrom, String toStatus,
                              String action, String comment,
                              java.util.function.Consumer<Order> extra) {
        if (!expectFrom.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "订单状态 " + order.getStatus() + " 不支持动作 " + action);
        }
        order.setStatus(toStatus);
        if (extra != null) extra.accept(order);
        order.setUpdatedAt(OffsetDateTime.now());
        Order saved = orderRepository.save(order);
        recordHistory(saved.getId(), expectFrom, toStatus, action, comment);
        return saved;
    }

    private void recordHistory(Long orderId, String from, String to, String action, String comment) {
        historyRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(from)
                .toStatus(to)
                .action(action)
                .operatorId(TenantContext.getUserId())
                .comment(comment)
                .build());
    }

    private Order loadOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单不存在"));
    }
}
