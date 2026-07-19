/*
 * 业务单据详情增强 Controller (v3.4.4)
 *   GET /api/sales-outs/{id}/detail       销售出库详情(含明细行)
 *   GET /api/receipts/{id}/detail          收货入库详情(含明细行)
 *   GET /api/orders/{id}/detail            订单详情(含明细行)
 *   GET /api/purchase-orders/{id}/detail   采购订单详情(含明细行)
 */
package com.dms.execution.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class BizDocDetailController {

    private final EntityManager em;

    @GetMapping({"/api/sales-outs/{id}/detail", "/api/sales-outs/{id}"})
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> salesOutDetail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT so.*, d.name AS dealer_name, h.name AS terminal_name " +
                "FROM sales_outs so " +
                "LEFT JOIN dealers d ON d.id = so.dealer_id " +
                "LEFT JOIN hospitals h ON h.id = so.terminal_id " +
                "WHERE so.id = ?1 AND so.tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "销售出库单不存在");
        Tuple t = (Tuple) rs.get(0);
        Map<String, Object> head = tupleToMap(t);

        // 明细
        var lq = em.createNativeQuery(
                "SELECT sol.*, p.name_cn AS product_name, p.code AS product_code, p.is_serial_managed " +
                "FROM sales_out_lines sol " +
                "LEFT JOIN products p ON p.id = sol.product_id " +
                "WHERE sol.sales_out_id = ?1 ORDER BY sol.id", Tuple.class);
        lq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : ls) lines.add(tupleToMap(l));

        head.put("lines", lines);
        // v3.4.12: 每笔发货执行明细
        var eq = em.createNativeQuery(
                "SELECT e.*, p.name_cn AS product_name, p.code AS product_code, u.name AS operator_name " +
                "FROM sales_out_execution_lines e " +
                "LEFT JOIN products p ON p.id = e.product_id " +
                "LEFT JOIN users u ON u.id = e.operator_id " +
                "WHERE e.sales_out_id = ?1 ORDER BY e.seq_no", Tuple.class);
        eq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> es = eq.getResultList();
        List<Map<String, Object>> execLines = new ArrayList<>();
        for (Tuple e : es) execLines.add(tupleToMap(e));
        head.put("executionLines", execLines);
        // 需求9: 带出关联销售订单表头信息作参考
        Object soId = head.get("sourceOrderId");
        if (soId != null) {
            try {
                var srcQ = em.createNativeQuery(
                        "SELECT o.code, o.order_type, o.status, o.amount_incl_tax, o.created_at, d.name AS dealer_name " +
                        "FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id WHERE o.id = ?1", Tuple.class);
                srcQ.setParameter(1, ((Number) soId).longValue());
                List<?> srs = srcQ.getResultList();
                if (!srs.isEmpty()) {
                    Tuple s = (Tuple) srs.get(0);
                    Map<String, Object> src = new LinkedHashMap<>();
                    src.put("code", s.get("code"));
                    src.put("orderType", s.get("order_type"));
                    src.put("status", s.get("status"));
                    src.put("amountInclTax", s.get("amount_incl_tax"));
                    src.put("dealerName", s.get("dealer_name"));
                    src.put("createdAt", com.dms.common.util.DateFmt.fmt(s.get("created_at")));
                    head.put("sourceOrder", src);
                }
            } catch (Exception ignored) {}
        }
        return ApiResponse.ok(head);
    }

    @GetMapping({"/api/receipts/{id}/detail", "/api/receipts/{id}"})
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> receiptDetail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT r.*, w.name AS warehouse_name " +
                "FROM receipts r LEFT JOIN warehouses w ON w.id = r.warehouse_id " +
                "WHERE r.id = ?1 AND r.tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "收货单不存在");
        Tuple t = (Tuple) rs.get(0);
        Map<String, Object> head = tupleToMap(t);

        var lq = em.createNativeQuery(
                "SELECT rl.*, p.name_cn AS product_name, p.code AS product_code, p.is_serial_managed " +
                "FROM receipt_lines rl " +
                "LEFT JOIN products p ON p.id = rl.product_id " +
                "WHERE rl.receipt_id = ?1 ORDER BY rl.id", Tuple.class);
        lq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : ls) lines.add(tupleToMap(l));

        head.put("lines", lines);
        // v3.4.12: 每笔收货执行明细
        var eq = em.createNativeQuery(
                "SELECT e.*, p.name_cn AS product_name, p.code AS product_code, u.name AS operator_name " +
                "FROM receipt_execution_lines e " +
                "LEFT JOIN products p ON p.id = e.product_id " +
                "LEFT JOIN users u ON u.id = e.operator_id " +
                "WHERE e.receipt_id = ?1 ORDER BY e.seq_no", Tuple.class);
        eq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> es = eq.getResultList();
        List<Map<String, Object>> execLines = new ArrayList<>();
        for (Tuple e : es) execLines.add(tupleToMap(e));
        head.put("executionLines", execLines);
        // 需求9: 带出关联采购订单表头信息作参考
        Object poId = head.get("sourcePoId");
        if (poId != null) {
            try {
                var srcQ = em.createNativeQuery(
                        "SELECT po.code, po.status, po.amount_incl_tax, po.created_at, s.name AS supplier_name " +
                        "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id WHERE po.id = ?1", Tuple.class);
                srcQ.setParameter(1, ((Number) poId).longValue());
                List<?> srs = srcQ.getResultList();
                if (!srs.isEmpty()) {
                    Tuple s = (Tuple) srs.get(0);
                    Map<String, Object> src = new LinkedHashMap<>();
                    src.put("code", s.get("code"));
                    src.put("status", s.get("status"));
                    src.put("amountInclTax", s.get("amount_incl_tax"));
                    src.put("supplierName", s.get("supplier_name"));
                    src.put("createdAt", com.dms.common.util.DateFmt.fmt(s.get("created_at")));
                    head.put("sourcePo", src);
                }
            } catch (Exception ignored) {}
        }
        return ApiResponse.ok(head);
    }

    @GetMapping("/api/orders/{id}/detail")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> orderDetail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT o.*, d.name AS dealer_name " +
                "FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id " +
                "WHERE o.id = ?1 AND o.tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        Map<String, Object> head = tupleToMap((Tuple) rs.get(0));

        var lq = em.createNativeQuery(
                "SELECT ol.*, p.name_cn AS product_name, p.code AS product_code " +
                "FROM order_lines ol LEFT JOIN products p ON p.id = ol.product_id " +
                "WHERE ol.order_id = ?1 ORDER BY ol.id", Tuple.class);
        lq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : ls) lines.add(tupleToMap(l));

        head.put("lines", lines);
        return ApiResponse.ok(head);
    }

    @GetMapping("/api/purchase-orders/{id}/detail")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> poDetail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT po.*, s.name AS supplier_name_ref, w.name AS warehouse_name " +
                "FROM purchase_orders po " +
                "LEFT JOIN suppliers s ON s.id = po.supplier_id " +
                "LEFT JOIN warehouses w ON w.id = po.warehouse_id " +
                "WHERE po.id = ?1 AND po.tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "采购单不存在");
        Map<String, Object> head = tupleToMap((Tuple) rs.get(0));
        // v3.4.12: supplier_name 为空时用 join 出的 suppliers.name 兜底
        Object sn = head.get("supplierName");
        if (sn == null || String.valueOf(sn).isBlank()) {
            head.put("supplierName", head.get("supplierNameRef"));
        }

        var lq = em.createNativeQuery(
                "SELECT pol.*, p.name_cn AS product_name, p.code AS product_code " +
                "FROM purchase_order_lines pol LEFT JOIN products p ON p.id = pol.product_id " +
                "WHERE pol.po_id = ?1 ORDER BY pol.id", Tuple.class);
        lq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : ls) lines.add(tupleToMap(l));

        head.put("lines", lines);
        return ApiResponse.ok(head);
    }

    private Map<String, Object> tupleToMap(Tuple t) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (var el : t.getElements()) {
            String name = el.getAlias();
            if (name == null) continue;
            Object v = t.get(name);
            // 时间列统一按北京时区格式化（需求5）
            if (v != null && (name.endsWith("_at") || name.endsWith("_date") || name.endsWith("_time"))
                    && (v instanceof java.sql.Timestamp || v instanceof java.time.OffsetDateTime
                        || v instanceof java.sql.Date || v instanceof java.time.temporal.Temporal)) {
                v = com.dms.common.util.DateFmt.fmt(v);
            }
            // 把 snake_case 转 camelCase 便于前端识别
            m.put(toCamel(name), v);
        }
        return m;
    }

    private String toCamel(String s) {
        if (s == null || s.indexOf('_') < 0) return s;
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : s.toCharArray()) {
            if (c == '_') { upper = true; continue; }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }
}
