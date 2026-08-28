package com.dms.v4;

import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.voucher.dto.VoucherAcquireRequest;
import com.dms.voucher.service.CustomerVoucherService;
import com.dms.consignment.service.ConsignmentService;
import com.dms.consignment.service.InvoiceOrderApprovalCallback;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class V4OrderService {
    private final EntityManager em;
    private final DocNoGenerator docNoGenerator;
    private final V4PricingService pricing;
    private final V4PriceEngine priceEngine;
    @Lazy
    private final CustomerVoucherService voucherService;
    @Lazy
    private final ApprovalService approvalService;
    @Lazy
    private final ConsignmentService consignmentService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public Map<String, Object> previewSalesOrder(Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long dealerId = longRequired(body.get("dealerId"), "dealer");
        boolean applyPromotions = !Boolean.FALSE.equals(body.get("applyPromotions"));
        V4CalcResult result = calculate(tid, dealerId, body, applyPromotions);
        List<V4Line> lines = result.getLines();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lines", lines);
        data.put("promotionMessages", result.getPromotionMessages());
        data.put("pricingMode", result.getPricingMode());
        data.put("amountInclTax", result.getOriginalAmount());
        data.put("discountAmount", sum(lines, "discount"));
        data.put("finalAmount", result.getFinalAmount());
        data.put("voucherId", result.getVoucherId());
        data.put("voucherAmount", result.getVoucherAmount());
        data.put("payableAmount", result.getPayableAmount());
        data.put("productDiscountTotal", result.getProductDiscountTotal());
        data.put("promoDiscountTotal", result.getPromoDiscountTotal());
        data.put("lineDiscountTotal", result.getLineDiscountTotal());
        data.put("dealerDiscountTotal", result.getDealerDiscountTotal());
        data.put("headerDiscountTotal", result.getHeaderDiscountTotal());
        data.put("taxAmount", result.getTaxAmount());
        data.put("amountExclTax", result.getAmountExclTax());
        return data;
    }

    @Transactional
    public Map<String, Object> createSalesOrder(Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long dealerId = longRequired(body.get("dealerId"), "dealer");
        String orderType = str(body.get("orderType"), "NORMAL");
        boolean isRed = "true".equalsIgnoreCase(String.valueOf(body.getOrDefault("isRed", false)));
        if (isRed && !"REPLENISHMENT".equals(orderType)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "红字订单仅支持补货类型（REPLENISHMENT），用于寄售补货红冲");
        }
        V4CalcResult result = calculate(tid, dealerId, body, true);
        List<V4Line> lines = result.getLines();
        if (isRed) {
            @SuppressWarnings("unchecked")
            List<Map<String,Object>> redRows = (List<Map<String,Object>>) body.getOrDefault("lines", List.of());
            validateReplenishRed(tid, dealerId, redRows);
        }
        String code = docNoGenerator.next(isRed ? "SOR" : "SO");
        var q = em.createNativeQuery("INSERT INTO orders (tenant_id,code,order_type,dealer_id,ship_snapshot,status,amount_incl_tax,discount_amount,final_amount,tax_amount,amount_excl_tax,header_discount_type,header_discount_value,expected_date,remark,extra,is_red,erp_status,pricing_mode,voucher_id,voucher_amount,promo_messages,pricing_snapshot,terminal_hospital_id,sample_reason,created_at,updated_at,created_by) VALUES (?1,?2,?3,?4,CAST(?5 AS jsonb),'DRAFT',?6,?7,?8,?9,?10,?11,?12,CAST(?13 AS date),?14,CAST(?15 AS jsonb),?24,'NOT_REQUIRED',?17,?18,?19,CAST(?20 AS jsonb),CAST(?21 AS jsonb),?22,?23,now(),now(),?16) RETURNING id");
        q.setParameter(1, tid).setParameter(2, code).setParameter(3, orderType).setParameter(4, dealerId).setParameter(5, snapshot(dealerId));
        setAmountParams(q, 6, lines);
        q.setParameter(11, body.get("headerDiscountType")).setParameter(12, bd(body.get("headerDiscountValue"))).setParameter(13, body.get("expectedDate")).setParameter(14, body.get("remark")).setParameter(15, json(body.get("extra"))).setParameter(16, TenantContext.getUserId());
        q.setParameter(17, result.getPricingMode()).setParameter(18, result.getVoucherId()).setParameter(19, result.getVoucherAmount());
        q.setParameter(20, json(result.getPromotionMessages())).setParameter(21, json(pricingSnapshot(result)))
                .setParameter(22, body.get("terminalHospitalId") == null ? null : Long.valueOf(String.valueOf(body.get("terminalHospitalId"))))
                .setParameter(23, body.get("sampleReason"));
        q.setParameter(24, isRed);
        Long id = ((Number) q.getSingleResult()).longValue();
        insertLines(id, lines);
        syncShipAddress(id, body);
        if (isRed) {
            Long refOrderId = toLong(body.get("refOrderId"));
            Long refSalesOutId = toLong(body.get("refSalesOutId"));
            if (refOrderId != null || refSalesOutId != null) {
                em.createNativeQuery("UPDATE orders SET ref_order_id=?1, ref_sales_out_id=?2, updated_at=now() WHERE id=?3 AND tenant_id=?4")
                        .setParameter(1, refOrderId).setParameter(2, refSalesOutId).setParameter(3, id).setParameter(4, tid).executeUpdate();
            }
        }
        return Map.of("id", id, "code", code);
    }

    @Transactional
    public Map<String, Object> updateSalesOrder(Long id, Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (!List.of("DRAFT", "REJECTED").contains(status(id, tid))) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有草稿或驳回状态的订单才能编辑或提交");
        Long dealerId = longRequired(body.get("dealerId"), "dealer");
        V4CalcResult result = calculate(tid, dealerId, body, true);
        List<V4Line> lines = result.getLines();
        var q = em.createNativeQuery("UPDATE orders SET order_type=?1,dealer_id=?2,ship_snapshot=CAST(?3 AS jsonb),amount_incl_tax=?4,discount_amount=?5,final_amount=?6,tax_amount=?7,amount_excl_tax=?8,header_discount_type=?9,header_discount_value=?10,expected_date=CAST(?11 AS date),remark=?12,extra=CAST(?13 AS jsonb),pricing_mode=?16,voucher_id=?17,voucher_amount=?18,promo_messages=CAST(?19 AS jsonb),pricing_snapshot=CAST(?20 AS jsonb),updated_at=now() WHERE id=?14 AND tenant_id=?15");
        q.setParameter(1, str(body.get("orderType"), "NORMAL")).setParameter(2, dealerId).setParameter(3, snapshot(dealerId));
        setAmountParams(q, 4, lines);
        q.setParameter(9, body.get("headerDiscountType")).setParameter(10, bd(body.get("headerDiscountValue"))).setParameter(11, body.get("expectedDate")).setParameter(12, body.get("remark")).setParameter(13, json(body.get("extra"))).setParameter(14, id).setParameter(15, tid);
        q.setParameter(16, result.getPricingMode()).setParameter(17, result.getVoucherId()).setParameter(18, result.getVoucherAmount());
        q.setParameter(19, json(result.getPromotionMessages())).setParameter(20, json(pricingSnapshot(result)));
        q.executeUpdate();
        em.createNativeQuery("DELETE FROM order_lines WHERE order_id=?1").setParameter(1, id).executeUpdate();
        insertLines(id, lines);
        syncShipAddress(id, body);
        return Map.of("id", id);
    }

    @Transactional
    public Map<String, Object> submit(Long id) {
        UUID tid = TenantContext.getTenantId();
        if (!List.of("DRAFT", "REJECTED").contains(status(id, tid))) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有草稿或驳回状态的订单才能编辑或提交");
        Tuple o = order(id, tid);
        Long dealerId = toLong(o.get("dealer_id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dealerId", dealerId);
        body.put("headerDiscountType", o.get("header_discount_type"));
        body.put("headerDiscountValue", o.get("header_discount_value"));
        body.put("pricingMode", o.get("pricing_mode"));
        body.put("voucherId", o.get("voucher_id"));
        body.put("orderId", id);
        body.put("fixedPrice", snapshotValue(o.get("pricing_snapshot"), "fixedPrice"));
        body.put("headerDiscountDirection", snapshotValue(o.get("pricing_snapshot"), "headerDiscountDirection"));
        List<Map<String,Object>> rows = new ArrayList<>();
        Map<String, List<Map<String,Object>>> childDiscounts = new HashMap<>();
        for (Tuple t : linesOf(id)) {
            Map<String,Object> m = new HashMap<>();
            m.put("productId", t.get("product_id")); m.put("qty", t.get("qty")); m.put("lineDiscountType", t.get("line_discount_type")); m.put("lineDiscountValue", t.get("line_discount_value")); m.put("lineDiscountDirection", t.get("line_discount_direction")); m.put("isGift", t.get("is_gift")); m.put("bomParentProductId", t.get("bom_parent_product_id")); m.put("bomVersion", t.get("bom_version")); m.put("bomGroupNo", t.get("bom_group_no")); m.put("componentQty", t.get("component_qty")); m.put("lineZero", t.get("line_zero")); m.put("batchNo", t.get("batch_no")); m.put("serialNo", t.get("serial_no")); m.put("consignmentStockId", t.get("consignment_stock_id"));
            if ("CHILD".equals(String.valueOf(t.get("line_level")))) {
                String groupNo = t.get("bom_group_no") == null ? null : String.valueOf(t.get("bom_group_no"));
                if (groupNo != null) childDiscounts.computeIfAbsent(groupNo, k -> new ArrayList<>()).add(Map.of(
                        "productId", t.get("product_id"),
                        "lineDiscountType", t.get("line_discount_type") == null ? "" : t.get("line_discount_type"),
                        "lineDiscountValue", t.get("line_discount_value") == null ? BigDecimal.ZERO : t.get("line_discount_value")
                ));
                continue;
            }
            if (t.get("bom_parent_line_id") != null) continue;
            rows.add(m);
        }
        childDiscounts.forEach((group, discounts) -> {
            for (Map<String,Object> row : rows) {
                if (group.equals(String.valueOf(row.get("bomGroupNo")))) row.put("childDiscounts", discounts);
            }
        });
        body.put("lines", rows);
        V4CalcResult result = calculate(tid, dealerId, body, true);
        List<V4Line> lines = result.getLines();
        em.createNativeQuery("DELETE FROM order_lines WHERE order_id=?1").setParameter(1, id).executeUpdate();
        insertLines(id, lines);
        em.createNativeQuery("UPDATE orders SET status='PENDING_APPROVAL',submitted_at=now(),amount_incl_tax=?1,discount_amount=?2,final_amount=?3,tax_amount=?4,amount_excl_tax=?5,erp_status='PENDING_PUSH',pricing_mode=?6,voucher_id=?7,voucher_amount=?8,promo_messages=CAST(?9 AS jsonb),pricing_snapshot=CAST(?10 AS jsonb),updated_at=now() WHERE id=?11 AND tenant_id=?12")
                .setParameter(1,result.getOriginalAmount()).setParameter(2,sum(lines,"discount")).setParameter(3,result.getFinalAmount())
                .setParameter(4,result.getTaxAmount()).setParameter(5,result.getAmountExclTax())
                .setParameter(6,result.getPricingMode()).setParameter(7,result.getVoucherId()).setParameter(8,result.getVoucherAmount())
                .setParameter(9,json(result.getPromotionMessages())).setParameter(10,json(pricingSnapshot(result)))
                .setParameter(11,id).setParameter(12,tid).executeUpdate();
        if (result.getVoucherId() != null && result.getVoucherAmount() != null && result.getVoucherAmount().signum() > 0) {
            VoucherAcquireRequest req = new VoucherAcquireRequest();
            req.setVoucherId(result.getVoucherId());
            req.setOrderId(id);
            req.setOrderCode(String.valueOf(o.get("code")));
            req.setUsedAmount(result.getVoucherAmount());
            req.setOrderOriginalAmount(result.getOriginalAmount());
            voucherService.acquire(req);
        }
        recordPromotionHits(id, lines);
        // v4.4.0 开票订单：提交即预占寄售库存（审批通过实扣，拒绝/退回/撤回释放）
        String orderType = str(o.get("order_type"), "SALES");
        boolean invoiceOrder = "INVOICE".equals(orderType);
        if (invoiceOrder) {
            List<ConsignmentService.StdLine> stockLines = new ArrayList<>();
            for (Tuple lt : linesOf(id)) {
                Object pid = lt.get("product_id"); Object qq = lt.get("qty");
                if (pid == null || qq == null) continue;
                Object sid = lt.get("consignment_stock_id");
                stockLines.add(new ConsignmentService.StdLine(
                    ((Number) pid).longValue(),
                    lt.get("batch_no") == null ? null : String.valueOf(lt.get("batch_no")),
                    lt.get("serial_no") == null ? null : String.valueOf(lt.get("serial_no")),
                    null,
                    new BigDecimal(String.valueOf(qq)),
                    sid == null ? null : ((Number) sid).longValue()));
            }
            consignmentService.lockForInvoice(dealerId, id, String.valueOf(o.get("code")), stockLines);
        }
        ApprovalInstance instance;
        try {
            StartApprovalRequest request = new StartApprovalRequest();
            request.setBusinessType(invoiceOrder ? InvoiceOrderApprovalCallback.BUSINESS_TYPE : "SALES_ORDER");
            request.setBusinessId(id);
            request.setBusinessCode(String.valueOf(o.get("code")));
            request.setTitle((invoiceOrder ? "开票订单审批：" : "销售订单审批：") + request.getBusinessCode());
            instance = approvalService.start(request);
        } catch (Exception e) {
            em.createNativeQuery("UPDATE orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2").setParameter(1,id).setParameter(2,tid).executeUpdate();
            throw e;
        }
        boolean approved = "APPROVED".equals(instance.getStatus().name()) || "AUTO_APPROVED".equals(instance.getStatus().name());
        return Map.of("id", id, "newStatus", approved ? "APPROVED" : "PENDING_APPROVAL", "approvalInstanceId", instance.getId(), "autoApproved", approved);
    }

    @Transactional
    public Map<String, Object> cancel(Long id) {
        UUID tid = TenantContext.getTenantId();
        String status = status(id, tid);
        if (!List.of("DRAFT", "PENDING_APPROVAL", "REJECTED", "APPROVED").contains(status)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "当前订单状态不允许取消");
        if (shippedQty(id, tid).signum() > 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "订单已有发货记录，不能取消");
        em.createNativeQuery("UPDATE orders SET status='CANCELLED',cancelled_at=now(),erp_status=CASE WHEN status='APPROVED' THEN 'CANCEL_PENDING' ELSE erp_status END,updated_at=now() WHERE id=?1 AND tenant_id=?2").setParameter(1, id).setParameter(2, tid).executeUpdate();
        // 整单未出库作废：返还已核销的代金券
        voucherService.release(id);
        return Map.of("id", id, "status", "CANCELLED");
    }

    @Transactional
    public Map<String, Object> approvePushErp(Long id, boolean red) {
        UUID tid = TenantContext.getTenantId();
        em.createNativeQuery("UPDATE orders SET status='APPROVED',approved_at=now(),erp_status='PUSH_SUCCESS',erp_pushed_at=now(),erp_error=NULL,updated_at=now() WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=?3").setParameter(1, id).setParameter(2, tid).setParameter(3, red).executeUpdate();
        log.info("ERP push placeholder executed order={} red={}", id, red);
        return Map.of("id", id, "erpStatus", "PUSH_SUCCESS");
    }

    private V4CalcResult calculate(UUID tid, Long dealerId, Map<String,Object> body, boolean applyPromotions) {
        @SuppressWarnings("unchecked") List<Map<String,Object>> rows = new ArrayList<>((List<Map<String,Object>>) body.getOrDefault("lines", List.of()));
        String orderType = str(body.get("orderType"), "SALES");
        // v4.4.0 订单类型计价规则
        boolean isReplenish = "REPLENISHMENT".equals(orderType);   // 补货：全部0金额、无折扣、无促销
        boolean isSample = "SAMPLE".equals(orderType);            // 样品：单品、0金额、无折扣促销、需申请原因
        boolean isInvoice = "INVOICE".equals(orderType);          // 开票：按合同/客户/全局折扣重计价，不参与满减满赠/代金券/一口价/0金额
        boolean isCustom = "CUSTOM".equals(orderType);            // 定制：保留选项，暂按普通订单
        if (isReplenish) {
            for (Map<String,Object> r : rows) {
                r.put("lineZero", true);
                r.put("lineDiscountType", null);
                r.put("lineDiscountValue", null);
            }
            body.put("headerDiscountType", null);
            body.put("headerDiscountValue", null);
            body.put("voucherId", null);
            body.put("pricingMode", "NORMAL");
            body.put("fixedPrice", null);
            applyPromotions = false;
            validateDealerConsignment(tid, dealerId, "补货订单");
        } else if (isSample) {
            long rootCount = rows.stream().filter(r -> {
                String lvl = r.get("lineLevel") == null ? "" : String.valueOf(r.get("lineLevel"));
                return !"CHILD".equals(lvl);
            }).count();
            if (rootCount > 1) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "样品订单只能包含一个样品（单品）");
            }
            Object reason = body.get("sampleReason");
            if (reason == null || String.valueOf(reason).isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_MISSING, "样品订单必须填写申请样品原因");
            }
            for (Map<String,Object> r : rows) {
                r.put("lineZero", true);
                r.put("lineDiscountType", null);
                r.put("lineDiscountValue", null);
            }
            body.put("headerDiscountType", null);
            body.put("headerDiscountValue", null);
            body.put("voucherId", null);
            body.put("pricingMode", "NORMAL");
            body.put("fixedPrice", null);
            applyPromotions = false;
        } else if (isInvoice) {
            body.put("voucherId", null);
            body.put("pricingMode", "NORMAL");
            body.put("fixedPrice", null);
            for (Map<String,Object> r : rows) { r.put("lineZero", false); }
            applyPromotions = false; // 不参与满减/满赠；产品促销/经销商折扣/行折扣/整单折扣仍由引擎按合同/客户/全局价计算
            validateDealerConsignment(tid, dealerId, "开票订单");
            validateInvoiceConsignment(tid, dealerId, rows);
        }
        Map<String,Object> params = new LinkedHashMap<>(body);
        params.put("dealerId", dealerId);
        return priceEngine.calculate(tid, dealerId, rows, applyPromotions, params);
    }

    /**
     * v4.4.0 开票订单：每行必须来自该经销商自身的可用寄售库存（产品+批号+序列号维度），
     * 且开票数量不能超过该维度可用量（在库-已锁定）。提交时 lockForInvoice 会再次做并发兜底校验。
     */
    private void validateInvoiceConsignment(UUID tid, Long dealerId, List<Map<String,Object>> rows) {
        for (Map<String,Object> r : rows) {
            Long pid = toLong(r.get("productId"));
            if (pid == null) continue;
            String lvl = r.get("lineLevel") == null ? "" : String.valueOf(r.get("lineLevel"));
            if ("CHILD".equals(lvl)) continue; // BOM 子件不独立开票
            BigDecimal need = bd(r.get("qty"));
            if (need == null || need.signum() <= 0)
                throw new BusinessException(ErrorCode.PARAM_MISSING, "开票订单行数量必须大于 0");
            String batch = r.get("batchNo") == null || String.valueOf(r.get("batchNo")).isBlank() ? null : String.valueOf(r.get("batchNo"));
            String serial = r.get("serialNo") == null || String.valueOf(r.get("serialNo")).isBlank() ? null : String.valueOf(r.get("serialNo"));
            List<Tuple> st = em.createNativeQuery(
                "SELECT COALESCE(on_hand_qty,0) oh, COALESCE(locked_qty,0) lk FROM consignment_stock " +
                "WHERE tenant_id=?1 AND dealer_id=?2 AND product_id=?3 " +
                "AND COALESCE(batch_no,'')=COALESCE(?4,'') AND COALESCE(serial_no,'')=COALESCE(?5,'')", Tuple.class)
                .setParameter(1,tid).setParameter(2,dealerId).setParameter(3,pid)
                .setParameter(4,batch).setParameter(5,serial).getResultList();
            String pname = productLabel(pid);
            if (st.isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "开票产品 [" + pname + "]" + (batch==null?"":" 批号 "+batch) + (serial==null?"":" 序列号 "+serial)
                    + " 不在该经销商的寄售库存中；开票订单只能选择该经销商自己的寄售库存");
            }
            int avail = ((Number)st.get(0).get("oh")).intValue() - ((Number)st.get(0).get("lk")).intValue();
            if (avail < need.intValue()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "开票产品 [" + pname + "]" + (batch==null?"":" 批号 "+batch) + (serial==null?"":" 序列号 "+serial)
                    + " 寄售可用量 " + avail + "，本次开票需 " + need.intValue());
            }
        }
    }

    /**
     * v4.4.1 红字补货单（寄售补货红冲）：每行按 经销商+产品+批号+序列号 维度匹配寄售台账，
     * 红冲数量不得超过该维度在库可用量（on_hand - locked）；红冲回调 onReplenishReversed 会扣减 on_hand。
     */
    private void validateReplenishRed(UUID tid, Long dealerId, List<Map<String,Object>> rows) {
        for (Map<String,Object> r : rows) {
            Long pid = toLong(r.get("productId"));
            if (pid == null) continue;
            String lvl = r.get("lineLevel") == null ? "" : String.valueOf(r.get("lineLevel"));
            if ("CHILD".equals(lvl)) continue;
            BigDecimal need = bd(r.get("qty"));
            if (need.signum() <= 0)
                throw new BusinessException(ErrorCode.PARAM_MISSING, "红字补货单数量必须大于 0");
            String batch = r.get("batchNo") == null || String.valueOf(r.get("batchNo")).isBlank() ? null : String.valueOf(r.get("batchNo"));
            String serial = r.get("serialNo") == null || String.valueOf(r.get("serialNo")).isBlank() ? null : String.valueOf(r.get("serialNo"));
            List<Tuple> st = em.createNativeQuery(
                "SELECT COALESCE(on_hand_qty,0) oh, COALESCE(locked_qty,0) lk FROM consignment_stock " +
                "WHERE tenant_id=?1 AND dealer_id=?2 AND product_id=?3 " +
                "AND COALESCE(batch_no,'')=COALESCE(?4,'') AND COALESCE(serial_no,'')=COALESCE(?5,'')", Tuple.class)
                .setParameter(1,tid).setParameter(2,dealerId).setParameter(3,pid)
                .setParameter(4,batch).setParameter(5,serial).getResultList();
            String pname = productLabel(pid);
            if (st.isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "红冲产品 [" + pname + "]" + (batch==null?"":" 批号 "+batch) + (serial==null?"":" 序列号 "+serial)
                    + " 不在该经销商的寄售库存中，不能红冲");
            }
            int avail = ((Number)st.get(0).get("oh")).intValue() - ((Number)st.get(0).get("lk")).intValue();
            if (avail < need.intValue()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "红冲产品 [" + pname + "]" + (batch==null?"":" 批号 "+batch) + (serial==null?"":" 序列号 "+serial)
                    + " 寄售可用量 " + avail + "，本次红冲需 " + need.intValue());
            }
        }
    }

    private String productLabel(Long productId) {
        try {
            Object v = em.createNativeQuery("SELECT COALESCE(code,'')||' '||COALESCE(name_cn,'') FROM products WHERE id=?1")
                    .setParameter(1, productId).getSingleResult();
            return v == null ? String.valueOf(productId) : String.valueOf(v);
        } catch (Exception e) { return String.valueOf(productId); }
    }

    /** v4.4.0：补货/开票订单要求经销商已开启寄售库存。 */
    private void validateDealerConsignment(UUID tid, Long dealerId, String label) {
        try {
            Object v = em.createNativeQuery("SELECT COALESCE(consignment_enabled,false) FROM dealers WHERE id=?1 AND tenant_id=?2")
                    .setParameter(1, dealerId).setParameter(2, tid).getSingleResult();
            boolean enabled = v != null && Boolean.parseBoolean(String.valueOf(v));
            if (!enabled) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "该经销商未开启寄售库存，不能下" + label + "；请先在经销商主数据中开启寄售库存");
            }
        } catch (BusinessException be) { throw be; }
        catch (Exception e) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "校验经销商寄售开关失败: " + e.getMessage());
        }
    }

    private void insertLines(Long orderId, List<V4Line> lines) {
        int seq = 1;
        java.util.Map<String, Long> parentLineIds = new java.util.HashMap<>();
        for (V4Line l : lines) {
            jakarta.persistence.Query q = em.createNativeQuery("INSERT INTO order_lines (order_id,seq,product_id,product_code,product_name,product_spec,qty,unit_price,tax_rate,sub_total,standard_price_incl_tax,line_discount_type,line_discount_value,line_discount_amount,promo_discount_amount,header_discount_amount,discount_amount,final_amount,amount_excl_tax,tax_amount,is_gift,bom_parent_product_id,bom_version,bom_group_no,component_qty,line_level,is_group_header,bom_parent_line_id,price_snapshot,base_price_incl_tax,price_source,product_discount_rate,product_discount_amount,promo_type,promotion_id,promo_hit_id,unit_price_incl_tax,batch_no,serial_no,line_zero,consignment_stock_id,created_at,updated_at) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22,?23,?24,?25,?26,?27,?28,CAST(?29 AS jsonb),?30,?31,?32,?33,?34,?35,?36,?37,?38,?39,?40,?41,now(),now()) RETURNING id");
            q.setParameter(1,orderId).setParameter(2,seq++).setParameter(3,l.getProductId()).setParameter(4,l.getProductCode()).setParameter(5,l.getProductName()).setParameter(6,l.getProductSpec()).setParameter(7,l.getQty()).setParameter(8,l.getStandardPriceInclTax()).setParameter(9,l.getTaxRate()).setParameter(10,l.getStandardAmount()).setParameter(11,l.getStandardPriceInclTax()).setParameter(12,l.getLineDiscountType()).setParameter(13,l.getLineDiscountValue()==null?BigDecimal.ZERO:l.getLineDiscountValue()).setParameter(14,l.getLineDiscountAmount()).setParameter(15,l.getPromoDiscountAmount()).setParameter(16,l.getHeaderDiscountAmount()).setParameter(17,l.getDiscountAmount()).setParameter(18,l.getFinalAmount()).setParameter(19,l.getAmountExclTax()).setParameter(20,l.getTaxAmount()).setParameter(21,l.isGift()).setParameter(22,l.getBomParentProductId()).setParameter(23,l.getBomVersion()).setParameter(24,l.getBomGroupNo()).setParameter(25,l.getComponentQty()).setParameter(26,l.getLineLevel() == null ? "NORMAL" : l.getLineLevel()).setParameter(27,l.isGroupHeader()).setParameter(28,l.getBomParentLineId()).setParameter(29,json(Map.of("excl", l.getUnitPriceExclTax(), "incl", l.getStandardPriceInclTax())))
            .setParameter(30, nz(l.getBasePriceInclTax())).setParameter(31, l.getPriceSource()).setParameter(32, nz(l.getProductDiscountRate())).setParameter(33, nz(l.getProductDiscountAmount()))
            .setParameter(34, l.getPromoType()).setParameter(35, l.getPromotionId()).setParameter(36, l.getPromoHitId())
            .setParameter(37, nz(l.getUnitPriceInclTax()))
            .setParameter(38, l.getBatchNo()==null||l.getBatchNo().isBlank()?null:l.getBatchNo())
            .setParameter(39, l.getSerialNo()==null||l.getSerialNo().isBlank()?null:l.getSerialNo())
            .setParameter(40, l.isLineZero() || l.isGift())
            .setParameter(41, l.getConsignmentStockId());
            Long lineId = ((Number) q.getSingleResult()).longValue();
            if (l.isGroupHeader() && l.getBomGroupNo() != null) parentLineIds.put(l.getBomGroupNo(), lineId);
            if (l.getLineDiscountDirection() != null) {
                em.createNativeQuery("UPDATE order_lines SET line_discount_direction=?1 WHERE id=?2")
                        .setParameter(1, l.getLineDiscountDirection()).setParameter(2, lineId).executeUpdate();
            }
        }
        for (V4Line l : lines) {
            if (!"CHILD".equals(l.getLineLevel()) || l.getBomGroupNo() == null) continue;
            Long parentId = parentLineIds.get(l.getBomGroupNo());
            if (parentId != null) em.createNativeQuery("UPDATE order_lines SET bom_parent_line_id=?1 WHERE order_id=?2 AND bom_group_no=?3 AND product_id=?4 AND (bom_parent_line_id IS NULL OR bom_parent_line_id <> ?1)").setParameter(1,parentId).setParameter(2,orderId).setParameter(3,l.getBomGroupNo()).setParameter(4,l.getProductId()).executeUpdate();
        }
    }

    private void setAmountParams(jakarta.persistence.Query q, int base, List<V4Line> lines) { q.setParameter(base++, sum(lines,"standard")).setParameter(base++, sum(lines,"discount")).setParameter(base++, sum(lines,"final")).setParameter(base++, sum(lines,"tax")).setParameter(base, sum(lines,"excl")); }
    private BigDecimal sum(List<V4Line> lines, String field) { return V4Money.money(lines.stream().map(l -> switch(field) { case "standard" -> l.getStandardAmount(); case "discount" -> l.getDiscountAmount(); case "final" -> l.getFinalAmount(); case "tax" -> l.getTaxAmount(); default -> l.getAmountExclTax(); }).reduce(BigDecimal.ZERO, BigDecimal::add)); }
    private String snapshot(Long dealerId) { return json(Map.of("dealerId", dealerId)); }

    /** 落库送货地址 id 与地址快照；地址快照来自前端 extra.shipAddress 或 body.shipAddress。 */
    @SuppressWarnings("unchecked")
    private void syncShipAddress(Long orderId, Map<String, Object> body) {
        Long addrId = toLong(body.get("shipAddressId"));
        if (addrId == null && body.get("extra") instanceof Map<?, ?> ex) addrId = toLong(ex.get("shipAddressId"));
        if (addrId == null) return;
        Object snap = body.get("shipAddress");
        if (snap == null && body.get("extra") instanceof Map<?, ?> ex) snap = ex.get("shipAddress");
        var q = em.createNativeQuery("UPDATE orders SET ship_address_id=?1, ship_snapshot=COALESCE(CAST(?2 AS jsonb), ship_snapshot) WHERE id=?3");
        q.setParameter(1, addrId).setParameter(2, snap == null ? null : json(snap)).setParameter(3, orderId).executeUpdate();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> calcPreview(Map<String, Object> body) {
        return previewSalesOrder(body);
    }

    @SuppressWarnings("unchecked")
    private Object snapshotValue(Object snapshot, String key) {
        if (snapshot instanceof Map<?, ?> m) return ((Map<String,Object>) m).get(key);
        if (snapshot == null) return null;
        try { return mapper.readTree(String.valueOf(snapshot)).has(key) ? mapper.readTree(String.valueOf(snapshot)).get(key).asText() : null; }
        catch (Exception e) { return null; }
    }
    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private Map<String, Object> pricingSnapshot(V4CalcResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pricingMode", r.getPricingMode());
        m.put("fixedPrice", r.getFixedPrice());
        m.put("headerDiscountDirection", r.getHeaderDiscountDirection());
        m.put("headerDiscountType", r.getHeaderDiscountType());
        m.put("headerDiscountValue", r.getHeaderDiscountValue());
        m.put("originalAmount", r.getOriginalAmount());
        m.put("productDiscountTotal", r.getProductDiscountTotal());
        m.put("promoDiscountTotal", r.getPromoDiscountTotal());
        m.put("lineDiscountTotal", r.getLineDiscountTotal());
        m.put("dealerDiscountTotal", r.getDealerDiscountTotal());
        m.put("headerDiscountTotal", r.getHeaderDiscountTotal());
        m.put("finalAmount", r.getFinalAmount());
        m.put("voucherAmount", r.getVoucherAmount());
        m.put("payableAmount", r.getPayableAmount());
        return m;
    }

    /** 落库促销命中记录（order_promotion_hits），供详情/打印与审计。 */
    private void recordPromotionHits(Long orderId, List<V4Line> lines) {
        em.createNativeQuery("DELETE FROM order_promotion_hits WHERE order_id=?1").setParameter(1, orderId).executeUpdate();
        Map<Long, BigDecimal> byPromo = new LinkedHashMap<>();
        Map<Long, String> promoType = new HashMap<>();
        for (V4Line l : lines) {
            if (l.getPromotionId() == null) continue;
            BigDecimal amt = nz(l.getPromoDiscountAmount());
            byPromo.merge(l.getPromotionId(), amt, BigDecimal::add);
            promoType.putIfAbsent(l.getPromotionId(), l.getPromoType());
        }
        byPromo.forEach((promoId, discount) ->
            em.createNativeQuery("INSERT INTO order_promotion_hits (order_id,promotion_id,rule_type,discount,detail,created_at) VALUES (?1,?2,?3,?4,CAST(?5 AS jsonb),now())")
                .setParameter(1, orderId).setParameter(2, promoId).setParameter(3, promoType.get(promoId))
                .setParameter(4, discount).setParameter(5, json(Map.of("discount", discount))).executeUpdate());
    }
    private String json(Object o) { try { return mapper.writeValueAsString(o); } catch(JsonProcessingException e) { return "{}"; } }
    private void ensureStatus(Long id, UUID tid, String status, String msg) { if(!status.equals(status(id,tid))) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, msg); }
    private String status(Long id, UUID tid) { var r=em.createNativeQuery("SELECT status FROM orders WHERE id=?1 AND tenant_id=?2").setParameter(1,id).setParameter(2,tid).getResultList(); return r.isEmpty()?null:String.valueOf(r.get(0)); }
    private Tuple order(Long id, UUID tid) { return (Tuple) em.createNativeQuery("SELECT * FROM orders WHERE id=?1 AND tenant_id=?2", Tuple.class).setParameter(1,id).setParameter(2,tid).getSingleResult(); }
    @SuppressWarnings("unchecked") private List<Tuple> linesOf(Long id) { return em.createNativeQuery("SELECT * FROM order_lines WHERE order_id=?1 ORDER BY seq,id", Tuple.class).setParameter(1,id).getResultList(); }
    private BigDecimal shippedQty(Long id, UUID tid) { return new BigDecimal(String.valueOf(em.createNativeQuery("SELECT COALESCE(SUM(COALESCE(shipped_qty,qty,0)),0) FROM sales_out_lines sol JOIN sales_outs so ON so.id=sol.sales_out_id WHERE so.source_order_id=?1 AND so.tenant_id=?2 AND COALESCE(so.is_red,false)=false AND so.deleted_at IS NULL").setParameter(1,id).setParameter(2,tid).getSingleResult())); }
    private Long longRequired(Object o, String name) { Long v=toLong(o); if(v==null) throw new BusinessException(ErrorCode.PARAM_MISSING, name + "不能为空"); return v; }
    private Long toLong(Object o){ if(o==null) return null; if(o instanceof Number n) return n.longValue(); try{return Long.parseLong(String.valueOf(o));}catch(Exception e){return null;} }
    private BigDecimal bd(Object o){ return o==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(o)); }
    private String str(Object o,String def){ return o==null?def:String.valueOf(o); }
}
