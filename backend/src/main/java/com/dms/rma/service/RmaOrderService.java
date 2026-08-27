/*
 * RMA 订单服务：v4.3.0 起一张销退单可关联同一经销商的多张销售出库单。
 */
package com.dms.rma.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.inventory.service.InventoryService;
import com.dms.rma.entity.RmaAuthorization;
import com.dms.rma.entity.RmaOrder;
import com.dms.rma.entity.RmaOrderLine;
import com.dms.rma.entity.RmaOrderRef;
import com.dms.rma.repository.RmaAuthorizationRepository;
import com.dms.rma.repository.RmaOrderLineRepository;
import com.dms.rma.repository.RmaOrderRefRepository;
import com.dms.rma.repository.RmaOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RmaOrderService {

    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.13");

    private final RmaOrderRepository rmaOrderRepository;
    private final RmaOrderRefRepository rmaOrderRefRepository;
    private final RmaOrderLineRepository rmaOrderLineRepository;
    private final RmaAuthorizationRepository authRepository;
    private final InventoryService inventoryService;
    private final DocNoGenerator docNoGenerator;
    private final ApprovalService approvalService;
    private final EntityManager em;

    @Transactional(readOnly = true)
    public PageResult<RmaOrder> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<RmaOrder> page = tenantId == null
                ? rmaOrderRepository.findAll(pageQuery.toPageable())
                : rmaOrderRepository.findByTenantId(tenantId, pageQuery.toPageable());
        enrichOrders(page.getContent(), false);
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public RmaOrder get(Long id) {
        RmaOrder order = findOrder(id);
        enrichOrder(order, true);
        return order;
    }

    /**
     * v4.3.0 统一销退单列表：合并新表 rma_orders（source=RMA，可关联多出库单）
     * 与历史 orders 红字销退单（source=LEGACY）。id 以 "r"/"l" 前缀区分两表主键，
     * 前端详情据此路由到 /api/rma/orders 或 /api/sales-returns。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> unifiedList(int page, int size, String status, Long dealerId, String keyword) {
        UUID tid = TenantContext.getTenantId();
        int safePage = Math.max(1, page);
        int safeSize = size <= 0 || size > 200 ? 30 : size;
        int offset = (safePage - 1) * safeSize;

        boolean hasStatus = status != null && !status.isBlank();
        boolean hasDealer = dealerId != null;
        boolean hasKw = keyword != null && !keyword.isBlank();

        // 过滤条件直接拼在带别名的子查询 WHERE 上，避免外层 tenant_id 列引用歧义。
        List<Object> p1 = new ArrayList<>();
        String f1 = buildReturnFilter("ro", "d", 1, p1, tid, hasStatus ? status : null, hasDealer ? dealerId : null, hasKw ? keyword : null);
        int n = p1.size();
        List<Object> p2 = new ArrayList<>();
        String f2 = buildReturnFilter("o", "d", 1 + n, p2, tid, hasStatus ? status : null, hasDealer ? dealerId : null, hasKw ? keyword : null);

        String rma = "SELECT 'r' || id AS uid, id, code, status, dealer_id, dealer_name, final_amount, " +
                "sales_out_count, total_qty, created_at, 'RMA' AS source FROM ( " +
                "SELECT ro.id, ro.code, ro.status, ro.dealer_id, COALESCE(d.name,'') AS dealer_name, " +
                "ro.amount AS final_amount, ro.sales_out_count, ro.total_qty, ro.created_at, ro.tenant_id " +
                "FROM rma_orders ro LEFT JOIN dealers d ON d.id = ro.dealer_id WHERE ro.deleted_at IS NULL " + f1 + " ) r";
        String leg = "SELECT 'l' || id AS uid, id, code, status, dealer_id, dealer_name, final_amount, " +
                "0 AS sales_out_count, 0 AS total_qty, created_at, 'LEGACY' AS source FROM ( " +
                "SELECT o.id, o.code, o.status, o.dealer_id, COALESCE(d.name,'') AS dealer_name, " +
                "o.final_amount, o.created_at, o.tenant_id " +
                "FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id " +
                "WHERE o.deleted_at IS NULL AND COALESCE(o.is_red,false)=true " + f2 + " ) l";

        List<Object> allParams = new ArrayList<>();
        allParams.addAll(p1);
        allParams.addAll(p2);

        jakarta.persistence.Query cntQ = em.createNativeQuery(
                "SELECT COUNT(*) FROM ( " + rma + " UNION ALL " + leg + " ) c");
        for (int i = 0; i < allParams.size(); i++) cntQ.setParameter(i + 1, allParams.get(i));
        long total = ((Number) cntQ.getSingleResult()).longValue();

        jakarta.persistence.Query q = em.createNativeQuery(
                "SELECT * FROM ( " + rma + " UNION ALL " + leg + " ) u " +
                "ORDER BY created_at DESC, uid DESC LIMIT " + safeSize + " OFFSET " + offset, Tuple.class);
        for (int i = 0; i < allParams.size(); i++) q.setParameter(i + 1, allParams.get(i));
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            long numericId = ((Number) t.get("id")).longValue();
            String source = String.valueOf(t.get("source"));
            String uid = ("RMA".equals(source) ? "r" : "l") + numericId;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", uid);
            m.put("id", uid);
            m.put("refId", numericId);
            m.put("source", source);
            m.put("code", t.get("code"));
            m.put("status", t.get("status"));
            m.put("dealerId", t.get("dealer_id"));
            m.put("dealerName", t.get("dealer_name"));
            m.put("finalAmount", t.get("final_amount"));
            m.put("salesOutCount", t.get("sales_out_count"));
            m.put("totalQty", t.get("total_qty"));
            Object ca = t.get("created_at");
            m.put("createdAt", ca == null ? null : ca.toString());
            list.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("list", list);
        return data;
    }

    /** 构造销退过滤条件（带表别名），占位符从 startIdx 开始；参数按顺序追加到 params。 */
    private String buildReturnFilter(String mainAlias, String dealerAlias, int startIdx, List<Object> params,
                                     UUID tid, String status, Long dealerId, String keyword) {
        StringBuilder sb = new StringBuilder();
        int i = startIdx;
        sb.append(" AND ").append(mainAlias).append(".tenant_id = ?").append(i++); params.add(tid);
        if (status != null) { sb.append(" AND ").append(mainAlias).append(".status = ?").append(i++); params.add(status); }
        if (dealerId != null) { sb.append(" AND ").append(mainAlias).append(".dealer_id = ?").append(i++); params.add(dealerId); }
        if (keyword != null) {
            String kw = "%" + keyword.trim() + "%";
            sb.append(" AND (").append(mainAlias).append(".code ILIKE ?").append(i++);
            sb.append(" OR ").append(dealerAlias).append(".name ILIKE ?").append(i++).append(") ");
            params.add(kw); params.add(kw);
        }
        return sb.toString();
    }

    @Transactional
    public RmaOrder create(RmaOrder req) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        req.setId(null);
        req.setTenantId(tenantId);
        req.setCode(docNoGenerator.next("RMA"));
        req.setStatus("DRAFT");
        req.setCreatedBy(TenantContext.getUserId());
        req.setUpdatedAt(OffsetDateTime.now());
        req.ensureJson();

        List<RequestedLine> requestedLines = requestedLines(req);
        boolean multiOutboundRequest = (req.getSalesOutIds() != null && !req.getSalesOutIds().isEmpty())
                || (req.getOutboundLines() != null && !req.getOutboundLines().isEmpty())
                || (req.getReturnLines() != null && !req.getReturnLines().isEmpty())
                || (req.getLines() != null && req.getLines().get("outboundLines") instanceof List<?> list
                    && !list.isEmpty());
        if (multiOutboundRequest && requestedLines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "多出库单销退必须提供有效明细：每行需包含 salesOutId、salesOutLineId 和正整数 qty");
        }
        RmaOrder saved;
        if (requestedLines.isEmpty()) {
            saved = createLegacy(req, tenantId);
        } else {
            saved = createMultiOutbound(req, tenantId, requestedLines);
        }
        return startApproval(saved);
    }

    /**
     * v4.3.2: 销退单创建后正式提交进入审批流（业务类型 RMA_ORDER）。
     * 有审批模板 -> RUNNING / 状态 PENDING_APPROVAL（审批中心可见待办）；
     * 无模板自动通过 -> AUTO_APPROVED / 状态 COMPLETED（回调 RmaOrderApprovalCallback 负责回写库存）。
     */
    private RmaOrder startApproval(RmaOrder order) {
        StartApprovalRequest request = new StartApprovalRequest();
        request.setBusinessType(RmaOrderApprovalCallback.BUSINESS_TYPE);
        request.setBusinessId(order.getId());
        request.setBusinessCode(order.getCode());
        request.setTitle("销退单审批: " + order.getCode());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", order.getCode());
        snapshot.put("dealerId", order.getDealerId());
        snapshot.put("amount", order.getAmount());
        snapshot.put("salesOutCount", order.getSalesOutCount());
        snapshot.put("totalQty", order.getTotalQty());
        snapshot.put("reason", order.getReason());
        request.setBusinessSnapshot(snapshot);
        ApprovalInstance instance = approvalService.start(request);
        String st = instance.getStatus() == null ? "" : instance.getStatus().name();
        boolean autoApproved = "APPROVED".equals(st) || "AUTO_APPROVED".equals(st);
        order.setStatus(autoApproved ? "COMPLETED" : "PENDING_APPROVAL");
        order.setSubmittedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        if (autoApproved) {
            order.setCompletedAt(OffsetDateTime.now());
        }
        return rmaOrderRepository.save(order);
    }

    @Transactional
    public RmaOrder submit(Long id) {
        RmaOrder order = findOrder(id);
        if (!"DRAFT".equals(order.getStatus()) && !"REJECTED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有草稿/已驳回状态的销退单可以提交");
        }
        order.setStatus("DRAFT");
        order.setUpdatedAt(OffsetDateTime.now());
        rmaOrderRepository.saveAndFlush(order);
        return startApproval(order);
    }

    @Transactional
    public RmaOrder complete(Long id) {
        RmaOrder order = findOrder(id);
        if ("COMPLETED".equals(order.getStatus())) {
            return order;
        }
        if (!"SUBMITTED".equals(order.getStatus()) && !"PENDING_APPROVAL".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有待审批/已提交的销退单可以审批通过");
        }

        List<RmaOrderLine> lines = rmaOrderLineRepository.findByRmaIdOrderBySeqAscIdAsc(id);
        Map<Long, Integer> qtyBySourceLine = lines.stream()
                .filter(line -> line.getSalesOutLineId() != null)
                .collect(Collectors.groupingBy(RmaOrderLine::getSalesOutLineId,
                        LinkedHashMap::new, Collectors.summingInt(RmaOrderLine::getQty)));

        for (Map.Entry<Long, Integer> entry : qtyBySourceLine.entrySet()) {
            Long sourceLineId = entry.getKey();
            BigDecimal qty = BigDecimal.valueOf(entry.getValue());
            int updated = em.createNativeQuery(
                    "UPDATE sales_out_lines " +
                    "SET returned_qty = LEAST(COALESCE(shipped_qty, qty, 0), COALESCE(returned_qty, 0) + ?1), " +
                    "    return_locked_qty = GREATEST(COALESCE(return_locked_qty, 0) - ?1, 0) " +
                    "WHERE id = ?2 AND sales_out_id IN (SELECT sales_out_id FROM rma_order_refs WHERE rma_id = ?3)")
                    .setParameter(1, qty)
                    .setParameter(2, sourceLineId)
                    .setParameter(3, id)
                    .executeUpdate();
            if (updated == 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "来源出库行 " + sourceLineId + " 不属于当前销退单，不能审批回写");
            }

            Tuple source = findSourceLineForStock(order.getTenantId(), sourceLineId);
            Long dealerId = order.getDealerId() != null ? order.getDealerId() : longValue(source.get("dealer_id"));
            inventoryService.applyTransaction(order.getTenantId(), dealerId,
                    longValue(source.get("warehouse_id")), longValue(source.get("product_id")),
                    blankToNull(stringValue(source.get("batch_no"))),
                    entry.getValue() == 1 ? blankToNull(stringValue(source.get("serial_no"))) : null,
                    qty, "RMA_IN", "RMA_ORDER", order.getId());
        }

        order.setStatus("COMPLETED");
        order.setCompletedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        log.info("RMA 销退单 {} 审批通过，来源出库单 {} 张，数量 {}，库存已回写",
                order.getCode(), order.getSalesOutCount(), order.getTotalQty());
        return rmaOrderRepository.save(order);
    }

    @Transactional
    public RmaOrder cancel(Long id) {
        RmaOrder order = findOrder(id);
        if ("COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已完成销退单不可取消");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return order;
        }

        List<RmaOrderLine> lines = rmaOrderLineRepository.findByRmaIdOrderBySeqAscIdAsc(id);
        Map<Long, Integer> qtyBySourceLine = lines.stream()
                .filter(line -> line.getSalesOutLineId() != null)
                .collect(Collectors.groupingBy(RmaOrderLine::getSalesOutLineId,
                        LinkedHashMap::new, Collectors.summingInt(RmaOrderLine::getQty)));
        for (Map.Entry<Long, Integer> entry : qtyBySourceLine.entrySet()) {
            em.createNativeQuery(
                    "UPDATE sales_out_lines " +
                    "SET return_locked_qty = GREATEST(COALESCE(return_locked_qty, 0) - ?1, 0) " +
                    "WHERE id = ?2")
                    .setParameter(1, BigDecimal.valueOf(entry.getValue()))
                    .setParameter(2, entry.getKey())
                    .executeUpdate();
        }
        releaseAuthorizationQuota(order);
        order.setStatus("CANCELLED");
        order.setUpdatedAt(OffsetDateTime.now());
        return rmaOrderRepository.save(order);
    }

    private RmaOrder createLegacy(RmaOrder req, UUID tenantId) {
        if (req.getRefRmaAuthId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING,
                    "缺少 refRmaAuthId，或请传入 salesOutIds/outboundLines 创建销退单");
        }
        if (req.getAmount() == null || req.getAmount().signum() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "amount 必须 > 0");
        }
        RmaAuthorization auth = lockAndOccupyAuthorization(
                req.getRefRmaAuthId(), tenantId, req.getAmount(), req.getDealerId());
        req.setDealerId(auth.getDealerId());
        req.setSalesOutCount(0);
        req.setTotalQty(0);
        RmaOrder saved = rmaOrderRepository.save(req);
        log.info("旧版 RMA 订单 {} 已提交，授权 {} 配额已占用", saved.getCode(), auth.getCode());
        return saved;
    }

    private RmaOrder createMultiOutbound(RmaOrder req, UUID tenantId, List<RequestedLine> requestedLines) {
        List<Long> salesOutIds = requestedLines.stream()
                .map(RequestedLine::salesOutId)
                .distinct()
                .toList();
        Map<Long, Tuple> headers = findSalesOutHeaders(tenantId, salesOutIds);
        validateSameDealer(salesOutIds, headers);
        validateSameWarehouse(salesOutIds, headers);

        Long dealerId = longValue(headers.get(salesOutIds.get(0)).get("dealer_id"));
        Map<Long, Tuple> sourceLines = findSourceLines(tenantId, salesOutIds);
        Map<Long, Integer> requestedQtyByLine = aggregateRequestedQty(requestedLines);
        List<PreparedLine> preparedLines = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalQty = 0;
        int seq = 1;

        for (RequestedLine requestedLine : requestedLines) {
            Tuple header = headers.get(requestedLine.salesOutId());
            Tuple source = sourceLines.get(requestedLine.salesOutLineId());
            if (source == null || !requestedLine.salesOutId().equals(longValue(source.get("sales_out_id")))) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "出库行 " + requestedLine.salesOutLineId() + " 不属于出库单 "
                                + stringValue(header.get("code")));
            }
            int requestedQty = requestedLine.qty();
            int availableQty = availableQty(source);
            if (requestedQty > availableQty) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "出库单 " + stringValue(header.get("code")) + " 中产品 "
                                + stringValue(source.get("product_code"))
                                + " 可退数量不足：申请 " + requestedQty + "，剩余可退 " + availableQty);
            }

            BigDecimal sourceTotal = refundableSourceTotal(source);
            BigDecimal claimedTotal = claimedRefundTotal(tenantId, requestedLine.salesOutLineId());
            BigDecimal remainingAmount = sourceTotal.subtract(claimedTotal).max(BigDecimal.ZERO);
            BigDecimal lineTotal = refundEaPrice(source)
                    .multiply(BigDecimal.valueOf(requestedQty))
                    .setScale(2, RoundingMode.HALF_UP);
            if (lineTotal.compareTo(remainingAmount) > 0) {
                lineTotal = remainingAmount.setScale(2, RoundingMode.HALF_UP);
            }
            if (lineTotal.signum() < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "出库行 " + requestedLine.salesOutLineId() + " 可退金额不足，不能出现负数退款");
            }
            BigDecimal unitPrice = requestedQty == 0 ? BigDecimal.ZERO
                    : lineTotal.divide(BigDecimal.valueOf(requestedQty), 4, RoundingMode.HALF_UP);
            BigDecimal taxRate = taxRate(source);
            preparedLines.add(new PreparedLine(requestedLine, source, requestedQty,
                    unitPrice, taxRate, lineTotal, seq++));
            totalAmount = totalAmount.add(lineTotal);
            totalQty += requestedQty;
        }

        if (req.getRefRmaAuthId() != null) {
            lockAndOccupyAuthorization(req.getRefRmaAuthId(), tenantId, totalAmount, dealerId);
        }

        req.setDealerId(dealerId);
        req.setRmaType(hasText(req.getRmaType()) ? req.getRmaType() : "RETURN");
        req.setAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        req.setSalesOutCount(salesOutIds.size());
        req.setTotalQty(totalQty);
        req.setPriceSnapshot(buildPriceSnapshot(preparedLines));
        RmaOrder saved = rmaOrderRepository.save(req);

        Map<Long, RmaOrderRef> refs = new LinkedHashMap<>();
        for (Long salesOutId : salesOutIds) {
            Tuple header = headers.get(salesOutId);
            RmaOrderRef ref = RmaOrderRef.builder()
                    .tenantId(tenantId)
                    .rmaId(saved.getId())
                    .salesOutId(salesOutId)
                    .salesOutCode(stringValue(header.get("code")))
                    .dealerId(dealerId)
                    .build();
            refs.put(salesOutId, rmaOrderRefRepository.save(ref));
        }

        for (Map.Entry<Long, Integer> entry : requestedQtyByLine.entrySet()) {
            int locked = em.createNativeQuery(
                    "UPDATE sales_out_lines sol " +
                    "SET return_locked_qty = COALESCE(sol.return_locked_qty, 0) + ?1 " +
                    "FROM sales_outs so " +
                    "WHERE sol.id = ?2 AND so.id = sol.sales_out_id AND so.tenant_id = ?3 " +
                    "AND COALESCE(sol.shipped_qty, sol.qty, 0) - COALESCE(sol.return_locked_qty, 0) " +
                    "    - COALESCE(sol.returned_qty, 0) >= ?1")
                    .setParameter(1, BigDecimal.valueOf(entry.getValue()))
                    .setParameter(2, entry.getKey())
                    .setParameter(3, tenantId)
                    .executeUpdate();
            if (locked == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                        "来源出库行 " + entry.getKey() + " 的可退数量刚被其他销退单锁定，请刷新后重试");
            }
        }

        List<Map<String, Object>> legacyLines = new ArrayList<>();
        for (PreparedLine prepared : preparedLines) {
            RmaOrderLine entity = RmaOrderLine.builder()
                    .tenantId(tenantId)
                    .rmaId(saved.getId())
                    .refId(refs.get(prepared.requestedLine().salesOutId()).getId())
                    .salesOutLineId(prepared.requestedLine().salesOutLineId())
                    .productId(longValue(prepared.source().get("product_id")))
                    .productCode(stringValue(prepared.source().get("product_code")))
                    .productName(stringValue(prepared.source().get("product_name")))
                    .productSpec(stringValue(prepared.source().get("product_spec")))
                    .qty(prepared.qty())
                    .unitPriceInclTax(prepared.unitPrice())
                    .taxRate(prepared.taxRate())
                    .subTotal(prepared.lineTotal())
                    .reason(prepared.requestedLine().reason())
                    .batchNo(stringValue(prepared.source().get("batch_no")))
                    .serialNo(prepared.qty() == 1 ? blankToNull(stringValue(prepared.source().get("serial_no"))) : null)
                    .seq(prepared.seq())
                    .build();
            rmaOrderLineRepository.save(entity);

            Map<String, Object> legacyLine = new LinkedHashMap<>();
            legacyLine.put("salesOutId", prepared.requestedLine().salesOutId());
            legacyLine.put("salesOutLineId", prepared.requestedLine().salesOutLineId());
            legacyLine.put("productId", entity.getProductId());
            legacyLine.put("productCode", entity.getProductCode());
            legacyLine.put("productName", entity.getProductName());
            legacyLine.put("qty", entity.getQty());
            legacyLine.put("unitPriceInclTax", entity.getUnitPriceInclTax());
            legacyLine.put("subTotal", entity.getSubTotal());
            legacyLine.put("reason", entity.getReason());
            legacyLine.put("batchNo", entity.getBatchNo());
            legacyLine.put("serialNo", entity.getSerialNo());
            legacyLines.add(legacyLine);
        }
        saved.getLines().put("outboundLines", legacyLines);
        saved.setUpdatedAt(OffsetDateTime.now());
        saved = rmaOrderRepository.save(saved);
        enrichOrder(saved, true);
        log.info("RMA 销退单 {} 创建成功，关联出库单 {} 张，明细 {} 行，总数量 {}",
                saved.getCode(), salesOutIds.size(), preparedLines.size(), totalQty);
        return saved;
    }

    private RmaOrder findOrder(Long id) {
        RmaOrder order = rmaOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "RMA 订单不存在"));
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.equals(order.getTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "RMA 订单不存在");
        }
        return order;
    }

    private void enrichOrders(List<RmaOrder> orders, boolean withLines) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> ids = orders.stream().map(RmaOrder::getId).toList();
        List<RmaOrderRef> refs = rmaOrderRefRepository.findByRmaIdInOrderByIdAsc(ids);
        List<RmaOrderLine> lines = withLines
                ? rmaOrderLineRepository.findByRmaIdInOrderByRmaIdAscSeqAscIdAsc(ids)
                : List.of();
        Map<Long, List<RmaOrderRef>> refsByRma = refs.stream()
                .collect(Collectors.groupingBy(RmaOrderRef::getRmaId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<RmaOrderLine>> linesByRma = lines.stream()
                .collect(Collectors.groupingBy(RmaOrderLine::getRmaId, LinkedHashMap::new, Collectors.toList()));
        Set<Long> salesOutIds = refs.stream().map(RmaOrderRef::getSalesOutId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Map<String, Object>> salesOutInfo = loadSalesOutInfo(salesOutIds);
        for (RmaOrder order : orders) {
            order.setRefs(refsByRma.getOrDefault(order.getId(), List.of()));
            order.setSalesOutIds(order.getRefs().stream().map(RmaOrderRef::getSalesOutId).toList());
            order.setReturnLines(withLines ? linesByRma.getOrDefault(order.getId(), List.of()) : null);
            order.setOutboundGroups(buildGroups(order, salesOutInfo));
            order.setOutboundLines(flatOutboundLines(order));
        }
    }

    private Map<Long, Map<String, Object>> loadSalesOutInfo(Set<Long> salesOutIds) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        if (salesOutIds == null || salesOutIds.isEmpty()) {
            return result;
        }
        var q = em.createNativeQuery(
                "SELECT so.id, so.code, so.dealer_id, so.warehouse_id, so.source_order_id, so.status, " +
                "COALESCE(so.sales_date, so.shipped_at, so.created_at) AS sales_date, " +
                "d.name AS dealer_name, w.name AS warehouse_name, o.code AS order_code " +
                "FROM sales_outs so LEFT JOIN dealers d ON d.id=so.dealer_id " +
                "LEFT JOIN warehouses w ON w.id=so.warehouse_id LEFT JOIN orders o ON o.id=so.source_order_id " +
                "WHERE so.id IN :ids", Tuple.class);
        q.setParameter("ids", salesOutIds);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", t.get("code"));
            m.put("dealerId", t.get("dealer_id"));
            m.put("dealerName", t.get("dealer_name"));
            m.put("warehouseId", t.get("warehouse_id"));
            m.put("warehouseName", t.get("warehouse_name"));
            m.put("orderId", t.get("source_order_id"));
            m.put("orderCode", t.get("order_code"));
            m.put("salesDate", com.dms.common.util.DateFmt.fmt(t.get("sales_date")));
            m.put("status", t.get("status"));
            Object id = t.get("id");
            if (id != null) result.put(((Number) id).longValue(), m);
        }
        return result;
    }

    private void enrichOrder(RmaOrder order, boolean withLines) {
        enrichOrders(List.of(order), withLines);
    }

    private List<Map<String, Object>> buildGroups(RmaOrder order, Map<Long, Map<String, Object>> salesOutInfo) {
        List<Map<String, Object>> groups = new ArrayList<>();
        if (order.getRefs() == null) {
            return groups;
        }
        for (RmaOrderRef ref : order.getRefs()) {
            List<RmaOrderLine> refLines = order.getReturnLines() == null ? List.of()
                    : order.getReturnLines().stream()
                    .filter(line -> ref.getId().equals(line.getRefId()))
                    .toList();
            Map<String, Object> info = salesOutInfo == null ? null : salesOutInfo.get(ref.getSalesOutId());
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("salesOutId", ref.getSalesOutId());
            group.put("salesOutCode", ref.getSalesOutCode() != null ? ref.getSalesOutCode()
                    : (info != null ? info.get("code") : null));
            group.put("dealerId", ref.getDealerId() != null ? ref.getDealerId()
                    : (info != null ? info.get("dealerId") : null));
            group.put("dealerName", info != null ? info.get("dealerName") : null);
            group.put("warehouseId", info != null ? info.get("warehouseId") : null);
            group.put("warehouseName", info != null ? info.get("warehouseName") : null);
            group.put("orderCode", info != null ? info.get("orderCode") : null);
            group.put("salesDate", info != null ? info.get("salesDate") : null);
            group.put("status", info != null ? info.get("status") : null);
            group.put("lines", refLines.stream().map(line -> {
                Map<String, Object> map = lineToMap(line);
                map.put("salesOutId", ref.getSalesOutId());
                map.put("salesOutCode", ref.getSalesOutCode());
                return map;
            }).toList());
            groups.add(group);
        }
        return groups;
    }

    private List<Map<String, Object>> flatOutboundLines(RmaOrder order) {
        if (order.getOutboundGroups() == null) {
            return List.of();
        }
        return order.getOutboundGroups().stream()
                .flatMap(group -> castLineMaps(group.get("lines")).stream())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castLineMaps(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private Map<String, Object> lineToMap(RmaOrderLine line) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", line.getId());
        map.put("refId", line.getRefId());
        map.put("salesOutLineId", line.getSalesOutLineId());
        map.put("productId", line.getProductId());
        map.put("productCode", line.getProductCode());
        map.put("productName", line.getProductName());
        map.put("productSpec", line.getProductSpec());
        map.put("qty", line.getQty());
        map.put("unitPriceInclTax", line.getUnitPriceInclTax());
        map.put("taxRate", line.getTaxRate());
        map.put("subTotal", line.getSubTotal());
        map.put("reason", line.getReason());
        map.put("batchNo", line.getBatchNo());
        map.put("serialNo", line.getSerialNo());
        map.put("seq", line.getSeq());
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<RequestedLine> requestedLines(RmaOrder req) {
        List<RequestedLine> result = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        Long singleSalesOutId = firstLong(req.getSalesOutIds(), req.getLines() == null ? null : req.getLines().get("salesOutId"));
        List<Map<String, Object>> rawLines = new ArrayList<>();
        if (req.getOutboundLines() != null) {
            rawLines.addAll(req.getOutboundLines());
        }
        if (req.getReturnLines() != null) {
            for (RmaOrderLine line : req.getReturnLines()) {
                Map<String, Object> map = lineToMap(line);
                if (map.get("salesOutId") == null) {
                    map.put("salesOutId", singleSalesOutId);
                }
                rawLines.add(map);
            }
        }
        if (req.getLines() != null && req.getLines().get("outboundLines") instanceof List<?> outboundLines) {
            for (Object item : outboundLines) {
                if (item instanceof Map<?, ?> map) {
                    rawLines.add((Map<String, Object>) map);
                }
            }
        }
        for (Map<String, Object> raw : rawLines) {
            Long salesOutId = longValue(raw.get("salesOutId") != null ? raw.get("salesOutId") : raw.get("sourceSalesOutId"));
            if (salesOutId == null) {
                salesOutId = singleSalesOutId;
            }
            Long salesOutLineId = longValue(raw.get("salesOutLineId") != null
                    ? raw.get("salesOutLineId") : raw.get("sourceOutLineId"));
            Integer qty = intValue(raw.get("qty"));
            if (salesOutId == null || salesOutLineId == null || qty == null) {
                continue;
            }
            if (qty <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "出库行 " + salesOutLineId + " 的退货数量必须为正整数");
            }
            String key = salesOutId + ":" + salesOutLineId;
            if (!unique.add(key)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "同一出库行 " + salesOutLineId + " 请合并为一行后再提交");
            }
            result.add(new RequestedLine(salesOutId, salesOutLineId, qty,
                    stringValue(raw.get("reason"))));
        }
        return result;
    }

    private Map<Long, Tuple> findSalesOutHeaders(UUID tenantId, List<Long> salesOutIds) {
        if (salesOutIds == null || salesOutIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "请选择至少一张销售出库单");
        }
        List<Long> distinctIds = salesOutIds.stream().distinct().toList();
        List<Tuple> rows = em.createNativeQuery(
                "SELECT id, code, dealer_id, warehouse_id, status, is_red FROM sales_outs " +
                "WHERE tenant_id = ?1 AND deleted_at IS NULL AND id IN (:ids)", Tuple.class)
                .setParameter(1, tenantId)
                .setParameter("ids", distinctIds)
                .getResultList();
        Map<Long, Tuple> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            result.put(longValue(row.get("id")), row);
        }
        List<String> missing = distinctIds.stream()
                .filter(id -> !result.containsKey(id))
                .map(String::valueOf)
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "销售出库单不存在或无权访问：" + String.join("、", missing));
        }
        List<String> invalid = result.values().stream()
                .filter(row -> Boolean.TRUE.equals(row.get("is_red"))
                        || !isReturnableStatus(stringValue(row.get("status"))))
                .map(row -> stringValue(row.get("code")) + "(" + row.get("status") + ")")
                .toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "以下出库单不是可退货状态：" + String.join("、", invalid));
        }
        return result;
    }

    private void validateSameDealer(List<Long> salesOutIds, Map<Long, Tuple> headers) {
        Map<Long, List<String>> byDealer = new LinkedHashMap<>();
        for (Long salesOutId : salesOutIds) {
            Tuple header = headers.get(salesOutId);
            Long dealerId = longValue(header.get("dealer_id"));
            byDealer.computeIfAbsent(dealerId, ignored -> new ArrayList<>())
                    .add(stringValue(header.get("code")));
        }
        if (byDealer.size() > 1) {
            String detail = byDealer.entrySet().stream()
                    .map(entry -> "客户ID " + entry.getKey() + "：" + String.join("、", entry.getValue()))
                    .collect(Collectors.joining("；"));
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "一张销退单只能关联同一经销商的出库单；" + detail);
        }
    }

    private void validateSameWarehouse(List<Long> salesOutIds, Map<Long, Tuple> headers) {
        Map<Long, List<String>> byWarehouse = new LinkedHashMap<>();
        for (Long salesOutId : salesOutIds) {
            Tuple header = headers.get(salesOutId);
            Long warehouseId = longValue(header.get("warehouse_id"));
            byWarehouse.computeIfAbsent(warehouseId, ignored -> new ArrayList<>())
                    .add(stringValue(header.get("code")));
        }
        if (byWarehouse.size() > 1) {
            String detail = byWarehouse.entrySet().stream()
                    .map(entry -> "仓库ID " + entry.getKey() + "：" + String.join("、", entry.getValue()))
                    .collect(Collectors.joining("；"));
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "一张销退单只能关联同一发货仓库的出库单；" + detail);
        }
    }

    private Map<Long, Tuple> findSourceLines(UUID tenantId, List<Long> salesOutIds) {
        List<Tuple> rows = em.createNativeQuery(
                "SELECT sol.id, sol.sales_out_id, sol.warehouse_id, so.dealer_id, sol.product_id, " +
                "       p.code AS product_code, p.name_cn AS product_name, p.spec AS product_spec, " +
                "       sol.batch_no, sol.serial_no, sol.seq, " +
                "       COALESCE(sol.shipped_qty, sol.qty, 0) AS shipped_qty, " +
                "       COALESCE(sol.return_locked_qty, 0) AS return_locked_qty, " +
                "       COALESCE(sol.returned_qty, 0) AS returned_qty, " +
                "       sol.unit_price, sol.tax_rate, sol.subtotal, sol.final_amount, " +
                "       ol.unit_price_incl_tax, ol.base_price_incl_tax, COALESCE(ol.is_gift, false) AS is_gift, " +
                "       o.pricing_mode, o.voucher_id " +
                "FROM sales_out_lines sol " +
                "JOIN sales_outs so ON so.id = sol.sales_out_id " +
                "LEFT JOIN products p ON p.id = sol.product_id " +
                "LEFT JOIN order_lines ol ON ol.id = sol.source_order_line_id " +
                "LEFT JOIN orders o ON o.id = ol.order_id " +
                "WHERE sol.sales_out_id IN (:ids) AND so.tenant_id = ?1 AND so.deleted_at IS NULL", Tuple.class)
                .setParameter(1, tenantId)
                .setParameter("ids", salesOutIds)
                .getResultList();
        Map<Long, Tuple> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            result.put(longValue(row.get("id")), row);
        }
        return result;
    }

    private Tuple findSourceLineForStock(UUID tenantId, Long sourceLineId) {
        List<Tuple> rows = em.createNativeQuery(
                "SELECT sol.id, sol.warehouse_id, so.dealer_id, sol.product_id, sol.batch_no, sol.serial_no " +
                "FROM sales_out_lines sol JOIN sales_outs so ON so.id = sol.sales_out_id " +
                "WHERE sol.id = ?1 AND so.tenant_id = ?2 AND so.deleted_at IS NULL", Tuple.class)
                .setParameter(1, sourceLineId)
                .setParameter(2, tenantId)
                .getResultList();
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "来源出库行不存在：" + sourceLineId);
        }
        return rows.get(0);
    }

    private Map<Long, Integer> aggregateRequestedQty(List<RequestedLine> lines) {
        return lines.stream().collect(Collectors.groupingBy(RequestedLine::salesOutLineId,
                LinkedHashMap::new, Collectors.summingInt(RequestedLine::qty)));
    }

    private int availableQty(Tuple source) {
        int shipped = intValue(source.get("shipped_qty")) == null ? 0 : intValue(source.get("shipped_qty"));
        int locked = intValue(source.get("return_locked_qty")) == null ? 0 : intValue(source.get("return_locked_qty"));
        int returned = intValue(source.get("returned_qty")) == null ? 0 : intValue(source.get("returned_qty"));
        return Math.max(0, shipped - locked - returned);
    }

    private boolean isReturnableStatus(String status) {
        return Set.of("COMPLETED", "PARTIAL_OUTBOUND", "PARTIAL_SHIPPED", "SHIPPED")
                .contains(status == null ? "" : status.toUpperCase(java.util.Locale.ROOT));
    }

    private BigDecimal taxRate(Tuple source) {
        BigDecimal taxRate = bigDecimal(source.get("tax_rate"));
        if (taxRate == null || taxRate.signum() < 0) {
            return DEFAULT_TAX_RATE;
        }
        return taxRate;
    }

    private BigDecimal refundEaPrice(Tuple source) {
        if (Boolean.TRUE.equals(source.get("is_gift"))) {
            return BigDecimal.ZERO;
        }
        BigDecimal price = null;
        if ("VOUCHER".equalsIgnoreCase(stringValue(source.get("pricing_mode")))
                || source.get("voucher_id") != null) {
            price = positiveOrNull(bigDecimal(source.get("base_price_incl_tax")));
        }
        if (price == null) {
            price = positiveOrNull(bigDecimal(source.get("unit_price_incl_tax")));
        }
        if (price == null) {
            price = positiveOrNull(bigDecimal(source.get("unit_price")));
        }
        if (price == null) {
            int shipped = availableShippedQty(source);
            BigDecimal total = positiveOrNull(bigDecimal(source.get("final_amount")));
            if (total == null) {
                total = positiveOrNull(bigDecimal(source.get("subtotal")));
            }
            if (total != null && shipped > 0) {
                price = total.divide(BigDecimal.valueOf(shipped), 4, RoundingMode.HALF_UP);
            }
        }
        return price == null ? BigDecimal.ZERO : price.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal refundableSourceTotal(Tuple source) {
        if (Boolean.TRUE.equals(source.get("is_gift"))) {
            return BigDecimal.ZERO;
        }
        int shipped = availableShippedQty(source);
        if ("VOUCHER".equalsIgnoreCase(stringValue(source.get("pricing_mode")))
                || source.get("voucher_id") != null) {
            BigDecimal base = positiveOrNull(bigDecimal(source.get("base_price_incl_tax")));
            if (base != null) {
                return base.multiply(BigDecimal.valueOf(shipped)).setScale(2, RoundingMode.HALF_UP);
            }
        }
        BigDecimal total = positiveOrNull(bigDecimal(source.get("final_amount")));
        if (total == null) {
            total = positiveOrNull(bigDecimal(source.get("subtotal")));
        }
        if (total == null) {
            total = refundEaPrice(source).multiply(BigDecimal.valueOf(shipped))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return total;
    }

    private int availableShippedQty(Tuple source) {
        int shipped = intValue(source.get("shipped_qty")) == null ? 0 : intValue(source.get("shipped_qty"));
        return Math.max(0, shipped);
    }

    private BigDecimal claimedRefundTotal(UUID tenantId, Long sourceLineId) {
        List<Tuple> rows = em.createNativeQuery(
                "SELECT COALESCE(SUM(rol.sub_total), 0) AS amount " +
                "FROM rma_order_lines rol " +
                "JOIN rma_orders ro ON ro.id = rol.rma_id " +
                "WHERE rol.tenant_id = ?1 AND rol.sales_out_line_id = ?2 " +
                "AND ro.deleted_at IS NULL AND COALESCE(ro.status, '') = 'COMPLETED'", Tuple.class)
                .setParameter(1, tenantId)
                .setParameter(2, sourceLineId)
                .getResultList();
        return rows.isEmpty() ? BigDecimal.ZERO : bigDecimal(rows.get(0).get("amount"));
    }

    private Map<String, Object> buildPriceSnapshot(List<PreparedLine> preparedLines) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("version", "v4.3.0");
        snapshot.put("taxBasis", "INCL_TAX");
        snapshot.put("lines", preparedLines.stream().map(line -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("salesOutId", line.requestedLine().salesOutId());
            item.put("salesOutLineId", line.requestedLine().salesOutLineId());
            item.put("productId", longValue(line.source().get("product_id")));
            item.put("productCode", stringValue(line.source().get("product_code")));
            item.put("qty", line.qty());
            item.put("unitPriceInclTax", line.unitPrice());
            item.put("taxRate", line.taxRate());
            item.put("subTotal", line.lineTotal());
            item.put("pricingMode", stringValue(line.source().get("pricing_mode")));
            item.put("voucherId", line.source().get("voucher_id"));
            item.put("gift", Boolean.TRUE.equals(line.source().get("is_gift")));
            return item;
        }).toList());
        return snapshot;
    }

    private RmaAuthorization lockAndOccupyAuthorization(Long authId, UUID tenantId,
                                                        BigDecimal amount, Long requestedDealerId) {
        RmaAuthorization auth = authRepository.lockById(authId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "RMA 授权不存在"));
        if (!tenantId.equals(auth.getTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "RMA 授权不存在");
        }
        if (!"active".equals(auth.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "RMA 授权未生效");
        }
        if (auth.getDealerId() != null && requestedDealerId != null
                && !auth.getDealerId().equals(requestedDealerId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "RMA 授权客户与出库单客户不一致");
        }
        BigDecimal used = auth.getQuotaUsed() == null ? BigDecimal.ZERO : auth.getQuotaUsed();
        BigDecimal quota = auth.getQuotaAmount() == null ? BigDecimal.ZERO : auth.getQuotaAmount();
        BigDecimal nextUsed = used.add(amount == null ? BigDecimal.ZERO : amount);
        if (nextUsed.compareTo(quota) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "RMA 授权配额不足: 已用=" + used + " 申请=" + amount + " 总额=" + quota);
        }
        auth.setQuotaUsed(nextUsed);
        auth.setUpdatedAt(OffsetDateTime.now());
        return authRepository.save(auth);
    }

    private void releaseAuthorizationQuota(RmaOrder order) {
        if (order.getRefRmaAuthId() == null || order.getAmount() == null) {
            return;
        }
        RmaAuthorization auth = authRepository.lockById(order.getRefRmaAuthId()).orElse(null);
        if (auth == null) {
            return;
        }
        BigDecimal used = auth.getQuotaUsed() == null ? BigDecimal.ZERO : auth.getQuotaUsed();
        auth.setQuotaUsed(used.subtract(order.getAmount()).max(BigDecimal.ZERO));
        auth.setUpdatedAt(OffsetDateTime.now());
        authRepository.save(auth);
    }

    private Long firstLong(List<Long> values, Object fallback) {
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return longValue(fallback);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return new BigDecimal(text).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        Long longValue = longValue(value);
        return longValue == null ? null : longValue.intValue();
    }

    private BigDecimal bigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal positiveOrNull(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RequestedLine(Long salesOutId, Long salesOutLineId, Integer qty, String reason) {
    }

    private record PreparedLine(RequestedLine requestedLine, Tuple source, Integer qty,
                                BigDecimal unitPrice, BigDecimal taxRate,
                                BigDecimal lineTotal, Integer seq) {
    }
}
