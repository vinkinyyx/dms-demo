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
        V4CalcResult result = calculate(tid, dealerId, body, true);
        List<V4Line> lines = result.getLines();
        String code = docNoGenerator.next("SO");
        var q = em.createNativeQuery("INSERT INTO orders (tenant_id,code,order_type,dealer_id,ship_snapshot,status,amount_incl_tax,discount_amount,final_amount,tax_amount,amount_excl_tax,header_discount_type,header_discount_value,expected_date,remark,extra,is_red,erp_status,pricing_mode,voucher_id,voucher_amount,promo_messages,pricing_snapshot,created_at,updated_at,created_by) VALUES (?1,?2,?3,?4,CAST(?5 AS jsonb),'DRAFT',?6,?7,?8,?9,?10,?11,?12,CAST(?13 AS date),?14,CAST(?15 AS jsonb),false,'NOT_REQUIRED',?17,?18,?19,CAST(?20 AS jsonb),CAST(?21 AS jsonb),now(),now(),?16) RETURNING id");
        q.setParameter(1, tid).setParameter(2, code).setParameter(3, str(body.get("orderType"), "NORMAL")).setParameter(4, dealerId).setParameter(5, snapshot(dealerId));
        setAmountParams(q, 6, lines);
        q.setParameter(11, body.get("headerDiscountType")).setParameter(12, bd(body.get("headerDiscountValue"))).setParameter(13, body.get("expectedDate")).setParameter(14, body.get("remark")).setParameter(15, json(body.get("extra"))).setParameter(16, TenantContext.getUserId());
        q.setParameter(17, result.getPricingMode()).setParameter(18, result.getVoucherId()).setParameter(19, result.getVoucherAmount());
        q.setParameter(20, json(result.getPromotionMessages())).setParameter(21, json(pricingSnapshot(result)));
        Long id = ((Number) q.getSingleResult()).longValue();
        insertLines(id, lines);
        syncShipAddress(id, body);
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
            m.put("productId", t.get("product_id")); m.put("qty", t.get("qty")); m.put("lineDiscountType", t.get("line_discount_type")); m.put("lineDiscountValue", t.get("line_discount_value")); m.put("lineDiscountDirection", t.get("line_discount_direction")); m.put("isGift", t.get("is_gift")); m.put("bomParentProductId", t.get("bom_parent_product_id")); m.put("bomVersion", t.get("bom_version")); m.put("bomGroupNo", t.get("bom_group_no")); m.put("componentQty", t.get("component_qty")); m.put("lineZero", t.get("line_zero"));
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
        ApprovalInstance instance;
        try {
            StartApprovalRequest request = new StartApprovalRequest();
            request.setBusinessType("SALES_ORDER");
            request.setBusinessId(id);
            request.setBusinessCode(String.valueOf(o.get("code")));
            request.setTitle("销售订单审批：" + request.getBusinessCode());
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
        @SuppressWarnings("unchecked") List<Map<String,Object>> rows = (List<Map<String,Object>>) body.getOrDefault("lines", List.of());
        Map<String,Object> params = new LinkedHashMap<>(body);
        params.put("dealerId", dealerId);
        return priceEngine.calculate(tid, dealerId, rows, applyPromotions, params);
    }

    private void insertLines(Long orderId, List<V4Line> lines) {
        int seq = 1;
        java.util.Map<String, Long> parentLineIds = new java.util.HashMap<>();
        for (V4Line l : lines) {
            jakarta.persistence.Query q = em.createNativeQuery("INSERT INTO order_lines (order_id,seq,product_id,product_code,product_name,product_spec,qty,unit_price,tax_rate,sub_total,standard_price_incl_tax,line_discount_type,line_discount_value,line_discount_amount,promo_discount_amount,header_discount_amount,discount_amount,final_amount,amount_excl_tax,tax_amount,is_gift,bom_parent_product_id,bom_version,bom_group_no,component_qty,line_level,is_group_header,bom_parent_line_id,price_snapshot,base_price_incl_tax,price_source,product_discount_rate,product_discount_amount,promo_type,promotion_id,promo_hit_id,unit_price_incl_tax,line_zero,created_at,updated_at) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22,?23,?24,?25,?26,?27,?28,CAST(?29 AS jsonb),?30,?31,?32,?33,?34,?35,?36,?37,?38,now(),now()) RETURNING id");
            q.setParameter(1,orderId).setParameter(2,seq++).setParameter(3,l.getProductId()).setParameter(4,l.getProductCode()).setParameter(5,l.getProductName()).setParameter(6,l.getProductSpec()).setParameter(7,l.getQty()).setParameter(8,l.getStandardPriceInclTax()).setParameter(9,l.getTaxRate()).setParameter(10,l.getStandardAmount()).setParameter(11,l.getStandardPriceInclTax()).setParameter(12,l.getLineDiscountType()).setParameter(13,l.getLineDiscountValue()==null?BigDecimal.ZERO:l.getLineDiscountValue()).setParameter(14,l.getLineDiscountAmount()).setParameter(15,l.getPromoDiscountAmount()).setParameter(16,l.getHeaderDiscountAmount()).setParameter(17,l.getDiscountAmount()).setParameter(18,l.getFinalAmount()).setParameter(19,l.getAmountExclTax()).setParameter(20,l.getTaxAmount()).setParameter(21,l.isGift()).setParameter(22,l.getBomParentProductId()).setParameter(23,l.getBomVersion()).setParameter(24,l.getBomGroupNo()).setParameter(25,l.getComponentQty()).setParameter(26,l.getLineLevel() == null ? "NORMAL" : l.getLineLevel()).setParameter(27,l.isGroupHeader()).setParameter(28,l.getBomParentLineId()).setParameter(29,json(Map.of("excl", l.getUnitPriceExclTax(), "incl", l.getStandardPriceInclTax())))
            .setParameter(30, nz(l.getBasePriceInclTax())).setParameter(31, l.getPriceSource()).setParameter(32, nz(l.getProductDiscountRate())).setParameter(33, nz(l.getProductDiscountAmount()))
            .setParameter(34, l.getPromoType()).setParameter(35, l.getPromotionId()).setParameter(36, l.getPromoHitId())
            .setParameter(37, nz(l.getUnitPriceInclTax())).setParameter(38, l.isLineZero() || l.isGift());
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
