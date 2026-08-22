package com.dms.execution.service;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BizDocDetailService {
    private final EntityManager em;
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> salesOutDetail(Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT so.*, d.name AS dealer_name, h.name AS terminal_name, w.name AS warehouse_name " +
                "FROM sales_outs so " +
                "LEFT JOIN dealers d ON d.id = so.dealer_id " +
                "LEFT JOIN hospitals h ON h.id = so.terminal_id " +
                "LEFT JOIN warehouses w ON w.id = so.warehouse_id " +
                "WHERE so.id = ?1 AND so.tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "销售出库单不存在");
        Tuple t = (Tuple) rs.get(0);
        Map<String, Object> head = tupleToMap(t);

        // 应发明细：expected_qty > 0 的行（按订单行带过来）
        var lq = em.createNativeQuery(
                "SELECT sol.*, p.name_cn AS product_name, p.code AS product_code, p.spec AS p_spec, p.is_serial_managed " +
                "FROM sales_out_lines sol " +
                "LEFT JOIN products p ON p.id = sol.product_id " +
                "WHERE sol.sales_out_id = ?1 AND COALESCE(sol.expected_qty,0) > 0 " +
                "ORDER BY sol.seq NULLS LAST, sol.id", Tuple.class);
        lq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : ls) lines.add(tupleToMap(l));
        head.put("lines", lines);

        // 已发执行明细：每一次 partial-ship 都会插一行 shipped_qty>0
        var eql = em.createNativeQuery(
                "SELECT sol.*, p.name_cn AS product_name, p.code AS product_code, p.is_serial_managed " +
                "FROM sales_out_lines sol " +
                "LEFT JOIN products p ON p.id = sol.product_id " +
                "WHERE sol.sales_out_id = ?1 AND COALESCE(sol.expected_qty,0) = 0 AND COALESCE(sol.shipped_qty,0) > 0 " +
                "ORDER BY sol.id", Tuple.class);
        eql.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> els = eql.getResultList();
        List<Map<String, Object>> shippedLines = new ArrayList<>();
        for (Tuple e : els) shippedLines.add(tupleToMap(e));
        head.put("shippedLines", shippedLines);

        // 关联销售订单详情（含订单行），用于展示来源信息
        Object soId = head.get("sourceOrderId");
        if (soId != null) {
            try {
                Long srcId = ((Number) soId).longValue();
                var srcQ = em.createNativeQuery(
                        "SELECT o.*, COALESCE(NULLIF(CAST(o.ship_snapshot AS jsonb)->>'dealerName',''), d.name) AS dealer_name, " +
                        "w.name AS warehouse_name, u1.name AS created_by_name, u2.name AS approved_by_name " +
                        "FROM orders o " +
                        "LEFT JOIN dealers d ON d.id = o.dealer_id " +
                        "LEFT JOIN warehouses w ON w.id = o.warehouse_id " +
                        "LEFT JOIN users u1 ON u1.id = o.created_by " +
                        "LEFT JOIN users u2 ON u2.id = o.approved_by " +
                        "WHERE o.id = ?1", Tuple.class);
                srcQ.setParameter(1, srcId);
                @SuppressWarnings("unchecked")
                List<Tuple> srs = srcQ.getResultList();
                if (!srs.isEmpty()) {
                    Tuple s = srs.get(0);
                    Map<String, Object> src = new LinkedHashMap<>();
                    src.put("id", srcId);
                    src.put("code", s.get("code"));
                    src.put("orderType", s.get("order_type"));
                    src.put("status", s.get("status"));
                    src.put("dealerId", s.get("dealer_id"));
                    src.put("dealerName", s.get("dealer_name"));
                    src.put("warehouseId", s.get("warehouse_id"));
                    src.put("warehouseName", s.get("warehouse_name"));
                    src.put("amountInclTax", s.get("amount_incl_tax"));
                    src.put("discountAmount", s.get("discount_amount"));
                    src.put("taxAmount", s.get("tax_amount"));
                    src.put("finalAmount", s.get("final_amount"));
                    src.put("expectedDate", com.dms.common.util.DateFmt.fmt(s.get("expected_date")));
                    src.put("remark", s.get("remark"));
                    src.put("createdAt", com.dms.common.util.DateFmt.fmt(s.get("created_at")));
                    src.put("createdByName", s.get("created_by_name"));
                    src.put("approvedAt", com.dms.common.util.DateFmt.fmt(s.get("approved_at")));
                    src.put("approvedByName", s.get("approved_by_name"));
                    head.put("sourceOrder", src);
                }
                // 订单行作为可选补充
                var olq = em.createNativeQuery(
                        "SELECT ol.id, ol.seq, ol.product_id, p.code AS product_code, p.name_cn AS product_name, " +
                        "ol.qty, ol.unit_price, ol.sub_total " +
                        "FROM order_lines ol LEFT JOIN products p ON p.id = ol.product_id " +
                        "WHERE ol.order_id = ?1 ORDER BY ol.seq, ol.id", Tuple.class);
                olq.setParameter(1, srcId);
                @SuppressWarnings("unchecked")
                List<Tuple> pols = olq.getResultList();
                List<Map<String, Object>> soLines = new ArrayList<>();
                for (Tuple pol : pols) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", pol.get("id"));
                    m.put("seq", pol.get("seq"));
                    m.put("productId", pol.get("product_id"));
                    m.put("productCode", pol.get("product_code"));
                    m.put("productName", pol.get("product_name"));
                    m.put("qty", pol.get("qty"));
                    m.put("unitPrice", pol.get("unit_price"));
                    m.put("subtotal", pol.get("sub_total"));
                    soLines.add(m);
                }
                head.put("soLines", soLines);
            } catch (Exception ignored) {}
        }
        // 发货子单（每次发货一张，独立草稿/确认/取消）
        try {
            var bq = em.createNativeQuery(
                    "SELECT b.id, b.code, b.seq, b.status, b.remark, b.created_at, b.confirmed_at, b.cancelled_at, b.cancel_reason, " +
                    "       u1.name AS confirmed_by_name, u2.name AS cancelled_by_name " +
                    "FROM sales_out_batches b " +
                    "LEFT JOIN users u1 ON u1.id = b.confirmed_by " +
                    "LEFT JOIN users u2 ON u2.id = b.cancelled_by " +
                    "WHERE b.sales_out_id = ?1 AND b.tenant_id = ?2 ORDER BY b.seq", Tuple.class);
            bq.setParameter(1, id); bq.setParameter(2, tid);
            @SuppressWarnings("unchecked")
            List<Tuple> bts = bq.getResultList();
            List<Map<String, Object>> batches = new ArrayList<>();
            for (Tuple bt : bts) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("id", bt.get("id"));
                b.put("code", bt.get("code"));
                b.put("seq", bt.get("seq"));
                b.put("status", bt.get("status"));
                b.put("remark", bt.get("remark"));
                b.put("createdAt", com.dms.common.util.DateFmt.fmt(bt.get("created_at")));
                b.put("confirmedAt", com.dms.common.util.DateFmt.fmt(bt.get("confirmed_at")));
                b.put("confirmedByName", bt.get("confirmed_by_name"));
                b.put("cancelledAt", com.dms.common.util.DateFmt.fmt(bt.get("cancelled_at")));
                b.put("cancelReason", bt.get("cancel_reason"));
                Long bId = ((Number) bt.get("id")).longValue();
                var blq = em.createNativeQuery(
                        "SELECT bl.id, bl.expected_line_id, bl.expected_line_seq, bl.ship_line_no, bl.product_id, bl.warehouse_id, bl.qty, " +
                        "       bl.stock_batch_id, bl.batch_no, bl.serial_no, bl.unit_price, " +
                        "       p.name_cn AS product_name, p.code AS product_code, p.is_serial_managed " +
                        "FROM sales_out_batch_lines bl LEFT JOIN products p ON p.id = bl.product_id " +
                        "WHERE bl.batch_id = ?1 ORDER BY bl.ship_line_no, bl.id", Tuple.class);
                blq.setParameter(1, bId);
                @SuppressWarnings("unchecked")
                List<Tuple> bls = blq.getResultList();
                List<Map<String, Object>> blines = new ArrayList<>();
                for (Tuple bl : bls) {
                    Map<String, Object> lm = new LinkedHashMap<>();
                    lm.put("id", bl.get("id"));
                    lm.put("expectedLineId", bl.get("expected_line_id"));
                    lm.put("expectedLineSeq", bl.get("expected_line_seq"));
                    lm.put("shipLineNo", bl.get("ship_line_no"));
                    lm.put("productId", bl.get("product_id"));
                    lm.put("warehouseId", bl.get("warehouse_id"));
                    lm.put("productName", bl.get("product_name"));
                    lm.put("productCode", bl.get("product_code"));
                    lm.put("isSerialManaged", bl.get("is_serial_managed"));
                    lm.put("qty", bl.get("qty"));
                    lm.put("stockBatchId", bl.get("stock_batch_id"));
                    lm.put("batchNo", bl.get("batch_no"));
                    lm.put("serialNo", bl.get("serial_no"));
                    lm.put("unitPrice", bl.get("unit_price"));
                    blines.add(lm);
                }
                b.put("lines", blines);
                batches.add(b);
            }
            head.put("batches", batches);
        } catch (Exception ignored) {}
        return ApiResponse.ok(head);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> receiptDetail(Long id) {
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
                        "SELECT po.code, po.status, po.order_type, po.supplier_id, po.warehouse_id, " +
                        "po.amount_incl_tax, po.discount_amount, po.final_amount, po.tax_amount, " +
                        "po.expected_date, po.remark, po.submitted_at, po.approved_at, po.approved_by, " +
                        "po.created_at, po.created_by, " +
                        "s.name AS supplier_name, w.name AS warehouse_name, " +
                        "u1.name AS created_by_name, u2.name AS approved_by_name " +
                        "FROM purchase_orders po " +
                        "LEFT JOIN suppliers s ON s.id = po.supplier_id " +
                        "LEFT JOIN warehouses w ON w.id = po.warehouse_id " +
                        "LEFT JOIN users u1 ON u1.id = po.created_by " +
                        "LEFT JOIN users u2 ON u2.id = po.approved_by " +
                        "WHERE po.id = ?1", Tuple.class);
                srcQ.setParameter(1, ((Number) poId).longValue());
                List<?> srs = srcQ.getResultList();
                if (!srs.isEmpty()) {
                    Tuple s = (Tuple) srs.get(0);
                    Map<String, Object> src = new LinkedHashMap<>();
                    src.put("id", ((Number) poId).longValue());
                    src.put("code", s.get("code"));
                    src.put("status", s.get("status"));
                    src.put("orderType", s.get("order_type"));
                    src.put("supplierId", s.get("supplier_id"));
                    src.put("supplierName", s.get("supplier_name"));
                    src.put("warehouseId", s.get("warehouse_id"));
                    src.put("warehouseName", s.get("warehouse_name"));
                    src.put("amountInclTax", s.get("amount_incl_tax"));
                    src.put("discountAmount", s.get("discount_amount"));
                    src.put("finalAmount", s.get("final_amount"));
                    src.put("taxAmount", s.get("tax_amount"));
                    src.put("expectedDate", com.dms.common.util.DateFmt.fmt(s.get("expected_date")));
                    src.put("remark", s.get("remark"));
                    src.put("submittedAt", com.dms.common.util.DateFmt.fmt(s.get("submitted_at")));
                    src.put("approvedAt", com.dms.common.util.DateFmt.fmt(s.get("approved_at")));
                    src.put("approvedByName", s.get("approved_by_name"));
                    src.put("createdAt", com.dms.common.util.DateFmt.fmt(s.get("created_at")));
                    src.put("createdByName", s.get("created_by_name"));
                    head.put("sourcePo", src);
                }
            } catch (Exception ignored) {}
        }
        
        // v3.7.4 R3-R6: 加载子单 (batches) + 子单明细
        try {
            var bq = em.createNativeQuery(
                    "SELECT b.id, b.code, b.seq, b.status, b.remark, b.created_at, b.confirmed_at, b.cancelled_at, b.cancel_reason " +
                    "FROM receipt_batches b WHERE b.receipt_id = ?1 AND b.tenant_id = ?2 ORDER BY b.seq", Tuple.class);
            bq.setParameter(1, id).setParameter(2, tid);
            @SuppressWarnings("unchecked")
            List<Tuple> bts = bq.getResultList();
            List<Map<String, Object>> batches = new ArrayList<>();
            for (Tuple bt : bts) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("id", bt.get("id"));
                b.put("code", bt.get("code"));
                b.put("seq", bt.get("seq"));
                b.put("status", bt.get("status"));
                b.put("remark", bt.get("remark"));
                b.put("createdAt", com.dms.common.util.DateFmt.fmt(bt.get("created_at")));
                b.put("confirmedAt", com.dms.common.util.DateFmt.fmt(bt.get("confirmed_at")));
                b.put("cancelledAt", com.dms.common.util.DateFmt.fmt(bt.get("cancelled_at")));
                b.put("cancelReason", bt.get("cancel_reason"));
                Long bId = ((Number) bt.get("id")).longValue();
                var blq = em.createNativeQuery(
                        "SELECT bl.id, bl.po_line_id, bl.po_line_seq, bl.receipt_line_no, bl.product_id, bl.qty, bl.batch_no, bl.serial_nos, " +
                        "       p.name_cn AS product_name, p.code AS product_code, p.is_serial_managed " +
                        "FROM receipt_batch_lines bl LEFT JOIN products p ON p.id = bl.product_id " +
                        "WHERE bl.batch_id = ?1 ORDER BY bl.receipt_line_no", Tuple.class);
                blq.setParameter(1, bId);
                @SuppressWarnings("unchecked")
                List<Tuple> bls = blq.getResultList();
                List<Map<String, Object>> blines = new ArrayList<>();
                for (Tuple bl : bls) {
                    Map<String, Object> lm = new LinkedHashMap<>();
                    lm.put("id", bl.get("id"));
                    lm.put("poLineId", bl.get("po_line_id"));
                    lm.put("poLineSeq", bl.get("po_line_seq"));
                    lm.put("receiptLineNo", bl.get("receipt_line_no"));
                    lm.put("productId", bl.get("product_id"));
                    lm.put("productName", bl.get("product_name"));
                    lm.put("productCode", bl.get("product_code"));
                    lm.put("isSerialManaged", bl.get("is_serial_managed"));
                    lm.put("qty", bl.get("qty"));
                    lm.put("batchNo", bl.get("batch_no"));
                    lm.put("serialNos", bl.get("serial_nos"));
                    blines.add(lm);
                }
                b.put("lines", blines);
                batches.add(b);
            }
            head.put("batches", batches);
        } catch (Exception ignored) {}
        // v3.7.4: 加载 PO lines (子单选料时用)
        if (poId != null) {
            try {
                var polQ = em.createNativeQuery(
                        "SELECT pol.id, pol.seq, pol.product_id, pol.qty, pol.received_qty, pol.unit_price, " +
                        "       p.name_cn AS product_name, p.code AS product_code, p.is_serial_managed " +
                        "FROM purchase_order_lines pol LEFT JOIN products p ON p.id = pol.product_id " +
                        "WHERE pol.po_id = ?1 ORDER BY pol.seq, pol.id", Tuple.class);
                polQ.setParameter(1, ((Number) poId).longValue());
                @SuppressWarnings("unchecked")
                List<Tuple> pols = polQ.getResultList();
                List<Map<String, Object>> poLines = new ArrayList<>();
                for (Tuple pol : pols) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", pol.get("id"));
                    m.put("seq", pol.get("seq"));
                    m.put("productId", pol.get("product_id"));
                    m.put("productName", pol.get("product_name"));
                    m.put("productCode", pol.get("product_code"));
                    m.put("isSerialManaged", pol.get("is_serial_managed"));
                    m.put("qty", pol.get("qty"));
                    m.put("receivedQty", pol.get("received_qty"));
                    m.put("unitPrice", pol.get("unit_price"));
                    poLines.add(m);
                }
                head.put("poLines", poLines);
            } catch (Exception ignored) {}

            try {
                var rlq = em.createNativeQuery(
                        "SELECT expected_qty, received_qty, cancelled_qty FROM receipt_lines WHERE receipt_id = ?1", Tuple.class);
                rlq.setParameter(1, id);
                @SuppressWarnings("unchecked")
                List<Tuple> rls = rlq.getResultList();
                java.math.BigDecimal expectedTotal = java.math.BigDecimal.ZERO;
                java.math.BigDecimal receivedTotal = java.math.BigDecimal.ZERO;
                java.math.BigDecimal cancelledTotal = java.math.BigDecimal.ZERO;
                for (Tuple rl : rls) {
                    expectedTotal = expectedTotal.add(toBd(rl.get("expected_qty")));
                    receivedTotal = receivedTotal.add(toBd(rl.get("received_qty")));
                    cancelledTotal = cancelledTotal.add(toBd(rl.get("cancelled_qty")));
                }
                head.put("totalExpected", expectedTotal);
                head.put("totalReceived", receivedTotal);
                head.put("totalCancelled", cancelledTotal);
                head.put("totalRemaining", expectedTotal.subtract(receivedTotal).subtract(cancelledTotal).max(java.math.BigDecimal.ZERO));
            } catch (Exception ignored) {}
        }
        
        return ApiResponse.ok(head);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> orderDetail(Long id) {
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> poDetail(Long id) {
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
        head.put("allowedActions", poAllowedActions(String.valueOf(head.get("status"))));
        return ApiResponse.ok(head);
    }

    private java.util.List<java.util.Map<String, Object>> poAllowedActions(String status) {
        java.util.List<java.util.Map<String, Object>> actions = new java.util.ArrayList<>();
        if ("DRAFT".equals(status)) {
            actions.add(poAct("edit", "编辑", "primary", "PUT", ""));
            actions.add(poAct("submit", "提交审批", "warning", "POST", "/submit"));
            actions.add(poAct("cancel", "取消", "danger", "POST", "/cancel"));
        } else if ("SUBMITTED".equals(status)) {
            actions.add(poAct("approve", "审批通过", "success", "POST", "/approve"));
            actions.add(poAct("reject", "驳回", "danger", "POST", "/reject"));
        } else if ("APPROVED".equals(status)) {
            actions.add(poAct("cancel", "取消", "warning", "POST", "/cancel"));
            actions.add(poAct("receive", "收货入库", "success", "POST", "/receive"));
        } else if ("RECEIVING".equals(status)) {
            actions.add(poAct("receive", "继续收货", "success", "POST", "/receive"));
        }
        return actions;
    }

    private java.util.Map<String, Object> poAct(String key, String label, String type, String method, String path) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("key", key); m.put("label", label); m.put("type", type);
        m.put("method", method); m.put("path", path);
        return m;
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

    private java.math.BigDecimal toBd(Object v) {
        if (v == null) return java.math.BigDecimal.ZERO;
        return new java.math.BigDecimal(String.valueOf(v));
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
