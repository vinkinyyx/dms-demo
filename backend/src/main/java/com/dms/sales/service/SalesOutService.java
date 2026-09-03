/*
 * 销售出库服务：
 *   create → 授权校验(SALES_TO_HOSPITAL) → 扣库存 → 写 sales_out_facts。
 *   redCancel → 生成红字冲销单 + 库存反向变动。
 *
 * v3.7.3 新增：
 *   - partialShip(soId, lines)：部分出库，batchNo/serialNo 从在库选
 *   - cancelPartial(soId, lines, reason)：按明细行部分取消，恢复库存
 *   - cancelFull(soId, reason)：整单作废，已出库恢复库存
 */
package com.dms.sales.service;

import com.dms.authz.dto.AuthorizationCheckRequest;
import com.dms.authz.dto.AuthorizationCheckResult;
import com.dms.authz.service.AuthorizationService;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.inventory.entity.StockSerial;
import com.dms.inventory.repository.StockSerialRepository;
import com.dms.inventory.service.InventoryService;
import com.dms.consignment.service.ConsignmentService;
import org.springframework.context.annotation.Lazy;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.repository.DealerRepository;
import com.dms.sales.dto.SalesOutCancelLineRequest;
import com.dms.sales.dto.SalesOutPartialShipRequest;
import com.dms.sales.entity.SalesOut;
import com.dms.sales.entity.SalesOutFact;
import com.dms.sales.entity.SalesOutLine;
import com.dms.sales.repository.SalesOutFactRepository;
import com.dms.sales.repository.SalesOutLineRepository;
import com.dms.sales.repository.SalesOutRepository;
import com.dms.tenant.entity.Tenant;
import com.dms.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOutService {

    private final SalesOutRepository salesOutRepository;
    private final SalesOutLineRepository lineRepository;
    private final SalesOutFactRepository factRepository;
    private final AuthorizationService authorizationService;
    private final InventoryService inventoryService;
    private final DocNoGenerator docNoGenerator;
    private final TenantRepository tenantRepository;
    private final DealerRepository dealerRepository;
    private final StockSerialRepository stockSerialRepository;
    private final EntityManager em;
    @Lazy
    private final ConsignmentService consignmentService;
    @Lazy
    private final com.dms.collab.CrossTenantCollabService crossTenantCollab;
    @Lazy
    private final com.dms.openapi.service.ExternalCollabWebhookService externalCollabWebhook;

    @Transactional(readOnly = true)
    public PageResult<SalesOut> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<SalesOut> page = tenantId == null
                ? salesOutRepository.findAll(pageQuery.toPageable())
                : salesOutRepository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    /**
     * 创建销售出库：授权 → 扣库存 → 写 fact。
     */
    @Transactional
    public SalesOut create(SalesOut salesOut, List<SalesOutLine> lines) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "销售出库明细为空");
        }

        // 医疗器械行业强制附件/序列号（简化：仅日志提示）
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant != null && "medical".equals(tenant.getIndustry())) {
            for (SalesOutLine l : lines) {
                if (l.getSerialNo() == null || l.getSerialNo().isBlank()) {
                    log.warn("[医疗器械] 销售明细缺少 serial_no，product={} qty={}", l.getProductId(), l.getQty());
                }
            }
        }

        // 授权校验 SALES_TO_HOSPITAL
        AuthorizationCheckRequest authReq = new AuthorizationCheckRequest();
        authReq.setDealerId(salesOut.getDealerId());
        authReq.setAuthType("SALES_TO_HOSPITAL");
        authReq.setAtTime(salesOut.getSalesDate() == null ? LocalDate.now() : salesOut.getSalesDate());
        List<AuthorizationCheckRequest.Line> authLines = new ArrayList<>();
        for (SalesOutLine l : lines) {
            AuthorizationCheckRequest.Line al = new AuthorizationCheckRequest.Line();
            al.setProductId(l.getProductId());
            al.setTerminalId(salesOut.getTerminalId());
            authLines.add(al);
        }
        authReq.setLines(authLines);
        List<AuthorizationCheckResult> checks = authorizationService.check(authReq);
        List<String> unauth = checks.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getAuthorized()))
                .map(r -> "商品 [" + productLabel(r.getProductId()) + "] " + r.getReason())
                .toList();
        if (!unauth.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "销售出库授权校验失败: " + String.join("; ", unauth));
        }

        salesOut.setId(null);
        salesOut.setTenantId(tenantId);
        salesOut.setCode(docNoGenerator.next("GI"));
        salesOut.setStatus("COMPLETED");
        salesOut.setIsRed(false);
        salesOut.setCreatedBy(TenantContext.getUserId());
        salesOut.setUpdatedAt(OffsetDateTime.now());
        if (salesOut.getSalesDate() == null) salesOut.setSalesDate(LocalDate.now());
        salesOut.ensureJson();

        BigDecimal totalAmount = BigDecimal.ZERO;
        SalesOut saved = salesOutRepository.save(salesOut);

        Long regionId = null;
        if (salesOut.getDealerId() != null) {
            Dealer d = dealerRepository.findById(salesOut.getDealerId()).orElse(null);
            if (d != null) regionId = d.getRegionId();
        }

        for (SalesOutLine l : lines) {
            l.setId(null);
            l.setSalesOutId(saved.getId());
            lineRepository.save(l);

            // 扣库存
            inventoryService.applyTransaction(tenantId, salesOut.getDealerId(), l.getWarehouseId(),
                    l.getProductId(), l.getBatchNo(), l.getSerialNo(),
                    l.getQty().negate(), "SALES_OUT", "SALES_OUT", saved.getId());

            BigDecimal unitPrice = l.getUnitPrice() == null ? BigDecimal.ZERO : l.getUnitPrice();
            BigDecimal amount = l.getQty().multiply(unitPrice);
            totalAmount = totalAmount.add(amount);

            SalesOutFact fact = SalesOutFact.builder()
                    .tenantId(tenantId)
                    .dealerId(salesOut.getDealerId())
                    .productId(l.getProductId())
                    .terminalId(salesOut.getTerminalId())
                    .regionId(regionId)
                    .salesDate(salesOut.getSalesDate())
                    .qty(l.getQty())
                    .amount(amount)
                    .build();
            factRepository.save(fact);
        }

        saved.setAmountInclTax(totalAmount);
        return salesOutRepository.save(saved);
    }

    /**
     * 红字冲销：生成 is_red=true 的负数销售出库单，库存反向入库。
     */
    @Transactional
    public SalesOut redCancel(Long id, String reason) {
        SalesOut origin = salesOutRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "原销售单不存在"));
        if (Boolean.TRUE.equals(origin.getIsRed())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "红字单不可再冲销");
        }
        UUID tenantId = origin.getTenantId();
        List<SalesOutLine> lines = lineRepository.findBySalesOutId(id);

        SalesOut red = SalesOut.builder()
                .tenantId(tenantId)
                .code(docNoGenerator.next("GI"))
                .dealerId(origin.getDealerId())
                .terminalId(origin.getTerminalId())
                .businessType(origin.getBusinessType())
                .salesDate(LocalDate.now())
                .surgeryInfo(origin.getSurgeryInfo())
                .isRed(true)
                .refSalesOutId(origin.getId())
                .status("COMPLETED")
                .amountInclTax(origin.getAmountInclTax() == null
                        ? null : origin.getAmountInclTax().negate())
                .createdBy(TenantContext.getUserId())
                .updatedAt(OffsetDateTime.now())
                .build();
        red.ensureJson();
        red.getSurgeryInfo().put("redReason", reason);
        SalesOut savedRed = salesOutRepository.save(red);

        for (SalesOutLine origLine : lines) {
            SalesOutLine redLine = SalesOutLine.builder()
                    .salesOutId(savedRed.getId())
                    .warehouseId(origLine.getWarehouseId())
                    .productId(origLine.getProductId())
                    .batchNo(origLine.getBatchNo())
                    .serialNo(null) // 红字单不可复用唯一序列号
                    .qty(origLine.getQty().negate())
                    .build();
            lineRepository.save(redLine);
            // 库存反向入库
            inventoryService.applyTransaction(tenantId, origin.getDealerId(), origLine.getWarehouseId(),
                    origLine.getProductId(), origLine.getBatchNo(), origLine.getSerialNo(),
                    origLine.getQty(), "SALES_OUT_RED", "SALES_OUT", savedRed.getId());
        }
        log.info("销售单 {} 红字冲销，红字单 {}", origin.getCode(), savedRed.getCode());
        return savedRed;
    }

    // ============================================================
    // v3.7.3 重构：SAP SD/Oracle WMS 风格销售出库业务
    // ============================================================

    /**
     * v3.7.3 部分出库：可多次执行，按本次行项目扣库存。
     *  - batchNo/serialNo 必须在已有在库中存在（由前端批次选择器保证）
     *  - 序列号管理产品：serialNo 在 stock_serials 必须 shipped_at IS NULL
     *  - 累计已发（qty - cancelledQty）<= 销售单预期（来自 create 时写入的 line.quantity 或原 qty）
     */
    @Transactional
    public SalesOut partialShip(Long soId, List<SalesOutPartialShipRequest.ShipLineRequest> ships) {
        SalesOut so = salesOutRepository.findById(soId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "销售出库单不存在"));
        if (Boolean.TRUE.equals(so.getIsRed())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "红字单不允许再发货");
        }
        if ("CANCELLED".equals(so.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已取消销售单不能发货");
        }
        if (ships == null || ships.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少发货明细");
        }
        UUID tenantId = so.getTenantId();

        Long regionId = null;
        if (so.getDealerId() != null) {
            Dealer d = dealerRepository.findById(so.getDealerId()).orElse(null);
            if (d != null) regionId = d.getRegionId();
        }

        // 1. 读取当前应发行（expected_qty > 0），按 id 映射，用于按 expectedLineId 定位
        List<SalesOutLine> expectedLinesAll = lineRepository.findBySalesOutId(soId).stream()
                .filter(l -> l.getExpectedQty() != null && l.getExpectedQty().signum() > 0)
                .collect(Collectors.toList());
        Map<Long, SalesOutLine> expectedMap = expectedLinesAll.stream()
                .collect(Collectors.toMap(SalesOutLine::getId, l -> l, (a, b) -> a));
        if (expectedLinesAll.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "该销售出库单没有可发货的应发明细");
        }

        // 2. 按 expectedLineId 聚合本次请求，并校验累计不超
        Map<Long, BigDecimal> reqByLine = new java.util.HashMap<>();
        for (SalesOutPartialShipRequest.ShipLineRequest r : ships) {
            if (r.getQty() == null || r.getQty().signum() <= 0) continue;
            Long lineId = r.getExpectedLineId();
            SalesOutLine el = lineId == null ? null : expectedMap.get(lineId);
            if (el == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "发货明细缺少 expectedLineId 或找不到对应应发行 product=" + r.getProductId());
            }
            BigDecimal already = el.getShippedQty() == null ? BigDecimal.ZERO : el.getShippedQty();
            BigDecimal add = reqByLine.getOrDefault(lineId, BigDecimal.ZERO).add(r.getQty());
            if (already.add(add).compareTo(el.getExpectedQty()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "行 " + lineId + " 累计发货 " + already.add(add) + " 超过应发数 " + el.getExpectedQty());
            }
            reqByLine.merge(lineId, r.getQty(), BigDecimal::add);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<ConsignmentService.StdLine> consignLines = new ArrayList<>();
        List<com.dms.collab.ShippedLine> collabShippedLines = new ArrayList<>();

        for (SalesOutPartialShipRequest.ShipLineRequest req : ships) {
            if (req.getProductId() == null || req.getWarehouseId() == null
                    || req.getQty() == null || req.getQty().signum() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "发货明细必填 productId/warehouseId/qty(>0)");
            }
            if (req.getBatchNo() == null || req.getBatchNo().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_MISSING, "发货明细必须指定 batchNo");
            }

            boolean serialManaged = isProductSerialManaged(tenantId, req.getProductId());
            if (serialManaged && (req.getSerialNo() == null || req.getSerialNo().isBlank())) {
                throw new BusinessException(ErrorCode.PARAM_MISSING,
                        "产品 " + req.getProductId() + " 是序列号管理，必须指定 serialNo");
            }
            if (req.getSerialNo() != null && !req.getSerialNo().isBlank()) {
                StockSerial ss = stockSerialRepository
                        .findByTenantIdAndSerialNoAndShippedAtIsNull(tenantId, req.getSerialNo())
                        .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                                "序列号不在库: " + req.getSerialNo()));
                if (!ss.getBatchNo().equals(req.getBatchNo())
                        || !ss.getWarehouseId().equals(req.getWarehouseId())) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "序列号 " + req.getSerialNo() + " 批次/仓库与本次发货不匹配");
                }
                if (!"QUALIFIED".equals(ss.getStockStatus())) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "序列号 " + req.getSerialNo() + " 不是合格库存，不能出库");
                }
            }

            // 写本次发货执行行（shippedQty 用本次数量，qty 历史字段同步）
            SalesOutLine line = SalesOutLine.builder()
                    .salesOutId(soId)
                    .warehouseId(req.getWarehouseId())
                    .productId(req.getProductId())
                    .batchNo(req.getBatchNo())
                    .serialNo(req.getSerialNo())
                    .qty(req.getQty())
                    .quantity(req.getQty())
                    .shippedQty(req.getQty())
                    .unitPrice(req.getUnitPrice())
                    .build();
            SalesOutLine savedLine = lineRepository.save(line);

            inventoryService.applyTransaction(
                    tenantId, so.getDealerId(), req.getWarehouseId(),
                    req.getProductId(), req.getBatchNo(), req.getSerialNo(),
                    req.getQty().negate(), "SALES_OUT", "SALES_OUT", soId);

            if (req.getSerialNo() != null && !req.getSerialNo().isBlank()) {
                em.createNativeQuery(
                        "UPDATE stock_serials SET shipped_at = now() " +
                        "WHERE tenant_id = ?1 AND serial_no = ?2 AND shipped_at IS NULL")
                        .setParameter(1, tenantId).setParameter(2, req.getSerialNo())
                        .executeUpdate();
            }

            BigDecimal price = req.getUnitPrice() == null ? BigDecimal.ZERO : req.getUnitPrice();
            BigDecimal amount = req.getQty().multiply(price);
            totalAmount = totalAmount.add(amount);
            SalesOutFact fact = SalesOutFact.builder()
                    .tenantId(tenantId)
                    .dealerId(so.getDealerId())
                    .productId(req.getProductId())
                    .terminalId(so.getTerminalId())
                    .regionId(regionId)
                    .salesDate(so.getSalesDate() == null ? LocalDate.now() : so.getSalesDate())
                    .qty(req.getQty())
                    .amount(amount)
                    .build();
            factRepository.save(fact);
            consignLines.add(new ConsignmentService.StdLine(req.getProductId(), req.getBatchNo(), req.getSerialNo(), req.getWarehouseId(), req.getQty()));

            com.dms.collab.ShippedLine sl = new com.dms.collab.ShippedLine();
            sl.setProductId(req.getProductId());
            sl.setQty(req.getQty());
            sl.setBatchNo(req.getBatchNo());
            sl.setSerialNo(req.getSerialNo());
            sl.setOutLineId(savedLine.getId());
            Object pc = em.createNativeQuery("SELECT code FROM products WHERE id=?1 AND tenant_id=?2")
                    .setParameter(1, req.getProductId()).setParameter(2, tenantId).getResultList().stream().findFirst().orElse(null);
            sl.setProductCode(pc == null ? String.valueOf(req.getProductId()) : String.valueOf(pc));
            collabShippedLines.add(sl);

            log.info("部分出库 soId={} execLineId={} product={} qty={}", soId, savedLine.getId(), req.getProductId(), req.getQty());
        }

        // 4. 更新应发行累计 shipped_qty（重读最新值，避免持久化上下文陈旧）
        @SuppressWarnings("unchecked")
        List<SalesOutLine> expectedLatest = lineRepository.findBySalesOutId(soId).stream()
                .filter(l -> l.getExpectedQty() != null && l.getExpectedQty().signum() > 0)
                .collect(Collectors.toList());
        for (SalesOutLine el : expectedLatest) {
            BigDecimal addQty = reqByLine.getOrDefault(el.getId(), BigDecimal.ZERO);
            if (addQty.signum() <= 0) continue;
            BigDecimal newShipped = (el.getShippedQty() == null ? BigDecimal.ZERO : el.getShippedQty()).add(addQty);
            el.setShippedQty(newShipped);
            el.setQty(newShipped);
            lineRepository.save(el);
        }

        // 5. 重算状态：所有应发行都 shipped+取消 >= expected → COMPLETED；否则 PARTIAL_SHIPPED
        boolean allDone = true;
        for (SalesOutLine el : expectedLatest) {
            BigDecimal shipped = el.getShippedQty() == null ? BigDecimal.ZERO : el.getShippedQty();
            BigDecimal ccl = el.getCancelledQty() == null ? BigDecimal.ZERO : el.getCancelledQty();
            if (shipped.add(ccl).compareTo(el.getExpectedQty()) < 0) { allDone = false; break; }
        }

        String newStatus = allDone ? "COMPLETED" : "PARTIAL_SHIPPED";
        so.setStatus(newStatus);
        so.setAmountInclTax(totalAmount);
        so.setShippedAt(allDone ? OffsetDateTime.now() : so.getShippedAt());
        so.setCompletedAt(allDone ? OffsetDateTime.now() : null);
        so.setUpdatedAt(OffsetDateTime.now());
        SalesOut saved = salesOutRepository.save(so);

        // 6. 同步回写源销售订单状态：APPROVED → SHIPPING；全部完成 → COMPLETED
        if (so.getSourceOrderId() != null) {
            String orderStatus = allDone ? "COMPLETED" : "SHIPPING";
            try {
                int upd = em.createNativeQuery(
                        "UPDATE orders SET status = ?1, completed_at = CASE WHEN ?1 = 'COMPLETED' THEN now() ELSE completed_at END, updated_at = now() " +
                        "WHERE id = ?2 AND tenant_id = ?3 AND status IN ('APPROVED','SHIPPING')")
                        .setParameter(1, orderStatus).setParameter(2, so.getSourceOrderId()).setParameter(3, tenantId)
                        .executeUpdate();
                if (upd > 0) log.info("销售订单 {} 状态同步为 {} (so={})", so.getSourceOrderId(), orderStatus, soId);
            } catch (Exception e) {
                log.warn("回写源订单状态失败 orderId={}: {}", so.getSourceOrderId(), e.getMessage());
            }
        }

        // v4.4.0 补货订单发货 -> 计入经销商寄售库存（厂家库存已在上面扣减）
        try {
            if (so.getSourceOrderId() != null) {
                Object ot = em.createNativeQuery("SELECT order_type FROM orders WHERE id=?1 AND tenant_id=?2")
                        .setParameter(1, so.getSourceOrderId()).setParameter(2, tenantId).getResultList().stream().findFirst().orElse(null);
                if (ot != null && "REPLENISHMENT".equals(String.valueOf(ot))) {
                    consignmentService.onReplenishShipped(so.getDealerId(), soId, consignLines);
                }
            }
        } catch (Exception cex) {
            log.error("补货寄售库存回写失败 soId={}: {}", soId, cex.getMessage(), cex);
            throw cex;
        }
        // v4.5.0 跨租户：向绑定的经销商租户回传待收货入库单（对码缺失抛错，随发货事务回滚）
        try {
            crossTenantCollab.onSalesOutShipped(soId, collabShippedLines);
        } catch (com.dms.common.BusinessException be) {
            throw be;
        } catch (Exception ce) {
            log.error("跨租户出库回传失败 soId={}: {}", soId, ce.getMessage(), ce);
            throw ce;
        }
        // v4.5.4 平台外经销商：发货报文 webhook 回传（事务提交后异步推送，失败仅落台账重试，不阻断发货）
        try {
            externalCollabWebhook.registerOutbound(soId, collabShippedLines, Boolean.TRUE.equals(so.getIsRed()));
        } catch (Exception we) {
            log.warn("外部经销商发货回传登记失败 soId={}: {}", soId, we.getMessage());
        }
        return saved;
    }

    /**
     * v3.7.3 部分取消：按明细行恢复库存。
     */
    @Transactional
    public SalesOut cancelPartial(Long soId, List<SalesOutCancelLineRequest> cancels, String reason) {
        SalesOut so = salesOutRepository.findById(soId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "销售出库单不存在"));
        if (Boolean.TRUE.equals(so.getIsRed())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "红字单不允许取消");
        }
        if ("CANCELLED".equals(so.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "销售单已取消");
        }
        if (cancels == null || cancels.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少取消明细");
        }

        UUID tenantId = so.getTenantId();

        for (SalesOutCancelLineRequest req : cancels) {
            if (req.getLineId() == null || req.getCancelQty() == null
                    || req.getCancelQty().signum() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "取消明细需 lineId 和正数 cancelQty");
            }
            SalesOutLine line = lineRepository.findById(req.getLineId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "出库明细行不存在: " + req.getLineId()));
            if (!soId.equals(line.getSalesOutId())) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "明细行不属于该销售单");
            }
            BigDecimal shipped = line.getQty() == null
                    ? (line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity())
                    : line.getQty();
            BigDecimal alreadyCcl = line.getCancelledQty() == null ? BigDecimal.ZERO : line.getCancelledQty();
            BigDecimal cancellable = shipped.subtract(alreadyCcl);
            if (req.getCancelQty().compareTo(cancellable) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "明细行 " + line.getId() + " 取消数量 " + req.getCancelQty()
                        + " 超过可取消数 " + cancellable);
            }

            // 库存恢复（正数）
            inventoryService.applyTransactionWithSource(
                    tenantId, so.getDealerId(), line.getWarehouseId(),
                    line.getProductId(), line.getBatchNo(), line.getSerialNo(),
                    req.getCancelQty(), "SALES_OUT_CANCEL", "SALES_OUT", soId,
                    line.getId(), null);

            // 序列号解锁定
            if (line.getSerialNo() != null && !line.getSerialNo().isBlank()) {
                em.createNativeQuery(
                        "UPDATE stock_serials SET shipped_at = NULL " +
                        "WHERE tenant_id = ?1 AND serial_no = ?2")
                        .setParameter(1, tenantId).setParameter(2, line.getSerialNo())
                        .executeUpdate();
            }

            line.setCancelledQty(alreadyCcl.add(req.getCancelQty()));
            line.setCancelledAt(OffsetDateTime.now());
            lineRepository.save(line);
        }

        // 重新评估状态：若所有应发行都发满或取消则 CANCELLED/COMPLETED；否则保持 PARTIAL_SHIPPED
        List<SalesOutLine> all = lineRepository.findBySalesOutId(soId);
        boolean allSettled = true;
        boolean anyShipped = false;
        boolean anyExpected = false;
        for (SalesOutLine l : all) {
            BigDecimal expected = l.getExpectedQty() == null ? BigDecimal.ZERO : l.getExpectedQty();
            if (expected.signum() <= 0) continue;
            anyExpected = true;
            BigDecimal shipped = l.getShippedQty() == null ? BigDecimal.ZERO : l.getShippedQty();
            BigDecimal ccl = l.getCancelledQty() == null ? BigDecimal.ZERO : l.getCancelledQty();
            if (shipped.add(ccl).compareTo(expected) < 0) allSettled = false;
            if (shipped.signum() > 0) anyShipped = true;
        }
        if (!anyExpected) {
            so.setStatus("CANCELLED");
        } else if (allSettled && anyShipped) {
            so.setStatus("COMPLETED");
            so.setCompletedAt(OffsetDateTime.now());
        } else if (allSettled) {
            so.setStatus("CANCELLED");
            so.setCancelledAt(OffsetDateTime.now());
        } else {
            so.setStatus("PARTIAL_SHIPPED");
        }
        so.ensureJson();
        if (reason != null && !reason.isBlank()) {
            so.getSurgeryInfo().put("cancelReason", reason);
            so.getSurgeryInfo().put("cancelType", "PARTIAL");
            so.getSurgeryInfo().put("cancelAt", com.dms.common.util.DateFmt.fmt(OffsetDateTime.now()));
        }
        so.setUpdatedAt(OffsetDateTime.now());
        return salesOutRepository.save(so);
    }

    /**
     * v3.7.3 整单作废：所有已发货的库存全部恢复。
     */
    @Transactional
    public SalesOut cancelFull(Long soId, String reason) {
        SalesOut so = salesOutRepository.findById(soId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "销售出库单不存在"));
        if (Boolean.TRUE.equals(so.getIsRed())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "红字单不允许取消");
        }
        if ("CANCELLED".equals(so.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "销售单已取消");
        }
        UUID tenantId = so.getTenantId();

        List<SalesOutLine> lines = lineRepository.findBySalesOutId(soId);
        if (lines != null) {
            for (SalesOutLine l : lines) {
                BigDecimal shipped = l.getQty() == null
                        ? (l.getQuantity() == null ? BigDecimal.ZERO : l.getQuantity())
                        : l.getQty();
                BigDecimal alreadyCcl = l.getCancelledQty() == null ? BigDecimal.ZERO : l.getCancelledQty();
                BigDecimal rollback = shipped.subtract(alreadyCcl);
                if (rollback.signum() <= 0) continue;
                inventoryService.applyTransactionWithSource(
                        tenantId, so.getDealerId(), l.getWarehouseId(),
                        l.getProductId(), l.getBatchNo(), l.getSerialNo(),
                        rollback, "SALES_OUT_CANCEL", "SALES_OUT", soId,
                        l.getId(), null);
                if (l.getSerialNo() != null && !l.getSerialNo().isBlank()) {
                    em.createNativeQuery(
                            "UPDATE stock_serials SET shipped_at = NULL " +
                            "WHERE tenant_id = ?1 AND serial_no = ?2")
                            .setParameter(1, tenantId).setParameter(2, l.getSerialNo())
                            .executeUpdate();
                }
                l.setCancelledQty(shipped);
                l.setCancelledAt(OffsetDateTime.now());
                lineRepository.save(l);
            }
        }

        so.setStatus("CANCELLED");
        so.setCancelledAt(OffsetDateTime.now());
        so.ensureJson();
        if (reason != null && !reason.isBlank()) {
            so.getSurgeryInfo().put("cancelReason", reason);
            so.getSurgeryInfo().put("cancelType", "FULL");
            so.getSurgeryInfo().put("cancelAt", com.dms.common.util.DateFmt.fmt(OffsetDateTime.now()));
        }
        so.setUpdatedAt(OffsetDateTime.now());
        return salesOutRepository.save(so);
    }

    private boolean isProductSerialManaged(UUID tenantId, Long productId) {
        try {
            var q = em.createNativeQuery(
                    "SELECT is_serial_managed FROM products WHERE id = ?1 AND tenant_id = ?2");
            q.setParameter(1, productId).setParameter(2, tenantId);
            List<?> rs = q.getResultList();
            if (rs.isEmpty() || rs.get(0) == null) return false;
            Object v = rs.get(0);
            if (v instanceof Boolean) return (Boolean) v;
            return Boolean.parseBoolean(String.valueOf(v));
        } catch (Exception e) {
            log.warn("查询产品 is_serial_managed 失败: {}", e.getMessage());
            return false;
        }
    }
    private String productLabel(Long productId) {
        if (productId == null) return "未知产品";
        try {
            Object[] row = (Object[]) em.createNativeQuery(
                    "SELECT code, name_cn FROM products WHERE id = ?1")
                    .setParameter(1, productId).getResultStream().findFirst().orElse(null);
            if (row != null) {
                String code = row[0] == null ? "" : String.valueOf(row[0]);
                String name = row[1] == null ? "" : String.valueOf(row[1]);
                return (code + " " + name).trim();
            }
        } catch (Exception ignored) {}
        return String.valueOf(productId);
    }
}
