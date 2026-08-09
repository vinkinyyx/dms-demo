/*
 * 销退订单 Controller (v3.8.1)
 * 基路径 /api/sales-returns，操作 orders 表 is_red=true 的红字销售单。
 * 状态机：DRAFT -> SUBMITTED -> APPROVED -> RECEIVING -> COMPLETED (\->REJECTED/CANCELLED)
 * 审批通过后自动生成销退入库草稿单(RGR)，入库进度回写销退单状态。
 */
package com.dms.order.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.TenantContext;
import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.execution.service.AutoDocGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/sales-returns")
@RequiredArgsConstructor
public class SalesReturnController {

    private final EntityManager em;
    private final AutoDocGenerator autoDocGenerator;
    private final com.dms.common.util.DocNoGenerator docNoGenerator;
    private final ApprovalService approvalService;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) Long warehouseId) {
        UUID tid = TenantContext.getTenantId();
        int offset = (page - 1) * size;
        StringBuilder where = new StringBuilder(" WHERE o.tenant_id = ?1 AND o.deleted_at IS NULL AND COALESCE(o.is_red,false) = true");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (status != null && !status.isBlank()) { where.append(" AND o.status = ?").append(idx++); params.add(status); }
        if (dealerId != null) { where.append(" AND o.dealer_id = ?").append(idx++); params.add(dealerId); }
        if (warehouseId != null) { where.append(" AND o.warehouse_id = ?").append(idx++); params.add(warehouseId); }

        var qCnt = em.createNativeQuery("SELECT COUNT(*) FROM orders o " + where);
        for (int i = 0; i < params.size(); i++) qCnt.setParameter(i + 1, params.get(i));
        long total = ((Number) qCnt.getSingleResult()).longValue();

        String limitParam = "?" + idx++;
        String offsetParam = "?" + idx++;
        var q = em.createNativeQuery(
                "SELECT o.id, o.code, o.order_type, o.dealer_id, o.warehouse_id, o.ref_order_id, o.ref_sales_out_id, o.return_reason, " +
                "COALESCE(NULLIF(CAST(o.ship_snapshot AS jsonb)->>'dealerName',''), d.name) AS dealer_name, " +
                "w.name AS warehouse_name, u.name AS audit_user_name, o.approved_at, " +
                "o.amount_incl_tax, o.final_amount, o.expected_date, o.status, o.created_at " +
                "FROM orders o LEFT JOIN dealers d ON d.id=o.dealer_id " +
                "LEFT JOIN warehouses w ON w.id=o.warehouse_id LEFT JOIN users u ON u.id=o.approved_by " +
                where + " ORDER BY o.created_at DESC LIMIT " + limitParam + " OFFSET " + offsetParam, Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(params.size() + 1, size);
        q.setParameter(params.size() + 2, offset);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toBrief(t));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total); data.put("page", page); data.put("size", size); data.put("list", list);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT o.*, d.name AS dealer_name, w.name AS warehouse_name, " +
                "so.code AS ref_sales_out_code, o0.code AS ref_order_code " +
                "FROM orders o LEFT JOIN dealers d ON d.id=o.dealer_id " +
                "LEFT JOIN warehouses w ON w.id=o.warehouse_id " +
                "LEFT JOIN sales_outs so ON so.id=o.ref_sales_out_id " +
                "LEFT JOIN orders o0 ON o0.id=o.ref_order_id " +
                "WHERE o.id=?1 AND o.tenant_id=?2 AND COALESCE(o.is_red,false)=true", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rs = q.getResultList();
        if (rs.isEmpty()) return ApiResponse.fail(40404, "销退订单不存在");
        Tuple t = rs.get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("code", t.get("code"));
        data.put("orderType", t.get("order_type"));
        data.put("dealerId", t.get("dealer_id"));
        data.put("dealerName", t.get("dealer_name"));
        data.put("warehouseId", t.get("warehouse_id"));
        data.put("warehouseName", t.get("warehouse_name"));
        data.put("refOrderId", t.get("ref_order_id"));
        data.put("refOrderCode", t.get("ref_order_code"));
        data.put("refSalesOutId", t.get("ref_sales_out_id"));
        data.put("refSalesOutCode", t.get("ref_sales_out_code"));
        data.put("returnReason", t.get("return_reason"));
        data.put("remark", t.get("remark"));
        data.put("status", t.get("status"));
        data.put("amountInclTax", t.get("amount_incl_tax"));
        data.put("finalAmount", t.get("final_amount"));
        data.put("expectedDate", t.get("expected_date"));
        try { data.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at"))); } catch (Exception ignored) {}

        var lq = em.createNativeQuery(
                "SELECT ol.id, ol.seq, ol.product_id, ol.qty, ol.unit_price, ol.tax_rate, ol.sub_total, " +
                "p.code AS p_code, p.name_cn AS p_name, p.spec AS p_spec, p.unit AS p_unit, p.is_serial_managed " +
                "FROM order_lines ol LEFT JOIN products p ON p.id=ol.product_id WHERE ol.order_id=?1 ORDER BY ol.seq, ol.id", Tuple.class);
        lq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> lrows = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : lrows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.get("id")); m.put("seq", l.get("seq"));
            m.put("productId", l.get("product_id"));
            m.put("productCode", l.get("p_code")); m.put("productName", l.get("p_name"));
            m.put("productSpec", l.get("p_spec")); m.put("unit", l.get("p_unit"));
            m.put("qty", l.get("qty")); m.put("unitPrice", l.get("unit_price"));
            m.put("taxRate", l.get("tax_rate")); m.put("subtotal", l.get("sub_total"));
            m.put("isSerialManaged", l.get("is_serial_managed"));
            lines.add(m);
        }
        data.put("lines", lines);
        data.put("allowedActions", allowedActions(String.valueOf(data.get("status"))));
        return ApiResponse.ok(data);
    }

    @GetMapping("/shipped-outs")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> shippedOuts(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long dealerId) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "SELECT so.id, so.code, so.dealer_id, so.warehouse_id, so.source_order_id, so.status, " +
                "d.name AS dealer_name, w.name AS warehouse_name, o.code AS order_code " +
                "FROM sales_outs so LEFT JOIN dealers d ON d.id=so.dealer_id " +
                "LEFT JOIN warehouses w ON w.id=so.warehouse_id LEFT JOIN orders o ON o.id=so.source_order_id " +
                "WHERE so.tenant_id=?1 AND so.deleted_at IS NULL AND COALESCE(so.is_red,false)=false " +
                "AND so.status IN ('PARTIAL_SHIPPED','COMPLETED')");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (orderId != null) { sql.append(" AND so.source_order_id = ?").append(idx++); params.add(orderId); }
        if (dealerId != null) { sql.append(" AND so.dealer_id = ?").append(idx++); params.add(dealerId); }
        sql.append(" ORDER BY so.id DESC LIMIT 200");
        var q = em.createNativeQuery(sql.toString(), Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id")); m.put("code", t.get("code"));
            m.put("dealerId", t.get("dealer_id")); m.put("dealerName", t.get("dealer_name"));
            m.put("warehouseId", t.get("warehouse_id")); m.put("warehouseName", t.get("warehouse_name"));
            m.put("orderId", t.get("source_order_id")); m.put("orderCode", t.get("order_code"));
            m.put("status", t.get("status"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/shipped-outs/{salesOutId}/lines")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> shippedOutLines(@PathVariable Long salesOutId) {
        UUID tid = TenantContext.getTenantId();
        var hq = em.createNativeQuery(
                "SELECT id, code, dealer_id, warehouse_id, source_order_id FROM sales_outs WHERE id=?1 AND tenant_id=?2", Tuple.class);
        hq.setParameter(1, salesOutId).setParameter(2, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> hr = hq.getResultList();
        if (hr.isEmpty()) return ApiResponse.fail(40404, "发货单不存在");
        Tuple h = hr.get(0);

        var lq = em.createNativeQuery(
                "SELECT sol.id, sol.seq, sol.product_id, sol.batch_no, sol.serial_no, " +
                "COALESCE(sol.shipped_qty, sol.qty, 0) AS shipped_qty, sol.unit_price, sol.tax_rate, " +
                "p.code AS p_code, p.name_cn AS p_name, p.spec AS p_spec, p.unit AS p_unit, p.is_serial_managed " +
                "FROM sales_out_lines sol LEFT JOIN products p ON p.id=sol.product_id " +
                "WHERE sol.sales_out_id=?1 ORDER BY sol.seq, sol.id", Tuple.class);
        lq.setParameter(1, salesOutId);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : rows) {
            BigDecimal shipped = new BigDecimal(String.valueOf(l.get("shipped_qty")));
            BigDecimal alreadyReturned = BigDecimal.ZERO;
            try {
                Object ar = em.createNativeQuery(
                        "SELECT COALESCE(SUM(ol.qty),0) FROM order_lines ol JOIN orders o ON o.id=ol.order_id " +
                        "WHERE o.ref_sales_out_id=?1 AND o.tenant_id=?2 AND COALESCE(o.is_red,false)=true AND o.status NOT IN ('CANCELLED','REJECTED') " +
                        "AND ol.product_id=?3 AND COALESCE(ol.batch_no,'')=COALESCE(CAST(?4 AS varchar),'') AND COALESCE(ol.serial_no,'')=COALESCE(CAST(?5 AS varchar),'')")
                        .setParameter(1, salesOutId).setParameter(2, tid)
                        .setParameter(3, l.get("product_id"))
                        .setParameter(4, l.get("batch_no"))
                        .setParameter(5, l.get("serial_no"))
                        .getSingleResult();
                alreadyReturned = new BigDecimal(String.valueOf(ar));
            } catch (Exception ignored) {}
            BigDecimal returnable = shipped.subtract(alreadyReturned);
            if (returnable.signum() < 0) returnable = BigDecimal.ZERO;
            if (returnable.signum() == 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", l.get("product_id"));
            m.put("productCode", l.get("p_code")); m.put("productName", l.get("p_name"));
            m.put("productSpec", l.get("p_spec")); m.put("unit", l.get("p_unit"));
            m.put("batchNo", l.get("batch_no")); m.put("serialNo", l.get("serial_no"));
            m.put("isSerialManaged", l.get("is_serial_managed"));
            m.put("shippedQty", shipped);
            m.put("returnableQty", returnable);
            m.put("qty", returnable);
            m.put("unitPrice", l.get("unit_price"));
            m.put("taxRate", l.get("tax_rate"));
            lines.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", h.get("id")); data.put("code", h.get("code"));
        data.put("dealerId", h.get("dealer_id")); data.put("warehouseId", h.get("warehouse_id"));
        data.put("orderId", h.get("source_order_id"));
        data.put("lines", lines);
        return ApiResponse.ok(data);
    }

    @PostMapping
    @OperationLog(businessType = "salesReturn", action = OperationAction.CREATE, remark = "销退订单-创建")
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (body.get("dealerId") == null) return ApiResponse.fail(40001, "经销商不能为空");
        if (body.get("warehouseId") == null) return ApiResponse.fail(40001, "收货仓库不能为空");
        if (body.get("refSalesOutId") == null) return ApiResponse.fail(40001, "必须关联已发货的发货单");
        if (body.get("returnReason") == null || String.valueOf(body.get("returnReason")).isBlank())
            return ApiResponse.fail(40001, "退货原因不能为空");

        Long refSalesOutId = toLong(body.get("refSalesOutId"));
        // 校验发货单存在且已发货
        var soQ = em.createNativeQuery(
                "SELECT id, source_order_id, dealer_id, warehouse_id, status FROM sales_outs WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=false", Tuple.class);
        soQ.setParameter(1, refSalesOutId).setParameter(2, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> soRs = soQ.getResultList();
        if (soRs.isEmpty()) return ApiResponse.fail(40001, "关联的发货单不存在");
        Tuple so = soRs.get(0);
        if (!"PARTIAL_SHIPPED".equals(so.get("status")) && !"COMPLETED".equals(so.get("status")))
            return ApiResponse.fail(40001, "只能关联已发货(部分发货/已完成)的发货单");

        String code = docNoGenerator.next("RS");
        BigDecimal total = calcTotal(body);

        var insert = em.createNativeQuery(
                "INSERT INTO orders (tenant_id, code, order_type, is_red, dealer_id, warehouse_id, ref_order_id, ref_sales_out_id, return_reason, " +
                "ship_snapshot, amount_incl_tax, discount_amount, final_amount, tax_amount, expected_date, status, remark, extra, created_at, updated_at, created_by) " +
                "VALUES (?1, ?2, 'RETURN', true, ?3, ?4, ?5, ?6, ?7, CAST(?8 AS jsonb), ?9, 0, ?9, 0, CAST(?10 AS date), 'DRAFT', ?11, CAST(?12 AS jsonb), now(), now(), ?13) RETURNING id");
        insert.setParameter(1, tid).setParameter(2, code)
              .setParameter(3, toLong(body.get("dealerId")))
              .setParameter(4, toLong(body.get("warehouseId")))
              .setParameter(5, so.get("source_order_id"))
              .setParameter(6, refSalesOutId)
              .setParameter(7, body.get("returnReason"))
              .setParameter(8, "{}")
              .setParameter(9, total)
              .setParameter(10, body.get("expectedDate"))
              .setParameter(11, body.getOrDefault("remark", ""))
              .setParameter(12, "{}")
              .setParameter(13, TenantContext.getUserId());
        Long id = ((Number) insert.getSingleResult()).longValue();

        String validate = validateReturnLines(tid, refSalesOutId, body);
        if (validate != null) return ApiResponse.fail(40001, validate);
        insertLines(id, body);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("code", code);
        return ApiResponse.ok(res);
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "salesReturn", action = OperationAction.UPDATE, remark = "销退订单-更新")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "销退订单不存在");
        if (!"DRAFT".equals(status)) return ApiResponse.fail(40009, "仅草稿可编辑，当前状态: " + status);

        Long refSalesOutId = toLong(body.get("refSalesOutId"));
        String validate = validateReturnLines(tid, refSalesOutId, body);
        if (validate != null) return ApiResponse.fail(40001, validate);

        BigDecimal total = calcTotal(body);
        em.createNativeQuery(
                "UPDATE orders SET dealer_id=?1, warehouse_id=?2, ref_sales_out_id=?3, return_reason=?4, " +
                "amount_incl_tax=?5, final_amount=?5, expected_date=CAST(?6 AS date), remark=?7, updated_at=now() WHERE id=?8 AND tenant_id=?9")
          .setParameter(1, toLong(body.get("dealerId"))).setParameter(2, toLong(body.get("warehouseId")))
          .setParameter(3, refSalesOutId).setParameter(4, body.get("returnReason"))
          .setParameter(5, total).setParameter(6, body.get("expectedDate"))
          .setParameter(7, body.getOrDefault("remark", ""))
          .setParameter(8, id).setParameter(9, tid).executeUpdate();
        em.createNativeQuery("DELETE FROM order_lines WHERE order_id=?1").setParameter(1, id).executeUpdate();
        insertLines(id, body);
        return ApiResponse.ok(Map.of("id", id));
    }

    @PostMapping("/{id}/submit")
    @Transactional
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        int n = em.createNativeQuery("UPDATE orders SET status='PENDING_APPROVAL', submitted_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2 AND status='DRAFT' AND COALESCE(is_red,false)=true")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (n == 0) return ApiResponse.fail(40009, "Only draft sales return can be submitted");
        try {
            StartApprovalRequest request = new StartApprovalRequest();
            request.setBusinessType("SALES_RETURN");
            request.setBusinessId(id);
            Object code = em.createNativeQuery("SELECT code FROM orders WHERE id=?1").setParameter(1, id).getSingleResult();
            request.setBusinessCode(String.valueOf(code));
            request.setTitle("Sales return approval: " + request.getBusinessCode());
            request.setBusinessSnapshot(buildApprovalSnapshot(id));
            ApprovalInstance instance = approvalService.start(request);
            boolean approved = "APPROVED".equals(instance.getStatus().name()) || "AUTO_APPROVED".equals(instance.getStatus().name());
            return ApiResponse.ok(Map.of("id", id, "newStatus", approved ? "APPROVED" : "PENDING_APPROVAL", "approvalInstanceId", instance.getId(), "autoApproved", approved));
        } catch (Exception e) {
            em.createNativeQuery("UPDATE orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2").setParameter(1, id).setParameter(2, tid).executeUpdate();
            throw e;
        }
    }

    private Map<String, Object> buildApprovalSnapshot(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery("SELECT code, order_type, dealer_id, warehouse_id, final_amount, amount_incl_tax, return_reason, expected_date FROM orders WHERE id=?1", Tuple.class).setParameter(1, id).getResultList();
        Map<String, Object> snapshot = new HashMap<>();
        if (rows.isEmpty()) return snapshot;
        Tuple row = rows.get(0);
        snapshot.put("code", row.get("code"));
        snapshot.put("orderType", row.get("order_type"));
        snapshot.put("dealerId", row.get("dealer_id"));
        snapshot.put("warehouseId", row.get("warehouse_id"));
        snapshot.put("finalAmount", row.get("final_amount"));
        snapshot.put("amountInclTax", row.get("amount_incl_tax"));
        snapshot.put("returnReason", row.get("return_reason"));
        snapshot.put("expectedDate", row.get("expected_date"));
        return snapshot;
    }

    @PostMapping("/{id}/approve")
    @OperationLog(businessType = "purchaseReturn", action = OperationAction.APPROVE, remark = "采退订单-审批通过")
    @Transactional
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id) {
        ApprovalInstance instance = approvalService.approveBusiness("SALES_RETURN", id, null);
        em.createNativeQuery("UPDATE orders SET approved_by=?1 WHERE id=?2")
          .setParameter(1, TenantContext.getUserId()).setParameter(2, id).executeUpdate();
        return ApiResponse.ok(Map.of("id", id, "newStatus", "APPROVED".equals(instance.getStatus().name()) || "AUTO_APPROVED".equals(instance.getStatus().name()) ? "APPROVED" : "PENDING_APPROVAL", "approvalInstanceId", instance.getId()));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id) {
        ApprovalInstance instance = approvalService.rejectBusiness("SALES_RETURN", id, null);
        return ApiResponse.ok(Map.of("id", id, "newStatus", instance.getStatus().name(), "approvalInstanceId", instance.getId()));
    }

    @PostMapping("/{id}/cancel")
    @OperationLog(businessType = "salesReturn", action = OperationAction.UPDATE, remark = "销退订单-取消")
    @Transactional
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "销退订单不存在");
        if (!"DRAFT".equals(status) && !"APPROVED".equals(status))
            return ApiResponse.fail(40009, "当前状态不允许取消: " + status);
        if ("APPROVED".equals(status)) {
            Object cnt = em.createNativeQuery(
                    "SELECT COUNT(*) FROM receipts WHERE ref_doc_type='sales_return' AND ref_doc_id=?1 AND tenant_id=?2 AND status NOT IN ('DRAFT','CANCELLED')")
                    .setParameter(1, id).setParameter(2, tid).getSingleResult();
            if (((Number) cnt).longValue() > 0)
                return ApiResponse.fail(40009, "存在已执行的入库单，不能取消销退订单");
            Object rcv = em.createNativeQuery(
                    "SELECT COALESCE(SUM(rl.received_qty),0) FROM receipt_lines rl JOIN receipts r ON r.id=rl.receipt_id " +
                    "WHERE r.ref_doc_type='sales_return' AND r.ref_doc_id=?1").setParameter(1, id).getSingleResult();
            if (new BigDecimal(String.valueOf(rcv)).signum() > 0)
                return ApiResponse.fail(40009, "已存在收货记录，不能取消销退订单");
        }
        em.createNativeQuery("UPDATE orders SET status='CANCELLED', closed_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        em.createNativeQuery(
                "UPDATE receipts SET status='CANCELLED', updated_at=now() WHERE ref_doc_type='sales_return' AND ref_doc_id=?1 AND tenant_id=?2 AND status IN ('DRAFT','APPROVED','PARTIAL_RECEIVED')")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        em.createNativeQuery(
                "UPDATE receipt_batches SET status='CANCELLED', cancelled_at=now(), updated_at=now() WHERE receipt_id IN " +
                "(SELECT id FROM receipts WHERE ref_doc_type='sales_return' AND ref_doc_id=?1 AND tenant_id=?2) AND status='DRAFT'")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("status", "CANCELLED");
        return ApiResponse.ok(res);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "销退订单不存在");
        if (!"DRAFT".equals(status)) return ApiResponse.fail(40009, "仅草稿状态可删除");
        em.createNativeQuery("UPDATE orders SET deleted_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        return ApiResponse.ok(Map.of("id", id));
    }

    // ==================== 辅助 ====================
    @SuppressWarnings("unchecked")
    private String validateReturnLines(UUID tid, Long refSalesOutId, Map<String, Object> body) {
        if (refSalesOutId == null) return "必须关联已发货的发货单";
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null || lines.isEmpty()) return "销退明细不能为空";
        for (int i = 0; i < lines.size(); i++) {
            Map<String, Object> l = lines.get(i);
            if (l.get("productId") == null) return "第 " + (i + 1) + " 行未选择产品";
            BigDecimal qty = toBd(l.get("qty"));
            if (qty.signum() <= 0) return "第 " + (i + 1) + " 行退货数量必须大于 0";
        }
        // 按 product+batch+serial 聚合退货数量，校验不超过可退数量
        var lq = em.createNativeQuery(
                "SELECT product_id, COALESCE(batch_no,'') AS batch_no, COALESCE(serial_no,'') AS serial_no, " +
                "COALESCE(SUM(COALESCE(shipped_qty,qty,0)),0) AS shipped " +
                "FROM sales_out_lines WHERE sales_out_id=?1 GROUP BY product_id, batch_no, serial_no", Tuple.class);
        lq.setParameter(1, refSalesOutId);
        Map<String, BigDecimal> shippedMap = new HashMap<>();
        for (Object o : lq.getResultList()) {
            Tuple t = (Tuple) o;
            String key = t.get("product_id") + "|" + t.get("batch_no") + "|" + t.get("serial_no");
            shippedMap.put(key, new BigDecimal(String.valueOf(t.get("shipped"))));
        }
        // 减去其它未取消销退已占用的数量
        var aq = em.createNativeQuery(
                "SELECT ol.product_id, COALESCE(ol.batch_no,'') AS batch_no, COALESCE(ol.serial_no,'') AS serial_no, " +
                "COALESCE(SUM(ol.qty),0) AS used FROM order_lines ol JOIN orders o ON o.id=ol.order_id " +
                "WHERE o.ref_sales_out_id=?1 AND o.tenant_id=?2 AND COALESCE(o.is_red,false)=true AND o.status NOT IN ('CANCELLED','REJECTED') " +
                "GROUP BY ol.product_id, ol.batch_no, ol.serial_no", Tuple.class);
        aq.setParameter(1, refSalesOutId).setParameter(2, tid);
        Map<String, BigDecimal> usedMap = new HashMap<>();
        for (Object o : aq.getResultList()) {
            Tuple t = (Tuple) o;
            String key = t.get("product_id") + "|" + t.get("batch_no") + "|" + t.get("serial_no");
            usedMap.put(key, new BigDecimal(String.valueOf(t.get("used"))));
        }
        Map<String, BigDecimal> reqMap = new HashMap<>();
        int rowNo = 0;
        for (Map<String, Object> l : lines) {
            rowNo++;
            String key = l.get("productId") + "|" + strOr(l.get("batchNo"), "") + "|" + strOr(l.get("serialNo"), "");
            reqMap.merge(key, toBd(l.get("qty")), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> e : reqMap.entrySet()) {
            BigDecimal shipped = shippedMap.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal used = usedMap.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal available = shipped.subtract(used);
            if (e.getValue().compareTo(available) > 0) {
                return "第 " + rowNo + " 行累计退货数量 " + e.getValue() + " 超过可退数量 " + available + "（已发货 " + shipped + "，已退 " + used + "）";
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void insertLines(Long orderId, Map<String, Object> body) {
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null) return;
        int seq = 1;
        for (Map<String, Object> l : lines) {
            if (l.get("productId") == null) continue;
            BigDecimal qty = toBd(l.get("qty"));
            BigDecimal price = toBd(l.get("unitPrice"));
            BigDecimal tax = toBd(l.get("taxRate"));
            if (tax.signum() == 0) tax = new BigDecimal("0.13");
            BigDecimal sub = qty.multiply(price);
            em.createNativeQuery(
                    "INSERT INTO order_lines (order_id, seq, product_id, batch_no, serial_no, qty, unit_price, tax_rate, sub_total, is_gift, created_at, updated_at) " +
                    "VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,now(),now())")
              .setParameter(1, orderId).setParameter(2, seq++)
              .setParameter(3, toLong(l.get("productId")))
              .setParameter(4, l.get("batchNo"))
              .setParameter(5, l.get("serialNo"))
              .setParameter(6, qty).setParameter(7, price).setParameter(8, tax).setParameter(9, sub)
              .setParameter(10, Boolean.TRUE.equals(l.get("isGift")))
              .executeUpdate();
        }
    }

    private BigDecimal calcTotal(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) for (Map<String, Object> l : lines)
            total = total.add(toBd(l.get("qty")).multiply(toBd(l.get("unitPrice"))));
        return total;
    }

    private ApiResponse<Map<String, Object>> doTransition(Long id, String from, String to) {
        UUID tid = TenantContext.getTenantId();
        int n = em.createNativeQuery(
                "UPDATE orders SET status=?1, updated_at=now(), " +
                "submitted_at=CASE WHEN ?1='SUBMITTED' THEN now() ELSE submitted_at END " +
                "WHERE id=?2 AND tenant_id=?3 AND status=?4 AND COALESCE(is_red,false)=true")
          .setParameter(1, to).setParameter(2, id).setParameter(3, tid).setParameter(4, from).executeUpdate();
        if (n == 0) return ApiResponse.fail(40009, "状态不允许该操作，需要当前状态为 " + from);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("newStatus", to);
        return ApiResponse.ok(res);
    }

    private String getStatus(Long id, UUID tid) {
        try {
            var q = em.createNativeQuery("SELECT status FROM orders WHERE id=?1 AND tenant_id=?2");
            q.setParameter(1, id).setParameter(2, tid);
            return String.valueOf(q.getSingleResult());
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> toBrief(Tuple t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id")); m.put("code", t.get("code"));
        m.put("orderType", t.get("order_type"));
        m.put("dealerId", t.get("dealer_id")); m.put("dealerName", t.get("dealer_name"));
        m.put("warehouseId", t.get("warehouse_id")); m.put("warehouseName", t.get("warehouse_name"));
        m.put("refOrderId", t.get("ref_order_id"));
        m.put("refSalesOutId", t.get("ref_sales_out_id"));
        m.put("returnReason", t.get("return_reason"));
        m.put("amountInclTax", t.get("amount_incl_tax"));
        m.put("finalAmount", t.get("final_amount"));
        m.put("auditUserName", t.get("audit_user_name"));
        m.put("status", t.get("status"));
        try { m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at"))); } catch (Exception ignored) {}
        return m;
    }

    private List<Map<String, Object>> allowedActions(String status) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if ("DRAFT".equals(status)) {
            actions.add(action("edit", "编辑", "primary", "PUT", ""));
            actions.add(action("submit", "提交审批", "warning", "POST", "/submit"));
            actions.add(action("cancel", "取消", "danger", "POST", "/cancel"));
        } else if ("SUBMITTED".equals(status)) {
            actions.add(action("approve", "审批通过", "success", "POST", "/approve"));
            actions.add(action("reject", "驳回", "danger", "POST", "/reject"));
        } else if ("APPROVED".equals(status)) {
            actions.add(action("cancel", "取消", "warning", "POST", "/cancel"));
        }
        return actions;
    }

    private Map<String, Object> action(String key, String label, String type, String method, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key); m.put("label", label); m.put("type", type);
        m.put("method", method); m.put("path", path);
        return m;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }

    private BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String strOr(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? def : s;
    }
}
