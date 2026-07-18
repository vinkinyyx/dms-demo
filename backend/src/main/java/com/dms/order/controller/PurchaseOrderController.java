/*
 * 采购订单 Controller - 完整状态机 + 库存联动
 * 状态机：DRAFT -> SUBMITTED -> APPROVED -> RECEIVING -> COMPLETED
 *                       \-> REJECTED / CANCELLED
 */
package com.dms.order.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final EntityManager em;

    /** 分页列表 */
    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId) {
        UUID tid = TenantContext.getTenantId();
        int offset = (page - 1) * size;

        StringBuilder where = new StringBuilder(" WHERE tenant_id = ?1 AND deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?").append(idx++);
            params.add(status);
        }
        if (supplierId != null) {
            where.append(" AND supplier_id = ?").append(idx++);
            params.add(supplierId);
        }

        var qCnt = em.createNativeQuery("SELECT COUNT(*) FROM purchase_orders " + where);
        for (int i = 0; i < params.size(); i++) qCnt.setParameter(i + 1, params.get(i));
        long total = ((Number) qCnt.getSingleResult()).longValue();

        String limitParam = "?" + idx++;
        String offsetParam = "?" + idx++;
        var q = em.createNativeQuery(
                "SELECT id, code, order_type, supplier_id, supplier_name, warehouse_id, " +
                "amount_incl_tax, final_amount, expected_date, status, extra, created_at " +
                "FROM purchase_orders " + where +
                " ORDER BY created_at DESC LIMIT " + limitParam + " OFFSET " + offsetParam,
                Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(params.size() + 1, size);
        q.setParameter(params.size() + 2, offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            list.add(toBrief(t));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("list", list);
        return ApiResponse.ok(data);
    }

    /** 详情（含明细） */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        Map<String, Object> data = readOne(id, tid);
        if (data == null) return ApiResponse.fail(40404, "采购单不存在");

        // 明细
        var q = em.createNativeQuery(
                "SELECT pol.id, pol.seq, pol.product_id, p.code AS p_code, p.name_cn AS p_name, p.spec AS p_spec, " +
                "pol.qty, pol.received_qty, pol.unit_price, pol.tax_rate, pol.subtotal, pol.remark " +
                "FROM purchase_order_lines pol LEFT JOIN products p ON p.id = pol.product_id " +
                "WHERE pol.po_id = ?1 ORDER BY pol.seq", Tuple.class);
        q.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> lineRows = q.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple t : lineRows) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", t.get("id"));
            l.put("seq", t.get("seq"));
            l.put("productId", t.get("product_id"));
            l.put("productCode", t.get("p_code"));
            l.put("productName", t.get("p_name"));
            l.put("productSpec", t.get("p_spec"));
            l.put("qty", t.get("qty"));
            l.put("receivedQty", t.get("received_qty"));
            l.put("unitPrice", t.get("unit_price"));
            l.put("taxRate", t.get("tax_rate"));
            l.put("subtotal", t.get("subtotal"));
            l.put("remark", t.get("remark"));
            lines.add(l);
        }
        data.put("lines", lines);
        data.put("allowedActions", allowedActions(String.valueOf(data.get("status"))));
        return ApiResponse.ok(data);
    }

    /** 创建（DRAFT） */
    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String code = "PO-" + System.currentTimeMillis();

        BigDecimal total = calcTotal(body);

        var insertPo = em.createNativeQuery(
                "INSERT INTO purchase_orders (tenant_id, code, order_type, supplier_id, supplier_name, warehouse_id, " +
                "amount_incl_tax, final_amount, expected_date, status, remark, extra, created_at, updated_at) " +
                "VALUES (:tid, :code, :ot, :sid, :sname, :wid, :amt, :famt, :ed, 'DRAFT', :rmk, CAST(:ext AS jsonb), now(), now()) RETURNING id");
        insertPo.setParameter("tid", tid);
        insertPo.setParameter("code", code);
        insertPo.setParameter("ot", body.getOrDefault("orderType", "NORMAL"));
        insertPo.setParameter("sid", body.get("supplierId"));
        insertPo.setParameter("sname", body.getOrDefault("supplierName", ""));
        insertPo.setParameter("wid", body.get("warehouseId"));
        insertPo.setParameter("amt", total);
        insertPo.setParameter("famt", total);
        insertPo.setParameter("ed", body.get("expectedDate"));
        insertPo.setParameter("rmk", body.getOrDefault("remark", ""));
        insertPo.setParameter("ext", extraToJson(body.get("extra")));
        Long poId = ((Number) insertPo.getSingleResult()).longValue();

        insertLines(poId, body);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", poId);
        res.put("code", code);
        return ApiResponse.ok(res);
    }

    /** 更新（仅 DRAFT 可改） */
    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (!"DRAFT".equals(status)) return ApiResponse.fail(40009, "仅草稿可编辑，当前状态: " + status);

        BigDecimal total = calcTotal(body);
        em.createNativeQuery(
                "UPDATE purchase_orders SET order_type = :ot, supplier_id = :sid, warehouse_id = :wid, " +
                "amount_incl_tax = :amt, final_amount = :famt, expected_date = :ed, remark = :rmk, extra = CAST(:ext AS jsonb), updated_at = now() " +
                "WHERE id = :id AND tenant_id = :tid")
            .setParameter("ot", body.getOrDefault("orderType", "NORMAL"))
            .setParameter("sid", body.get("supplierId"))
            .setParameter("wid", body.get("warehouseId"))
            .setParameter("amt", total)
            .setParameter("famt", total)
            .setParameter("ed", body.get("expectedDate"))
            .setParameter("rmk", body.getOrDefault("remark", ""))
            .setParameter("ext", extraToJson(body.get("extra")))
            .setParameter("id", id)
            .setParameter("tid", tid)
            .executeUpdate();

        em.createNativeQuery("DELETE FROM purchase_order_lines WHERE po_id = ?1").setParameter(1, id).executeUpdate();
        insertLines(id, body);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        return ApiResponse.ok(res);
    }

    /** 提交审批 */
    @PostMapping("/{id}/submit")
    @Transactional
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        return doTransition(id, "DRAFT", "SUBMITTED", "PO_SUBMIT");
    }

    /** 审批通过 */
    @PostMapping("/{id}/approve")
    @Transactional
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id) {
        ApiResponse<Map<String, Object>> res = doTransition(id, "SUBMITTED", "APPROVED", "PO_APPROVE");
        if (res.getCode() == 0) {
            em.createNativeQuery("UPDATE purchase_orders SET approved_at = now(), approved_by = ?1 WHERE id = ?2")
              .setParameter(1, TenantContext.getUserId()).setParameter(2, id).executeUpdate();
        }
        return res;
    }

    /** 驳回 */
    @PostMapping("/{id}/reject")
    @Transactional
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return doTransition(id, "SUBMITTED", "REJECTED", "PO_REJECT");
    }

    /** 取消（草稿或已提交可取消） */
    @PostMapping("/{id}/cancel")
    @Transactional
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (!"DRAFT".equals(status) && !"SUBMITTED".equals(status)) {
            return ApiResponse.fail(40009, "当前状态不允许取消: " + status);
        }
        em.createNativeQuery("UPDATE purchase_orders SET status='CANCELLED', updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        audit(id, "PO_CANCEL");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("status", "CANCELLED");
        return ApiResponse.ok(res);
    }

    /**
     * 采购收货入库（US-B-12）
     * 已批准的采购单 → 逐行入库 → 更新 inventory 表 + 写 inventory_transactions
     * 全部收完 → 状态 COMPLETED
     */
    @PostMapping("/{id}/receive")
    @Transactional
    public ApiResponse<Map<String, Object>> receive(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (!"APPROVED".equals(status) && !"RECEIVING".equals(status)) {
            return ApiResponse.fail(40009, "仅已批准/收货中的采购单可入库，当前: " + status);
        }

        // 读取采购单信息（仓库）
        var qPo = em.createNativeQuery(
                "SELECT warehouse_id FROM purchase_orders WHERE id = ?1 AND tenant_id = ?2");
        qPo.setParameter(1, id).setParameter(2, tid);
        List<?> pos = qPo.getResultList();
        if (pos.isEmpty()) return ApiResponse.fail(40404, "采购单不存在");
        Long warehouseId = pos.get(0) == null ? null : ((Number) pos.get(0)).longValue();

        // 收货明细 (可以指定明细行 partial，也可以整单收货)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> receiveLines = body == null ? null : (List<Map<String, Object>>) body.get("lines");

        int receivedCount = 0;
        BigDecimal totalQty = BigDecimal.ZERO;

        if (receiveLines == null || receiveLines.isEmpty()) {
            // 整单收货：把每行 (qty - received_qty) 全部收货
            var qLines = em.createNativeQuery(
                    "SELECT id, product_id, qty, received_qty FROM purchase_order_lines WHERE po_id = ?1", Tuple.class);
            qLines.setParameter(1, id);
            @SuppressWarnings("unchecked")
            List<Tuple> lines = qLines.getResultList();
            for (Tuple t : lines) {
                Long lineId = ((Number) t.get("id")).longValue();
                Long productId = ((Number) t.get("product_id")).longValue();
                BigDecimal qty = new BigDecimal(String.valueOf(t.get("qty")));
                BigDecimal received = new BigDecimal(String.valueOf(t.get("received_qty")));
                BigDecimal remaining = qty.subtract(received);
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    doReceive(tid, id, lineId, productId, warehouseId, remaining, "PO-" + id);
                    receivedCount++;
                    totalQty = totalQty.add(remaining);
                }
            }
        } else {
            for (Map<String, Object> rl : receiveLines) {
                Long lineId = toLong(rl.get("lineId"));
                Long productId = toLong(rl.get("productId"));
                BigDecimal qty = new BigDecimal(String.valueOf(rl.get("qty")));
                doReceive(tid, id, lineId, productId, warehouseId, qty, "PO-" + id);
                receivedCount++;
                totalQty = totalQty.add(qty);
            }
        }

        // 检查是否全部收完 → 变 COMPLETED
        var qCheck = em.createNativeQuery(
                "SELECT COUNT(*) FROM purchase_order_lines WHERE po_id = ?1 AND received_qty < qty");
        qCheck.setParameter(1, id);
        long unfinished = ((Number) qCheck.getSingleResult()).longValue();
        String newStatus = unfinished == 0 ? "COMPLETED" : "RECEIVING";
        em.createNativeQuery("UPDATE purchase_orders SET status = ?1, updated_at = now(), completed_at = CASE WHEN ?1 = 'COMPLETED' THEN now() ELSE completed_at END WHERE id = ?2")
          .setParameter(1, newStatus).setParameter(2, id).executeUpdate();

        audit(id, "PO_RECEIVE");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("receivedLines", receivedCount);
        res.put("totalQtyReceived", totalQty);
        res.put("newStatus", newStatus);
        return ApiResponse.ok(res);
    }

    // ============ 辅助方法 ============

    private void doReceive(UUID tid, Long poId, Long lineId, Long productId, Long warehouseId, BigDecimal qty, String refCode) {
        // 1. 更新采购单明细 received_qty
        em.createNativeQuery(
                "UPDATE purchase_order_lines SET received_qty = received_qty + ?1 WHERE id = ?2")
            .setParameter(1, qty).setParameter(2, lineId).executeUpdate();

        // 2. 更新 inventory 表（若已存在则累加；不存在则新建）
        var existQ = em.createNativeQuery(
                "SELECT id, qty FROM inventory WHERE tenant_id = ?1 AND product_id = ?2 AND warehouse_id = ?3 " +
                "AND (batch_no IS NULL OR batch_no = '') AND (serial_no IS NULL OR serial_no = '') LIMIT 1", Tuple.class);
        existQ.setParameter(1, tid).setParameter(2, productId).setParameter(3, warehouseId);
        @SuppressWarnings("unchecked")
        List<Tuple> exs = existQ.getResultList();
        if (exs.isEmpty()) {
            em.createNativeQuery(
                    "INSERT INTO inventory (tenant_id, product_id, warehouse_id, qty, in_source, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, ?4, 'PO', now(), now())")
                .setParameter(1, tid).setParameter(2, productId).setParameter(3, warehouseId).setParameter(4, qty)
                .executeUpdate();
        } else {
            Long invId = ((Number) exs.get(0).get("id")).longValue();
            em.createNativeQuery("UPDATE inventory SET qty = qty + ?1, updated_at = now() WHERE id = ?2")
                .setParameter(1, qty).setParameter(2, invId).executeUpdate();
        }

        // 3. 写事务日志
        em.createNativeQuery(
                "INSERT INTO inventory_transactions (tenant_id, product_id, warehouse_id, txn_type, qty_change, " +
                "ref_doc_type, ref_doc_id, at_time) " +
                "VALUES (?1, ?2, ?3, 'RECEIPT', ?4, 'purchase_order', ?5, now())")
            .setParameter(1, tid).setParameter(2, productId).setParameter(3, warehouseId).setParameter(4, qty)
            .setParameter(5, poId).executeUpdate();
    }

    private ApiResponse<Map<String, Object>> doTransition(Long id, String fromStatus, String toStatus, String action) {
        UUID tid = TenantContext.getTenantId();
        int n = em.createNativeQuery(
                "UPDATE purchase_orders SET status = ?1, updated_at = now(), " +
                "submitted_at = CASE WHEN ?1 = 'SUBMITTED' THEN now() ELSE submitted_at END " +
                "WHERE id = ?2 AND tenant_id = ?3 AND status = ?4")
            .setParameter(1, toStatus).setParameter(2, id).setParameter(3, tid).setParameter(4, fromStatus)
            .executeUpdate();
        if (n == 0) return ApiResponse.fail(40009, "状态不允许该操作，需要当前状态为 " + fromStatus);
        audit(id, action);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("newStatus", toStatus);
        return ApiResponse.ok(res);
    }

    private void audit(Long id, String action) {
        try {
            em.createNativeQuery(
                    "INSERT INTO audit_logs (tenant_id, user_id, action, resource_type, resource_id, ip, at_time) " +
                    "VALUES (?1, ?2, ?3, 'purchase_order', ?4, '127.0.0.1', now())")
                .setParameter(1, TenantContext.getTenantId()).setParameter(2, TenantContext.getUserId())
                .setParameter(3, action).setParameter(4, String.valueOf(id))
                .executeUpdate();
        } catch (Exception ignored) {}
    }

    private String getStatus(Long id, UUID tid) {
        try {
            var q = em.createNativeQuery(
                    "SELECT status FROM purchase_orders WHERE id = ?1 AND tenant_id = ?2");
            q.setParameter(1, id).setParameter(2, tid);
            return String.valueOf(q.getSingleResult());
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> readOne(Long id, UUID tid) {
        try {
            var q = em.createNativeQuery(
                    "SELECT id, code, order_type, supplier_id, supplier_name, warehouse_id, " +
                    "amount_incl_tax, final_amount, expected_date, status, remark, extra, " +
                    "created_at, updated_at, submitted_at, approved_at, completed_at " +
                    "FROM purchase_orders WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
            q.setParameter(1, id).setParameter(2, tid);
            @SuppressWarnings("unchecked")
            List<Tuple> rs = q.getResultList();
            if (rs.isEmpty()) return null;
            return toBrief(rs.get(0));
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> toBrief(Tuple t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id"));
        m.put("code", t.get("code"));
        m.put("orderType", t.get("order_type"));
        m.put("supplierId", t.get("supplier_id"));
        try { m.put("supplierName", t.get("supplier_name")); } catch (Exception ignored) {}
        try { m.put("warehouseId", t.get("warehouse_id")); } catch (Exception ignored) {}
        m.put("amountInclTax", t.get("amount_incl_tax"));
        m.put("finalAmount", t.get("final_amount"));
        try { m.put("expectedDate", String.valueOf(t.get("expected_date"))); } catch (Exception ignored) {}
        m.put("status", t.get("status"));
        try { m.put("remark", t.get("remark")); } catch (Exception ignored) {}
        try { m.put("extra", t.get("extra")); } catch (Exception ignored) {}
        try { m.put("createdAt", String.valueOf(t.get("created_at"))); } catch (Exception ignored) {}
        try { m.put("submittedAt", String.valueOf(t.get("submitted_at"))); } catch (Exception ignored) {}
        try { m.put("approvedAt", String.valueOf(t.get("approved_at"))); } catch (Exception ignored) {}
        try { m.put("completedAt", String.valueOf(t.get("completed_at"))); } catch (Exception ignored) {}
        return m;
    }

    private BigDecimal calcTotal(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Map<String, Object> l : lines) {
                BigDecimal qty = new BigDecimal(String.valueOf(l.getOrDefault("qty", "0")));
                BigDecimal price = new BigDecimal(String.valueOf(l.getOrDefault("unitPrice", "0")));
                total = total.add(qty.multiply(price));
            }
        }
        return total;
    }

    private void insertLines(Long poId, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null) return;
        int seq = 1;
        for (Map<String, Object> l : lines) {
            if (l.get("productId") == null) continue;
            BigDecimal qty = new BigDecimal(String.valueOf(l.getOrDefault("qty", "0")));
            BigDecimal price = new BigDecimal(String.valueOf(l.getOrDefault("unitPrice", "0")));
            BigDecimal tax = new BigDecimal(String.valueOf(l.getOrDefault("taxRate", "0.13")));
            BigDecimal sub = qty.multiply(price);
            em.createNativeQuery(
                    "INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, unit_price, tax_rate, subtotal, remark, created_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, now())")
                .setParameter(1, poId).setParameter(2, seq++)
                .setParameter(3, toLong(l.get("productId")))
                .setParameter(4, qty).setParameter(5, price).setParameter(6, tax).setParameter(7, sub)
                .setParameter(8, l.getOrDefault("remark", ""))
                .executeUpdate();
        }
    }

    /** 状态机 → 允许的操作 */
    private List<Map<String, Object>> allowedActions(String status) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if ("DRAFT".equals(status)) {
            actions.add(action("edit", "编辑", "primary", "PUT", ""));
            actions.add(action("submit", "提交审批", "warn", "POST", "/submit"));
            actions.add(action("cancel", "取消", "danger", "POST", "/cancel"));
        } else if ("SUBMITTED".equals(status)) {
            actions.add(action("approve", "审批通过", "success", "POST", "/approve"));
            actions.add(action("reject", "驳回", "danger", "POST", "/reject"));
            actions.add(action("cancel", "取消", "default", "POST", "/cancel"));
        } else if ("APPROVED".equals(status)) {
            actions.add(action("receive", "收货入库", "success", "POST", "/receive"));
        } else if ("RECEIVING".equals(status)) {
            actions.add(action("receive", "继续收货", "success", "POST", "/receive"));
        }
        // COMPLETED / REJECTED / CANCELLED - 无可执行操作，只能查看
        return actions;
    }

    private Map<String, Object> action(String key, String label, String type, String method, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("type", type);
        m.put("method", method);
        m.put("path", path);
        return m;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.valueOf(String.valueOf(o));
    }

    private String extraToJson(Object extra) {
        if (extra == null) return "{}";
        if (extra instanceof String) return (String) extra;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extra);
        } catch (Exception e) { return "{}"; }
    }
}
