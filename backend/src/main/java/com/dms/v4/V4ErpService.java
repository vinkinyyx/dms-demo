package com.dms.v4;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DocNoGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class V4ErpService {
    private final EntityManager em;
    private final DocNoGenerator docNoGenerator;
    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional
    public Map<String, Object> simulateShip(Long orderId) {
        UUID tid = com.dms.common.util.TenantContext.getTenantId();
        Tuple o = order(orderId, tid, false);
        String orderStatus = str(o.get("status"), "");
        if (!List.of("APPROVED", "PARTIAL_OUTBOUND").contains(orderStatus) && !"PUSH_SUCCESS".equals(str(o.get("erp_status"), ""))) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有已审批待发货或部分发货的订单才能接收ERP出库回调");
        }
        Long wh = firstWarehouse(tid);
        List<Map<String,Object>> lines = new ArrayList<>();
        for (Tuple t : orderLines(orderId)) {
            if (Boolean.TRUE.equals(t.get("is_group_header")) || "PARENT".equals(String.valueOf(t.get("line_level")))) continue;
            BigDecimal qty = bd(t.get("qty")).subtract(bd(t.get("closed_qty")));
            if (qty.signum() <= 0) continue;
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("orderLineId", t.get("id"));
            m.put("productId", t.get("product_id"));
            m.put("qty", qty);
            m.put("batchNo", "SIM-" + orderId + "-" + t.get("id"));
            if (Boolean.TRUE.equals(serialProduct(tid, toLong(t.get("product_id"))))) m.put("serialNo", "SIMSN-" + UUID.randomUUID());
            lines.add(m);
        }
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("sourceOrderId", orderId);
        payload.put("dealerId", o.get("dealer_id"));
        payload.put("direction", "FORWARD");
        payload.put("warehouseId", wh);
        payload.put("salesDate", java.time.LocalDate.now());
        payload.put("warehouseName", "模拟仓");
        payload.put("lines", lines);
        payload.put("idempotencyKey", "SIM-" + UUID.randomUUID());
        return receiveOutbound(payload);
    }

    @Transactional
    public Map<String, Object> receiveOutbound(Map<String, Object> body) {
        UUID tid = com.dms.common.util.TenantContext.getTenantId();
        boolean red = "RED".equalsIgnoreCase(str(body.get("direction"), "FORWARD"));
        Long orderId = toLong(body.get("sourceOrderId"));
        String idem = str(body.get("idempotencyKey"), null);
        if (idem != null && !idem.isBlank()) {
            var existed = em.createNativeQuery("SELECT sales_out_id FROM erp_outbound_callbacks WHERE tenant_id=?1 AND idempotency_key=?2").setParameter(1, tid).setParameter(2, idem).getResultList();
            if (!existed.isEmpty()) return Map.of("id", toLong(existed.get(0)), "idempotent", true);
        }
        Tuple order = order(orderId, tid, red);
        if (red) {
            String returnStatus = str(order.get("status"), "");
            if (!List.of("APPROVED", "PARTIAL_RED_OUTBOUND").contains(returnStatus) && !"PUSH_SUCCESS".equals(str(order.get("erp_status"), ""))) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有已审批待退货或部分红字出库的销退单才能接收ERP回调");
            }
        }
        String code = docNoGenerator.next(red ? "GIR" : "GI");
        Long dealerId = toLong(body.get("dealerId"));
        if (dealerId == null) dealerId = toLong(order.get("dealer_id"));
        var ins = em.createNativeQuery("INSERT INTO sales_outs (tenant_id,code,dealer_id,business_type,is_red,source_order_id,warehouse_id,sales_date,status,amount_incl_tax,erp_outbound_no,idempotency_key,callback_payload,shipped_at,completed_at,created_at,updated_at) VALUES (?1,?2,?3,'ERP',?4,?5,?6,CAST(?10 AS date),'COMPLETED',0,?7,?8,CAST(?9 AS jsonb),now(),now(),now(),now()) RETURNING id");
        Long whId = toLong(body.get("warehouseId"));
        if (whId == null) whId = toLong(order.get("warehouse_id"));
        if (whId == null) whId = firstWarehouse(tid);
        if (whId == null) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "出库单未指定仓库，且无默认仓库");
        Long soId = toLong(ins.setParameter(1,tid).setParameter(2,code).setParameter(3,dealerId).setParameter(4,red).setParameter(5,orderId).setParameter(6,whId).setParameter(7,body.get("erpOutboundNo")).setParameter(8,idem).setParameter(9,json(body)).setParameter(10, body.getOrDefault("salesDate", java.time.LocalDate.now())).getSingleResult());
        @SuppressWarnings("unchecked") List<Map<String,Object>> lines = (List<Map<String,Object>>) body.getOrDefault("lines", List.of());
        BigDecimal total = BigDecimal.ZERO;
        int lineSeq = 0;
        List<Map<String,Object>> shippedRows = new ArrayList<>();
        for (Map<String,Object> row : lines) {
            Long orderLineId = toLong(row.get("orderLineId"));
            Tuple ol = orderLine(orderLineId);
            if (ol == null || !orderId.equals(toLong(ol.get("order_id")))) throw new BusinessException(ErrorCode.PARAM_INVALID, "出库行不属于当前订单");
            if ("PARENT".equals(String.valueOf(ol.get("line_level")))) continue;
            BigDecimal qty = bd(row.get("qty"));
            if (qty.signum() <= 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "出库数量必须大于0");
            BigDecimal already = shippedQtyOfLine(orderLineId, red);
            BigDecimal allowed = bd(ol.get("qty")).subtract(bd(ol.get("closed_qty"))).subtract(already);
            if (qty.compareTo(allowed) > 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "出库数量超过可发货数量，订单行ID=" + orderLineId);
            BigDecimal price = bd(ol.get("final_amount")).divide(bd(ol.get("qty")), 4, RoundingMode.HALF_UP);
            BigDecimal taxRate = ol == null ? bd(row.get("taxRate")) : bd(ol.get("tax_rate"));
            BigDecimal amount = V4Money.money(price.multiply(qty));
            var tax = V4Money.splitTax(amount, taxRate);
            int currentSeq = ++lineSeq;
            var lineIns = em.createNativeQuery("INSERT INTO sales_out_lines (sales_out_id,warehouse_id,source_order_line_id,product_id,batch_no,serial_no,expected_qty,shipped_qty,qty,unit_price,tax_rate,subtotal,final_amount,amount_excl_tax,tax_amount,bom_parent_product_id,bom_version,bom_group_no,component_qty,returned_qty,return_locked_qty,seq,created_at) VALUES (?1,?2,?3,?4,?5,?6,?7,?7,?7,?8,?9,?10,?10,?11,?12,?13,?14,?15,?16,0,0,?17,now()) RETURNING id");
            Long outLineId = toLong(lineIns
                    .setParameter(1,soId).setParameter(2, toLong(first(row.get("warehouseId"), whId))).setParameter(3,orderLineId).setParameter(4, first(row.get("productId"), ol==null?null:ol.get("product_id")))
                    .setParameter(5,row.get("batchNo")).setParameter(6,row.get("serialNo")).setParameter(7,qty).setParameter(8,price).setParameter(9,taxRate).setParameter(10,amount)
                    .setParameter(11,tax.get("excl")).setParameter(12,tax.get("tax")).setParameter(13, ol==null?null:ol.get("bom_parent_product_id")).setParameter(14, ol==null?null:ol.get("bom_version")).setParameter(15, ol==null?null:ol.get("bom_group_no")).setParameter(16, ol==null?BigDecimal.ONE:bd(ol.get("component_qty")))
                    .setParameter(17,currentSeq).getSingleResult());
            Map<String,Object> shipped = new LinkedHashMap<>();
            shipped.put("outLineId", outLineId);
            shipped.put("seq", currentSeq);
            shipped.put("productId", first(row.get("productId"), ol==null?null:ol.get("product_id")));
            shipped.put("warehouseId", toLong(first(row.get("warehouseId"), whId)));
            shipped.put("qty", qty);
            shipped.put("batchNo", row.get("batchNo"));
            shipped.put("serialNo", row.get("serialNo"));
            shipped.put("unitPrice", price);
            shippedRows.add(shipped);
            total = total.add(amount);
            if (red) applyRedReceipt(orderId, row, qty);
        }
        em.createNativeQuery("UPDATE sales_outs SET amount_incl_tax=?1 WHERE id=?2").setParameter(1,total).setParameter(2,soId).executeUpdate();
        if (!shippedRows.isEmpty()) {
            String batchCode = code + "-1";
            var batchIns = em.createNativeQuery("INSERT INTO sales_out_batches (tenant_id,sales_out_id,code,seq,status,created_at,updated_at,confirmed_at,created_by) VALUES (?1,?2,?3,1,'CONFIRMED',now(),now(),now(),?4) RETURNING id");
            Long batchId = toLong(batchIns.setParameter(1,tid).setParameter(2,soId).setParameter(3,batchCode).setParameter(4, com.dms.common.util.TenantContext.getUserId()).getSingleResult());
            for (Map<String,Object> srow : shippedRows) {
                em.createNativeQuery("INSERT INTO sales_out_batch_lines (batch_id,expected_line_id,expected_line_seq,ship_line_no,product_id,warehouse_id,qty,batch_no,serial_no,unit_price,created_at) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,now())")
                        .setParameter(1,batchId).setParameter(2,srow.get("outLineId")).setParameter(3,srow.get("seq")).setParameter(4,srow.get("seq"))
                        .setParameter(5,srow.get("productId")).setParameter(6,srow.get("warehouseId")).setParameter(7,srow.get("qty"))
                        .setParameter(8,srow.get("batchNo")).setParameter(9,srow.get("serialNo")).setParameter(10,srow.get("unitPrice")).executeUpdate();
            }
        }
        em.createNativeQuery("INSERT INTO erp_outbound_callbacks (tenant_id,idempotency_key,direction,source_order_id,sales_out_id,erp_outbound_no,process_status,raw_payload) VALUES (?1,?2,?3,?4,?5,?6,'PROCESSED',CAST(?7 AS jsonb))").setParameter(1,tid).setParameter(2,idem).setParameter(3,red?"RED":"FORWARD").setParameter(4,orderId).setParameter(5,soId).setParameter(6,body.get("erpOutboundNo")).setParameter(7,json(body)).executeUpdate();
        refreshOrderStatus(orderId, tid, red);
        return Map.of("id", soId, "code", code, "amount", total, "direction", red ? "RED" : "FORWARD");
    }

    private void refreshOrderStatus(Long orderId, UUID tid, boolean red) {
        if (orderId == null) return;
        @SuppressWarnings("unchecked") List<Tuple> lines = em.createNativeQuery("SELECT ol.id, ol.qty, ol.closed_qty, COALESCE((SELECT SUM(COALESCE(sol.shipped_qty,sol.qty,0)) FROM sales_out_lines sol JOIN sales_outs so ON so.id=sol.sales_out_id WHERE sol.source_order_line_id=ol.id AND so.is_red=?2 AND so.deleted_at IS NULL),0) shipped FROM order_lines ol WHERE ol.order_id=?1 AND COALESCE(ol.line_level,'NORMAL') <> 'PARENT'", Tuple.class).setParameter(1,orderId).setParameter(2,red).getResultList();
        boolean any = false, all = true;
        for (Tuple t : lines) {
            BigDecimal need = bd(t.get("qty")).subtract(bd(t.get("closed_qty")));
            BigDecimal shipped = bd(t.get("shipped"));
            if (shipped.signum() > 0) any = true;
            if (shipped.compareTo(need) < 0) all = false;
        }
        String status;
        if (!lines.isEmpty() && all) status = "COMPLETED";
        else if (any) status = red ? "PARTIAL_RED_OUTBOUND" : "PARTIAL_OUTBOUND";
        else status = "APPROVED";
        em.createNativeQuery("UPDATE orders SET status=?1, completed_at=CASE WHEN ?1='COMPLETED' THEN now() ELSE completed_at END, updated_at=now() WHERE id=?2 AND tenant_id=?3").setParameter(1,status).setParameter(2,orderId).setParameter(3,tid).executeUpdate();
    }

    private BigDecimal shippedQtyOfLine(Long orderLineId, boolean red) {
        return bd(em.createNativeQuery("SELECT COALESCE(SUM(COALESCE(shipped_qty,qty,0)),0) FROM sales_out_lines sol JOIN sales_outs so ON so.id=sol.sales_out_id WHERE sol.source_order_line_id=?1 AND so.is_red=?2 AND so.deleted_at IS NULL")
                .setParameter(1, orderLineId).setParameter(2, red).getSingleResult());
    }

    private void applyRedReceipt(Long returnOrderId, Map<String,Object> row, BigDecimal qty) {
        Long sourceOutLineId = toLong(row.get("sourceOutLineId"));
        if (sourceOutLineId == null) return;
        em.createNativeQuery("UPDATE sales_out_lines SET returned_qty=returned_qty+?1 WHERE id=?2").setParameter(1, qty).setParameter(2, sourceOutLineId).executeUpdate();
    }

    private Tuple order(Long id, UUID tid, boolean red) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "sourceOrderId不能为空");
        var rs = em.createNativeQuery("SELECT * FROM orders WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=?3 AND deleted_at IS NULL", Tuple.class).setParameter(1,id).setParameter(2,tid).setParameter(3,red).getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        return (Tuple) rs.get(0);
    }
    @SuppressWarnings("unchecked") private List<Tuple> orderLines(Long id) { return em.createNativeQuery("SELECT * FROM order_lines WHERE order_id=?1 AND COALESCE(line_level,'NORMAL') <> 'PARENT' ORDER BY seq,id", Tuple.class).setParameter(1,id).getResultList(); }
    private Tuple orderLine(Long id) { if(id==null) return null; var rs = em.createNativeQuery("SELECT * FROM order_lines WHERE id=?1", Tuple.class).setParameter(1,id).getResultList(); return rs.isEmpty()?null:(Tuple)rs.get(0); }
    private Long firstWarehouse(UUID tid) { var rs = em.createNativeQuery("SELECT id FROM warehouses WHERE tenant_id=?1 ORDER BY id LIMIT 1").setParameter(1,tid).getResultList(); return rs.isEmpty()?null:toLong(rs.get(0)); }
    private Object serialProduct(UUID tid, Long pid) { var rs = em.createNativeQuery("SELECT is_serial_managed FROM products WHERE id=?1 AND tenant_id=?2").setParameter(1,pid).setParameter(2,tid).getResultList(); return rs.isEmpty()?null:rs.get(0); }
    private Object first(Object a, Object b) { return a != null ? a : b; }
    private String json(Object o) { try { return mapper.writeValueAsString(o); } catch(Exception e) { return "{}"; } }
    private Long toLong(Object o){ if(o==null) return null; if(o instanceof Number n) return n.longValue(); try{return Long.parseLong(String.valueOf(o));}catch(Exception e){return null;} }
    private BigDecimal bd(Object o){ return o==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(o)); }
    private String str(Object o,String def){ return o==null?def:String.valueOf(o); }
}
