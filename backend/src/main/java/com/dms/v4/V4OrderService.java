package com.dms.v4;

import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
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
    private final V4Calculator calculator;
    @Lazy
    private final ApprovalService approvalService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public Map<String, Object> previewSalesOrder(Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long dealerId = longRequired(body.get("dealerId"), "dealer");
        boolean applyPromotions = Boolean.TRUE.equals(body.get("applyPromotions"));
        V4CalcResult result = calculate(tid, dealerId, body, applyPromotions);
        List<V4Line> lines = result.getLines();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lines", lines);
        data.put("promotionMessages", result.getPromotionMessages());
        data.put("amountInclTax", sum(lines, "standard"));
        data.put("discountAmount", sum(lines, "discount"));
        data.put("finalAmount", sum(lines, "final"));
        data.put("taxAmount", sum(lines, "tax"));
        data.put("amountExclTax", sum(lines, "excl"));
        return data;
    }

    @Transactional
    public Map<String, Object> createSalesOrder(Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long dealerId = longRequired(body.get("dealerId"), "dealer");
        List<V4Line> lines = calculate(tid, dealerId, body, true).getLines();
        String code = docNoGenerator.next("SO");
        var q = em.createNativeQuery("INSERT INTO orders (tenant_id,code,order_type,dealer_id,ship_snapshot,status,amount_incl_tax,discount_amount,final_amount,tax_amount,amount_excl_tax,header_discount_type,header_discount_value,expected_date,remark,extra,is_red,erp_status,created_at,updated_at,created_by) VALUES (?1,?2,?3,?4,CAST(?5 AS jsonb),'DRAFT',?6,?7,?8,?9,?10,?11,?12,CAST(?13 AS date),?14,CAST(?15 AS jsonb),false,'NOT_REQUIRED',now(),now(),?16) RETURNING id");
        q.setParameter(1, tid).setParameter(2, code).setParameter(3, str(body.get("orderType"), "NORMAL")).setParameter(4, dealerId).setParameter(5, snapshot(dealerId));
        setAmountParams(q, 6, lines);
        q.setParameter(11, body.get("headerDiscountType")).setParameter(12, bd(body.get("headerDiscountValue"))).setParameter(13, body.get("expectedDate")).setParameter(14, body.get("remark")).setParameter(15, json(body.get("extra"))).setParameter(16, TenantContext.getUserId());
        Long id = ((Number) q.getSingleResult()).longValue();
        insertLines(id, lines);
        return Map.of("id", id, "code", code);
    }

    @Transactional
    public Map<String, Object> updateSalesOrder(Long id, Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (!List.of("DRAFT", "REJECTED").contains(status(id, tid))) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有草稿或驳回状态的订单才能编辑或提交");
        Long dealerId = longRequired(body.get("dealerId"), "dealer");
        List<V4Line> lines = calculate(tid, dealerId, body, true).getLines();
        var q = em.createNativeQuery("UPDATE orders SET order_type=?1,dealer_id=?2,ship_snapshot=CAST(?3 AS jsonb),amount_incl_tax=?4,discount_amount=?5,final_amount=?6,tax_amount=?7,amount_excl_tax=?8,header_discount_type=?9,header_discount_value=?10,expected_date=CAST(?11 AS date),remark=?12,extra=CAST(?13 AS jsonb),updated_at=now() WHERE id=?14 AND tenant_id=?15");
        q.setParameter(1, str(body.get("orderType"), "NORMAL")).setParameter(2, dealerId).setParameter(3, snapshot(dealerId));
        setAmountParams(q, 4, lines);
        q.setParameter(9, body.get("headerDiscountType")).setParameter(10, bd(body.get("headerDiscountValue"))).setParameter(11, body.get("expectedDate")).setParameter(12, body.get("remark")).setParameter(13, json(body.get("extra"))).setParameter(14, id).setParameter(15, tid).executeUpdate();
        em.createNativeQuery("DELETE FROM order_lines WHERE order_id=?1").setParameter(1, id).executeUpdate();
        insertLines(id, lines);
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
        List<Map<String,Object>> rows = new ArrayList<>();
        Map<String, List<Map<String,Object>>> childDiscounts = new HashMap<>();
        for (Tuple t : linesOf(id)) {
            Map<String,Object> m = new HashMap<>();
            m.put("productId", t.get("product_id")); m.put("qty", t.get("qty")); m.put("lineDiscountType", t.get("line_discount_type")); m.put("lineDiscountValue", t.get("line_discount_value")); m.put("isGift", t.get("is_gift")); m.put("bomParentProductId", t.get("bom_parent_product_id")); m.put("bomVersion", t.get("bom_version")); m.put("bomGroupNo", t.get("bom_group_no")); m.put("componentQty", t.get("component_qty"));
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
        List<V4Line> lines = calculate(tid, dealerId, body, true).getLines();
        em.createNativeQuery("DELETE FROM order_lines WHERE order_id=?1").setParameter(1, id).executeUpdate();
        insertLines(id, lines);
        em.createNativeQuery("UPDATE orders SET status='PENDING_APPROVAL',submitted_at=now(),amount_incl_tax=?1,discount_amount=?2,final_amount=?3,tax_amount=?4,amount_excl_tax=?5,erp_status='PENDING_PUSH',updated_at=now() WHERE id=?6 AND tenant_id=?7").setParameter(1,sum(lines,"standard")).setParameter(2,sum(lines,"discount")).setParameter(3,sum(lines,"final")).setParameter(4,sum(lines,"tax")).setParameter(5,sum(lines,"excl")).setParameter(6,id).setParameter(7,tid).executeUpdate();
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
        V4CalcResult result = calculator.expand(tid, dealerId, rows, applyPromotions, str(body.get("headerDiscountType"), null), bd(body.get("headerDiscountValue")));
        if (sum(result.getLines(), "final").signum() < 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "订单最终金额不能小于0");
        return result;
    }

    private void insertLines(Long orderId, List<V4Line> lines) {
        int seq = 1;
        java.util.Map<String, Long> parentLineIds = new java.util.HashMap<>();
        for (V4Line l : lines) {
            jakarta.persistence.Query q = em.createNativeQuery("INSERT INTO order_lines (order_id,seq,product_id,product_code,product_name,product_spec,qty,unit_price,tax_rate,sub_total,standard_price_incl_tax,line_discount_type,line_discount_value,line_discount_amount,promo_discount_amount,header_discount_amount,discount_amount,final_amount,amount_excl_tax,tax_amount,is_gift,bom_parent_product_id,bom_version,bom_group_no,component_qty,line_level,is_group_header,bom_parent_line_id,price_snapshot,created_at,updated_at) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22,?23,?24,?25,?26,?27,?28,CAST(?29 AS jsonb),now(),now()) RETURNING id");
            q.setParameter(1,orderId).setParameter(2,seq++).setParameter(3,l.getProductId()).setParameter(4,l.getProductCode()).setParameter(5,l.getProductName()).setParameter(6,l.getProductSpec()).setParameter(7,l.getQty()).setParameter(8,l.getStandardPriceInclTax()).setParameter(9,l.getTaxRate()).setParameter(10,l.getStandardAmount()).setParameter(11,l.getStandardPriceInclTax()).setParameter(12,l.getLineDiscountType()).setParameter(13,l.getLineDiscountValue()==null?BigDecimal.ZERO:l.getLineDiscountValue()).setParameter(14,l.getLineDiscountAmount()).setParameter(15,l.getPromoDiscountAmount()).setParameter(16,l.getHeaderDiscountAmount()).setParameter(17,l.getDiscountAmount()).setParameter(18,l.getFinalAmount()).setParameter(19,l.getAmountExclTax()).setParameter(20,l.getTaxAmount()).setParameter(21,l.isGift()).setParameter(22,l.getBomParentProductId()).setParameter(23,l.getBomVersion()).setParameter(24,l.getBomGroupNo()).setParameter(25,l.getComponentQty()).setParameter(26,l.getLineLevel() == null ? "NORMAL" : l.getLineLevel()).setParameter(27,l.isGroupHeader()).setParameter(28,l.getBomParentLineId()).setParameter(29,json(Map.of("excl", l.getUnitPriceExclTax(), "incl", l.getStandardPriceInclTax())));
            Long lineId = ((Number) q.getSingleResult()).longValue();
            if (l.isGroupHeader() && l.getBomGroupNo() != null) parentLineIds.put(l.getBomGroupNo(), lineId);
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
