package com.dms.order.service;

import com.dms.annotation.OperationLog;
import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.ContentDispositionUtils;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.PagingUtil;
import com.dms.common.util.TenantContext;
import com.dms.order.service.support.ActionButtonSupport;
import com.dms.order.service.support.ApprovalResponseSupport;
import com.dms.execution.service.AutoDocGenerator;
import com.dms.v4.V4Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SalesReturnService {
    private final EntityManager em;
    private final AutoDocGenerator autoDocGenerator;
    private final com.dms.common.util.DocNoGenerator docNoGenerator;
    private final ApprovalService approvalService;
    private final com.dms.order.service.SalesReturnLineSupport lineSupport;
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            int page,
            int size,
            String status,
            Long dealerId,
            Long warehouseId,
            String createdAtFrom,
            String createdAtTo,
            String updatedAtFrom,
            String updatedAtTo,
            String finalAmountFrom,
            String finalAmountTo,
            String code,
            String reasonCode,
            String sort) {
        UUID tid = TenantContext.getTenantId();
        int safePage = PagingUtil.normalizePage(page); int safeSize = PagingUtil.normalizeSize(size); int offset = (safePage - 1) * safeSize;
        StringBuilder where = new StringBuilder(" WHERE o.tenant_id = ?1 AND o.deleted_at IS NULL AND COALESCE(o.is_red,false) = true");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (status != null && !status.isBlank()) { where.append(" AND o.status = ?").append(idx++); params.add(status); }
        if (dealerId != null) { where.append(" AND o.dealer_id = ?").append(idx++); params.add(dealerId); }
        if (warehouseId != null) { where.append(" AND o.warehouse_id = ?").append(idx++); params.add(warehouseId); }
        if (createdAtFrom != null && !createdAtFrom.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(createdAtFrom, true); if (__t != null) { where.append(" AND o.created_at >= ?").append(idx++); params.add(__t); } }
        if (createdAtTo != null && !createdAtTo.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(createdAtTo, false); if (__t != null) { where.append(com.dms.common.util.SpecUtil.hasTime(createdAtTo) ? " AND o.created_at <= ?" : " AND o.created_at < ?").append(idx++); params.add(__t); } }
        if (updatedAtFrom != null && !updatedAtFrom.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(updatedAtFrom, true); if (__t != null) { where.append(" AND o.updated_at >= ?").append(idx++); params.add(__t); } }
        if (updatedAtTo != null && !updatedAtTo.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(updatedAtTo, false); if (__t != null) { where.append(com.dms.common.util.SpecUtil.hasTime(updatedAtTo) ? " AND o.updated_at <= ?" : " AND o.updated_at < ?").append(idx++); params.add(__t); } }
        if (finalAmountFrom != null && !finalAmountFrom.isBlank()) { where.append(" AND o.final_amount >= ?").append(idx++); params.add(new java.math.BigDecimal(finalAmountFrom)); }
        if (finalAmountTo != null && !finalAmountTo.isBlank()) { where.append(" AND o.final_amount <= ?").append(idx++); params.add(new java.math.BigDecimal(finalAmountTo)); }
        if (code != null && !code.isBlank()) { where.append(" AND o.code ILIKE ?").append(idx++); params.add("%" + code.trim() + "%"); }
        if (reasonCode != null && !reasonCode.isBlank()) { where.append(" AND o.reason_code = ?").append(idx++); params.add(reasonCode); }

        var qCnt = em.createNativeQuery("SELECT COUNT(*) FROM orders o " + where);
        for (int i = 0; i < params.size(); i++) qCnt.setParameter(i + 1, params.get(i));
        long total = ((Number) qCnt.getSingleResult()).longValue();

        String orderSql = " ORDER BY o.created_at DESC";
        if (sort != null && !sort.isBlank()) {
            String[] sp = sort.split(",");
            String f = sp[0].trim();
            String dir = sp.length > 1 && "asc".equalsIgnoreCase(sp[1].trim()) ? "ASC" : "DESC";
            switch (f) {
                case "updatedAt" -> orderSql = " ORDER BY o.updated_at " + dir;
                case "createdAt" -> orderSql = " ORDER BY o.created_at " + dir;
                case "finalAmount" -> orderSql = " ORDER BY o.final_amount " + dir;
                case "amountInclTax" -> orderSql = " ORDER BY o.amount_incl_tax " + dir;
                case "code" -> orderSql = " ORDER BY o.code " + dir;
                default -> { }
            }
        }
        String limitParam = "?" + idx++;
        String offsetParam = "?" + idx++;
        var q = em.createNativeQuery(
                "SELECT o.id, o.code, o.order_type, o.dealer_id, o.warehouse_id, o.ref_order_id, o.ref_sales_out_id, o.return_reason, o.reason_code, " +
                "COALESCE(NULLIF(CAST(o.ship_snapshot AS jsonb)->>'dealerName',''), d.name) AS dealer_name, " +
                "w.name AS warehouse_name, u.name AS audit_user_name, o.approved_at, o.submitted_at, o.cancelled_at, " +
                "o.amount_incl_tax, o.final_amount, o.tax_amount, o.expected_date, o.status, o.remark, o.created_at, o.updated_at " +
                "FROM orders o LEFT JOIN dealers d ON d.id=o.dealer_id " +
                "LEFT JOIN warehouses w ON w.id=o.warehouse_id LEFT JOIN users u ON u.id=o.approved_by " +
                where + orderSql + " LIMIT " + limitParam + " OFFSET " + offsetParam, Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(params.size() + 1, safeSize);
        q.setParameter(params.size() + 2, offset);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toBrief(t));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total); data.put("page", safePage); data.put("size", safeSize); data.put("list", list);
        return ApiResponse.ok(data);
    }
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(String status,
                                         Long dealerId,
                                         Long warehouseId,
                                         String reasonCode) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "SELECT o.id, o.code, o.order_type, o.dealer_id, d.name AS dealer_name, o.warehouse_id, w.name AS warehouse_name, " +
                "o.ref_order_id, o.ref_sales_out_id, o.amount_incl_tax, o.final_amount, o.return_reason, o.reason_code, o.status, o.expected_date, o.created_at " +
                "FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id " +
                "LEFT JOIN warehouses w ON w.id = o.warehouse_id " +
                "WHERE o.tenant_id = ?1 AND o.deleted_at IS NULL AND COALESCE(o.is_red,true) = true ");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (status != null && !status.isBlank()) { sql.append(" AND o.status = ?").append(idx++); params.add(status); }
        if (dealerId != null) { sql.append(" AND o.dealer_id = ?").append(idx++); params.add(dealerId); }
        if (warehouseId != null) { sql.append(" AND o.warehouse_id = ?").append(idx++); params.add(warehouseId); }
        if (reasonCode != null && !reasonCode.isBlank()) { sql.append(" AND o.reason_code = ?").append(idx++); params.add(reasonCode); }
        sql.append(" ORDER BY o.created_at DESC");
        var q = em.createNativeQuery(sql.toString(), Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toBrief(t));
        String[] headers = {"ID", "销退单号", "订单类型", "经销商ID", "经销商", "仓库ID", "收货仓库", "金额", "退货原因", "原因编码", "状态", "期望收货日期", "创建时间"};
        String[] fields = {"id", "code", "orderType", "dealerId", "dealerName", "warehouseId", "warehouseName", "finalAmount", "returnReason", "reasonCode", "status", "expectedDate", "createdAt"};
        byte[] excel;
        try {
            excel = ExcelExportUtils.exportMapToExcel(list, headers, fields);
        } catch (Exception e) {
            throw new RuntimeException("导出销退单失败: " + e.getMessage(), e);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("销退订单列表.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> get(Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT o.*, d.name AS dealer_name, w.name AS warehouse_name, " +
                "so.code AS ref_sales_out_code, so.status AS source_sales_out_status, so.source_order_id AS ref_sales_out_order_id, o0.code AS ref_order_code " +
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
        data.put("sourceSalesOutId", t.get("ref_sales_out_id"));
        data.put("sourceSalesOutCode", t.get("ref_sales_out_code"));
        data.put("returnReason", t.get("return_reason"));
        try { data.put("reasonCode", t.get("reason_code")); } catch (Exception ignored) {}
        data.put("remark", t.get("remark"));
        data.put("status", t.get("status"));
        data.put("amountInclTax", t.get("amount_incl_tax"));
        data.put("finalAmount", t.get("final_amount"));
        try { data.put("sourceSalesOutStatus", t.get("source_sales_out_status")); } catch (Exception ignored) {}
        data.put("taxAmount", t.get("tax_amount"));
        data.put("expectedDate", t.get("expected_date"));
        try { data.put("submittedAt", t.get("submitted_at")); } catch (Exception ignored) {}
        try { data.put("approvedAt", t.get("approved_at")); } catch (Exception ignored) {}
        try { data.put("cancelledAt", t.get("cancelled_at")); } catch (Exception ignored) {}
        try { data.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at"))); } catch (Exception ignored) {}

        var lq = em.createNativeQuery(
                "SELECT ol.id, ol.seq, ol.product_id, ol.qty, ol.unit_price, ol.tax_rate, ol.sub_total, ol.final_amount, ol.batch_no, ol.serial_no, ol.extra, " +
                "p.code AS p_code, p.name_cn AS p_name, p.spec AS p_spec, p.unit AS p_unit, p.is_serial_managed, " +
                "COALESCE(sol.shipped_qty, sol.qty, 0) AS shipped_qty, COALESCE(sol.returned_qty,0) AS returned_qty, COALESCE(sol.return_locked_qty,0) AS locked_qty, " +
                "GREATEST(COALESCE(sol.return_locked_qty,0) - COALESCE((SELECT SUM(olx.qty) FROM order_lines olx WHERE olx.order_id=ol.order_id AND CAST(COALESCE(olx.extra->>'sourceOutLineId','0') AS bigint)=sol.id),0),0) AS other_locked_qty, " +
                "sol.seq AS source_line_no, ol2.seq AS order_line_no " +
                "FROM order_lines ol LEFT JOIN products p ON p.id=ol.product_id " +
                "LEFT JOIN sales_out_lines sol ON sol.id = CAST(COALESCE(ol.extra->>'sourceOutLineId','0') AS bigint) " +
                "LEFT JOIN order_lines ol2 ON ol2.id = sol.source_order_line_id " +
                "WHERE ol.order_id=?1 ORDER BY ol.seq, ol.id", Tuple.class);
        lq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> lrows = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : lrows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.get("id")); m.put("seq", l.get("seq"));
            m.put("id", l.get("id"));
            m.put("sourceOutLineId", lineSupport.sourceOutLineId(l.get("extra"), l.get("id")));
            m.put("lineNo", l.get("source_line_no"));
            m.put("orderLineNo", l.get("order_line_no"));
            m.put("productId", l.get("product_id"));
            m.put("productCode", l.get("p_code")); m.put("productName", l.get("p_name"));
            m.put("productSpec", l.get("p_spec")); m.put("unit", l.get("p_unit"));
            m.put("batchNo", l.get("batch_no")); m.put("serialNo", l.get("serial_no"));
            m.put("qty", l.get("qty")); m.put("unitPrice", l.get("unit_price"));
            m.put("taxRate", l.get("tax_rate")); m.put("subtotal", l.get("sub_total"));
            m.put("finalAmount", l.get("final_amount"));
            m.put("shippedQty", l.get("shipped_qty"));
            m.put("returnedQty", l.get("returned_qty"));
            m.put("lockedQty", l.get("locked_qty"));
            BigDecimal otherLocked = lineSupport.toBd(l.get("other_locked_qty"));
            m.put("otherLockedQty", otherLocked);
            // returnable = shipped - returned - locked by OTHER rmas (this RMA's own lines must not be subtracted,
            // otherwise a submitted RMA shows 0 returnable for its own lines)
            m.put("returnableQty", lineSupport.toBd(l.get("shipped_qty")).subtract(lineSupport.toBd(l.get("returned_qty"))).subtract(otherLocked).max(BigDecimal.ZERO));
            m.put("isSerialManaged", l.get("is_serial_managed"));
            lines.add(m);
        }
        data.put("lines", lines);
        data.put("allowedActions", allowedActions(String.valueOf(data.get("status"))));
        return ApiResponse.ok(data);
    }
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> shippedOuts(
            Long orderId,
            Long dealerId,
            Long warehouseId,
            String startDate,
            String endDate,
            String keyword,
            String batchNo,
            String serialNo,
            Long productId) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "SELECT so.id, so.code, so.dealer_id, so.warehouse_id, so.source_order_id, so.status, COALESCE(so.sales_date, so.shipped_at, so.created_at) AS sales_date, " +
                "d.name AS dealer_name, w.name AS warehouse_name, o.code AS order_code " +
                "FROM sales_outs so LEFT JOIN dealers d ON d.id=so.dealer_id " +
                "LEFT JOIN warehouses w ON w.id=so.warehouse_id LEFT JOIN orders o ON o.id=so.source_order_id " +
                "WHERE so.tenant_id=? AND so.deleted_at IS NULL AND COALESCE(so.is_red,false)=false " +
                "AND so.status IN ('COMPLETED','PARTIAL_OUTBOUND','PARTIAL_SHIPPED','SHIPPED') " +
                "AND EXISTS (SELECT 1 FROM sales_out_lines sl WHERE sl.sales_out_id=so.id " +
                "AND (COALESCE(sl.shipped_qty,sl.qty,0) - COALESCE(sl.return_locked_qty,0) - COALESCE(sl.returned_qty,0)) > 0)");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        if (orderId != null) { sql.append(" AND so.source_order_id = ?"); params.add(orderId); }
        if (dealerId != null) { sql.append(" AND so.dealer_id = ?"); params.add(dealerId); }
        if (warehouseId != null) { sql.append(" AND so.warehouse_id = ?"); params.add(warehouseId); }
        if (startDate != null && !startDate.isBlank()) { sql.append(" AND COALESCE(so.sales_date, so.shipped_at, so.created_at) >= ?"); params.add(java.sql.Date.valueOf(startDate)); }
        if (endDate != null && !endDate.isBlank()) { sql.append(" AND COALESCE(so.sales_date, so.shipped_at, so.created_at) <= ?"); params.add(java.sql.Date.valueOf(endDate)); }
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND (o.code ILIKE ? OR so.code ILIKE ? OR d.name ILIKE ?)"); String kw="%"+keyword+"%"; params.add(kw); params.add(kw); params.add(kw); }
        if (batchNo != null && !batchNo.isBlank()) { sql.append(" AND EXISTS (SELECT 1 FROM sales_out_lines sl WHERE sl.sales_out_id=so.id AND sl.batch_no ILIKE ?)"); params.add("%"+batchNo+"%"); }
        if (serialNo != null && !serialNo.isBlank()) { sql.append(" AND EXISTS (SELECT 1 FROM sales_out_lines sl WHERE sl.sales_out_id=so.id AND sl.serial_no ILIKE ?)"); params.add("%"+serialNo+"%"); }
        if (productId != null) { sql.append(" AND EXISTS (SELECT 1 FROM sales_out_lines sl WHERE sl.sales_out_id=so.id AND sl.product_id=?)"); params.add(productId); }
        sql.append(" ORDER BY so.sales_date DESC, so.id DESC LIMIT 200");
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
            m.put("salesDate", com.dms.common.util.DateFmt.fmt(t.get("sales_date"))); m.put("status", t.get("status"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> shippedOutLines(Long salesOutId) {
        UUID tid = TenantContext.getTenantId();
        var hq = em.createNativeQuery(
                "SELECT id, code, dealer_id, warehouse_id, source_order_id FROM sales_outs WHERE id=?1 AND tenant_id=?2", Tuple.class);
        hq.setParameter(1, salesOutId).setParameter(2, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> hr = hq.getResultList();
        if (hr.isEmpty()) return ApiResponse.fail(40404, "发货单不存在");
        Tuple h = hr.get(0);

        var lq = em.createNativeQuery(
                "SELECT sol.id, sol.seq, sol.product_id, sol.batch_no, sol.serial_no, sol.source_order_line_id, " +
                "COALESCE(sol.shipped_qty, sol.qty, 0) AS shipped_qty, COALESCE(sol.return_locked_qty,0) AS locked_qty, COALESCE(sol.returned_qty,0) AS returned_qty, sol.unit_price, sol.tax_rate, sol.final_amount, " +
                "p.code AS p_code, p.name_cn AS p_name, p.spec AS p_spec, p.unit AS p_unit, p.is_serial_managed, " +
                "COALESCE(ol.is_gift,false) AS is_gift, COALESCE(ol.line_level,'NORMAL') AS line_level, ol.seq AS order_line_no " +
                "FROM sales_out_lines sol LEFT JOIN products p ON p.id=sol.product_id " +
                "LEFT JOIN order_lines ol ON ol.id = sol.source_order_line_id " +
                "WHERE sol.sales_out_id=?1 ORDER BY sol.seq, sol.id", Tuple.class);
        lq.setParameter(1, salesOutId);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : rows) {
            BigDecimal shipped = new BigDecimal(String.valueOf(l.get("shipped_qty")));
            BigDecimal locked = lineSupport.toBd(l.get("locked_qty"));
            BigDecimal returned = lineSupport.toBd(l.get("returned_qty"));
            String lineLevel = l.get("line_level") == null ? "NORMAL" : String.valueOf(l.get("line_level"));
            // v4.1.6: physical gifts (is_gift=true) are shipped in sales-out_lines and must be
            // returnable too. Still skip BOM parent (PARENT, no physical product).
            if ("PARENT".equals(lineLevel)) continue;
            BigDecimal returnable = shipped.subtract(locked).subtract(returned);
            if (returnable.signum() < 0) returnable = BigDecimal.ZERO;
            if (returnable.signum() == 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.get("id"));
            m.put("sourceOutLineId", l.get("id"));
            m.put("lineNo", l.get("seq"));
            m.put("orderLineNo", l.get("order_line_no"));
            m.put("productId", l.get("product_id"));
            m.put("productCode", l.get("p_code")); m.put("productName", l.get("p_name"));
            m.put("productSpec", l.get("p_spec")); m.put("unit", l.get("p_unit"));
            m.put("batchNo", l.get("batch_no")); m.put("serialNo", l.get("serial_no"));
            m.put("isSerialManaged", l.get("is_serial_managed"));
            m.put("shippedQty", shipped);
            m.put("lockedQty", locked);
            m.put("returnedQty", returned);
            m.put("returnableQty", returnable);
            m.put("qty", returnable);
            // Prefer the stored unit_price so gift lines (0.00) are returned accurately.
            m.put("unitPrice", lineSupport.toBd(l.get("unit_price")));
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
    @OperationLog(businessType = "salesReturn", action = OperationAction.CREATE, remark = "销退订单-创建")
    @Transactional
    public ApiResponse<Map<String, Object>> create(Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (body.get("dealerId") == null) return ApiResponse.fail(40001, "经销商不能为空");
        Long refSalesOutId = lineSupport.firstLong(body, "refSalesOutId", "sourceSalesOutId");
        if (refSalesOutId == null) return ApiResponse.fail(40001, "必须关联已发货的发货单");
        String reasonText = lineSupport.firstString(body, "returnReason", "reason");
        String reasonCode = lineSupport.firstString(body, "reasonCode");
        if ((reasonText == null || reasonText.isBlank()) && (reasonCode == null || reasonCode.isBlank()))
            return ApiResponse.fail(40001, "退货原因不能为空");

        var soQ = em.createNativeQuery(
                "SELECT id, source_order_id, dealer_id, warehouse_id, status FROM sales_outs WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=false", Tuple.class);
        soQ.setParameter(1, refSalesOutId).setParameter(2, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> soRs = soQ.getResultList();
        if (soRs.isEmpty()) return ApiResponse.fail(40001, "关联的发货单不存在");
        Tuple so = soRs.get(0);
        if (!"PARTIAL_SHIPPED".equals(so.get("status")) && !"COMPLETED".equals(so.get("status")))
            return ApiResponse.fail(40001, "只能关联已发货(部分发货/已完成)的发货单");

        Long warehouseId = lineSupport.firstLong(body, "warehouseId");
        if (warehouseId == null) warehouseId = lineSupport.toLong(so.get("warehouse_id"));

        String code = docNoGenerator.next("RS");
        String validate = validateReturnLines(tid, refSalesOutId, body);
        if (validate != null) return ApiResponse.fail(40001, validate);
        BigDecimal total = calcTotal(refSalesOutId, body);

        var insert = em.createNativeQuery(
                "INSERT INTO orders (tenant_id, code, order_type, is_red, dealer_id, warehouse_id, ref_order_id, ref_sales_out_id, return_reason, reason_code, " +
                "ship_snapshot, amount_incl_tax, discount_amount, final_amount, tax_amount, expected_date, status, remark, extra, created_at, updated_at, created_by) " +
                "VALUES (?1, ?2, 'RETURN', true, ?3, ?4, ?5, ?6, ?7, ?8, CAST(?9 AS jsonb), ?10, 0, ?10, 0, CAST(?11 AS date), 'DRAFT', ?12, CAST(?13 AS jsonb), now(), now(), ?14) RETURNING id");
        insert.setParameter(1, tid).setParameter(2, code)
              .setParameter(3, lineSupport.toLong(body.get("dealerId")))
              .setParameter(4, warehouseId)
              .setParameter(5, so.get("source_order_id"))
              .setParameter(6, refSalesOutId)
              .setParameter(7, reasonText)
              .setParameter(8, reasonCode)
              .setParameter(9, "{}")
              .setParameter(10, total)
              .setParameter(11, body.get("expectedDate"))
              .setParameter(12, body.getOrDefault("remark", ""))
              .setParameter(13, "{}")
              .setParameter(14, TenantContext.getUserId());
        Long id = ((Number) insert.getSingleResult()).longValue();

        insertLines(id, refSalesOutId, body);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("code", code);
        return ApiResponse.ok(res);
    }
    @OperationLog(businessType = "salesReturn", action = OperationAction.UPDATE, remark = "销退订单-更新")
    @Transactional
    public ApiResponse<Map<String, Object>> update(Long id, Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "销退订单不存在");
        if (!List.of("DRAFT","REJECTED").contains(status)) return ApiResponse.fail(40009, "仅草稿或驳回状态可编辑，当前状态: " + status);

        Long refSalesOutId = lineSupport.firstLong(body, "refSalesOutId", "sourceSalesOutId");
        String validate = validateReturnLines(tid, refSalesOutId, body);
        if (validate != null) return ApiResponse.fail(40001, validate);

        Long warehouseId = lineSupport.firstLong(body, "warehouseId");
        BigDecimal total = calcTotal(refSalesOutId, body);
        em.createNativeQuery(
                "UPDATE orders SET dealer_id=?1, warehouse_id=?2, ref_sales_out_id=?3, return_reason=?4, reason_code=?5, " +
                "amount_incl_tax=?6, final_amount=?6, expected_date=CAST(?7 AS date), remark=?8, updated_at=now() WHERE id=?9 AND tenant_id=?10")
          .setParameter(1, lineSupport.toLong(body.get("dealerId"))).setParameter(2, warehouseId)
          .setParameter(3, refSalesOutId)
          .setParameter(4, lineSupport.firstString(body, "returnReason", "reason"))
          .setParameter(5, lineSupport.firstString(body, "reasonCode"))
          .setParameter(6, total).setParameter(7, body.get("expectedDate"))
          .setParameter(8, body.getOrDefault("remark", ""))
          .setParameter(9, id).setParameter(10, tid).executeUpdate();
        em.createNativeQuery("DELETE FROM order_lines WHERE order_id=?1").setParameter(1, id).executeUpdate();
        insertLines(id, refSalesOutId, body);
        return ApiResponse.ok(Map.of("id", id));
    }
    @OperationLog(businessType = "salesReturn", action = OperationAction.CREATE, remark = "销退订单-生成红字销售出库")
    @Transactional
    public ApiResponse<Map<String, Object>> createRedOut(Long id) {
        UUID tid = TenantContext.getTenantId();
        var rq = em.createNativeQuery("SELECT id,status,dealer_id,warehouse_id,final_amount,code FROM orders WHERE id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=true", Tuple.class)
                .setParameter(1,id).setParameter(2,tid).getResultList();
        if (rq.isEmpty()) return ApiResponse.fail(40404, "销退订单不存在");
        Tuple r = (Tuple) rq.get(0);
        String status = String.valueOf(r.get("status"));
        if (!List.of("APPROVED","RECEIVING","COMPLETED").contains(status)) return ApiResponse.fail(40009, "仅已审批销退订单可生成红字销售出库");
        Long dealerId = lineSupport.toLong(r.get("dealer_id"));
        Long warehouseId = lineSupport.toLong(r.get("warehouse_id"));
        var exists = em.createNativeQuery("SELECT id,code FROM sales_outs WHERE source_order_id=?1 AND tenant_id=?2 AND COALESCE(is_red,false)=true AND deleted_at IS NULL ORDER BY id DESC LIMIT 1", Tuple.class)
                .setParameter(1,id).setParameter(2,tid).getResultList();
        if (!exists.isEmpty()) {
            Tuple e = (Tuple) exists.get(0);
            return ApiResponse.ok(Map.of("id", e.get("id"), "code", e.get("code"), "existed", true));
        }
        String code = docNoGenerator.next("GIR");
        var ins = em.createNativeQuery(
                "INSERT INTO sales_outs (tenant_id,code,dealer_id,warehouse_id,is_red,status,auto_created,source_order_id,sales_date,amount_incl_tax,created_at,updated_at,created_by) " +
                "VALUES (?1,?2,?3,?4,true,'DRAFT',false,?5,CURRENT_DATE,?6,now(),now(),?7) RETURNING id", Tuple.class)
          .setParameter(1,tid).setParameter(2,code).setParameter(3,dealerId).setParameter(4,warehouseId).setParameter(5,id).setParameter(6,r.get("final_amount")).setParameter(7,TenantContext.getUserId());
        Tuple inserted = (Tuple) ins.getSingleResult();
        Long outId = ((Number) inserted.get("id")).longValue();
        var lines = em.createNativeQuery("SELECT product_id,product_code,product_name,product_spec,batch_no,serial_no,qty,unit_price,tax_rate,sub_total,extra FROM order_lines WHERE order_id=?1 ORDER BY seq,id", Tuple.class)
                .setParameter(1,id).getResultList();
        int seq=1;
        for (Object o : lines) {
            Tuple l = (Tuple) o;
            BigDecimal qty = lineSupport.toBd(l.get("qty"));
            BigDecimal price = lineSupport.toBd(l.get("unit_price")).negate();
            BigDecimal sub = lineSupport.toBd(l.get("sub_total")).negate();
            em.createNativeQuery(
                    "INSERT INTO sales_out_lines (sales_out_id,seq,product_id,warehouse_id,batch_no,serial_no,expected_qty,qty,unit_price,subtotal,created_at) " +
                    "VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,now())")
              .setParameter(1,outId).setParameter(2,seq++).setParameter(3,lineSupport.toLong(l.get("product_id"))).setParameter(4,warehouseId)
              .setParameter(5,l.get("batch_no")).setParameter(6,l.get("serial_no")).setParameter(7,qty).setParameter(8,qty).setParameter(9,price).setParameter(10,sub)
              .executeUpdate();
        }
        Map<String,Object> res = new LinkedHashMap<>();
        res.put("id", outId); res.put("code", code); res.put("existed", false);
        return ApiResponse.ok(res);
    }
    @OperationLog(businessType = "salesReturn", action = OperationAction.UPDATE, remark = "销退订单-提交审批")
    @Transactional
    public ApiResponse<Map<String, Object>> submit(Long id) {
        UUID tid = TenantContext.getTenantId();
        int n = em.createNativeQuery("UPDATE orders SET status='PENDING_APPROVAL', submitted_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2 AND status IN ('DRAFT','REJECTED') AND COALESCE(is_red,false)=true")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (n == 0) return ApiResponse.fail(40009, "Only draft sales return can be submitted");
        // Pre-check current returnable quantity per source out line and report a business-readable
        // message (product code/name + remaining qty) before locking. This catches stale drafts that
        // were created before another RMA locked the same line.
        String precheckError = checkReturnableBeforeSubmit(tid, id);
        if (precheckError != null) {
            em.createNativeQuery("UPDATE orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2").setParameter(1, id).setParameter(2, tid).executeUpdate();
            return ApiResponse.fail(40009, precheckError);
        }
        String lockError = lockReturnLines(tid, id, false);
        if (lockError != null) {
            em.createNativeQuery("UPDATE orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2").setParameter(1, id).setParameter(2, tid).executeUpdate();
            return ApiResponse.fail(40009, lockError);
        }
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
            return ApiResponse.ok(ApprovalResponseSupport.submitResult(id, instance, true));
        } catch (Exception e) {
            lockReturnLines(tid, id, true);
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
    @OperationLog(businessType = "salesReturn", action = OperationAction.APPROVE, remark = "销退订单-审批通过")
    @Transactional
    public ApiResponse<Map<String, Object>> approve(Long id) {
        ApprovalInstance instance = approvalService.approveBusiness("SALES_RETURN", id, null);
        em.createNativeQuery("UPDATE orders SET approved_by=?1 WHERE id=?2")
          .setParameter(1, TenantContext.getUserId()).setParameter(2, id).executeUpdate();
        return ApiResponse.ok(Map.of("id", id, "newStatus", "APPROVED".equals(instance.getStatus().name()) || "AUTO_APPROVED".equals(instance.getStatus().name()) ? "APPROVED" : "PENDING_APPROVAL", "approvalInstanceId", instance.getId()));
    }
    @Transactional
    public ApiResponse<Map<String, Object>> reject(Long id) {
        // Locks are released by SalesReturnApprovalCallback.onRejected (fired synchronously
        // inside rejectBusiness). Do NOT unlock again here or return_locked_qty would be
        // double-counted and corrupt returnable quantity for other RMAs.
        ApprovalInstance instance = approvalService.rejectBusiness("SALES_RETURN", id, null);
        return ApiResponse.ok(ApprovalResponseSupport.decisionResult(id, instance));
    }
    @OperationLog(businessType = "salesReturn", action = OperationAction.UPDATE, remark = "销退订单-取消")
    @Transactional
    public ApiResponse<Map<String, Object>> cancel(Long id) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "销退订单不存在");
        if (!List.of("DRAFT","PENDING_APPROVAL","REJECTED","APPROVED").contains(status))
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
        lockReturnLines(tid, id, true);
        em.createNativeQuery("UPDATE orders SET status='CANCELLED', cancelled_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        em.createNativeQuery(
                "UPDATE receipts SET status='CANCELLED', updated_at=now() WHERE ref_doc_type='sales_return' AND ref_doc_id=?1 AND tenant_id=?2 AND status IN ('DRAFT','APPROVED','PARTIAL_RECEIVED')")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        em.createNativeQuery(
                "UPDATE receipt_batches SET status='CANCELLED', cancelled_at=now(), updated_at=now() WHERE receipt_id IN " +
                "(SELECT id FROM receipts WHERE ref_doc_type='sales_return' AND ref_doc_id=?1 AND tenant_id=?2) AND status IN ('DRAFT','REJECTED')")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("status", "CANCELLED");
        return ApiResponse.ok(res);
    }
    @Transactional
    public ApiResponse<Map<String, Object>> delete(Long id) {
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
    private String checkReturnableBeforeSubmit(UUID tid, Long returnOrderId) {
        var rows = em.createNativeQuery(
                "SELECT CAST(COALESCE(ol.extra->>'sourceOutLineId','0') AS bigint) AS out_line_id, " +
                "       p.code AS p_code, p.name_cn AS p_name, SUM(ol.qty) AS qty " +
                "FROM order_lines ol LEFT JOIN products p ON p.id=ol.product_id " +
                "WHERE ol.order_id=?1 GROUP BY 1,2,3", Tuple.class)
                .setParameter(1, returnOrderId).getResultList();
        for (Object o : rows) {
            Tuple t = (Tuple) o;
            Long outLineId = lineSupport.toLong(t.get("out_line_id"));
            if (outLineId == null || outLineId == 0L) continue;
            BigDecimal need = lineSupport.toBd(t.get("qty"));
            var sol = em.createNativeQuery(
                    "SELECT COALESCE(shipped_qty,qty,0) AS shipped, COALESCE(return_locked_qty,0) AS locked, COALESCE(returned_qty,0) AS returned " +
                    "FROM sales_out_lines WHERE id=?1", Tuple.class).setParameter(1, outLineId).getResultList();
            if (sol.isEmpty()) return "原出库行不存在，请重新选择发货单明细";
            Tuple st = (Tuple) sol.get(0);
            BigDecimal available = lineSupport.toBd(st.get("shipped"))
                    .subtract(lineSupport.toBd(st.get("locked")))
                    .subtract(lineSupport.toBd(st.get("returned")));
            if (need.compareTo(available) > 0) {
                String code = t.get("p_code") == null ? "" : String.valueOf(t.get("p_code"));
                String name = t.get("p_name") == null ? "" : String.valueOf(t.get("p_name"));
                return "产品 [" + code + " " + name + "] 可退数量不足，当前可退 " + available.stripTrailingZeros().toPlainString()
                        + "，本单需退 " + need.stripTrailingZeros().toPlainString()
                        + "（可能已被其他审批中的销退单占用）";
            }
        }
        return null;
    }

    private String lockReturnLines(UUID tid, Long returnOrderId, boolean unlock) {
        var rows = em.createNativeQuery("SELECT id, qty, extra FROM order_lines WHERE order_id=?1", Tuple.class).setParameter(1, returnOrderId).getResultList();
        for (Object o : rows) {
            Tuple t = (Tuple) o;
            Long outLineId = lineSupport.jsonLong(t.get("extra"), "sourceOutLineId");
            if (outLineId == null) continue;
            BigDecimal qty = lineSupport.toBd(t.get("qty"));
            if (unlock) {
                em.createNativeQuery("UPDATE sales_out_lines SET return_locked_qty=GREATEST(return_locked_qty-?1,0) WHERE id=?2").setParameter(1, qty).setParameter(2, outLineId).executeUpdate();
            } else {
                int updated = em.createNativeQuery("UPDATE sales_out_lines SET return_locked_qty=return_locked_qty+?1 WHERE id=?2 AND COALESCE(shipped_qty,qty,0)-COALESCE(return_locked_qty,0)-COALESCE(returned_qty,0)>=?1").setParameter(1, qty).setParameter(2, outLineId).executeUpdate();
                if (updated == 0) return "可退数量不足，原出库行可能已被其他销退单锁定";
            }
        }
        return null;
    }


    @SuppressWarnings("unchecked")
    private String validateReturnLines(UUID tid, Long refSalesOutId, Map<String, Object> body) {
        if (refSalesOutId == null) return "请选择原销售出库单";
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        var aggregation = lineSupport.aggregate(lines);
        if (aggregation.hasError()) return aggregation.error();
        for (Map.Entry<Long, BigDecimal> e : aggregation.quantities().entrySet()) {
            var rs = em.createNativeQuery("SELECT COALESCE(shipped_qty,qty,0) AS shipped, COALESCE(return_locked_qty,0) AS locked, COALESCE(returned_qty,0) AS returned FROM sales_out_lines WHERE id=?1 AND sales_out_id=?2", Tuple.class)
                    .setParameter(1, e.getKey()).setParameter(2, refSalesOutId).getResultList();
            if (rs.isEmpty()) return "原出库行 " + e.getKey() + " 不存在";
            Tuple t = (Tuple) rs.get(0);
            BigDecimal available = lineSupport.toBd(t.get("shipped")).subtract(lineSupport.toBd(t.get("locked"))).subtract(lineSupport.toBd(t.get("returned")));
            if (e.getValue().compareTo(available) > 0) return "原出库行 " + e.getKey() + " 可退数量不足";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void insertLines(Long orderId, Long refSalesOutId, Map<String, Object> body) {
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null) return;
        int seq = 1;
        for (Map<String, Object> l : lines) {
            if (l.get("productId") == null) continue;
            BigDecimal qty = lineSupport.toBd(l.get("qty"));
            Long sourceOutLineId = lineSupport.lineSourceOutLineId(l);
            Tuple source = sourceOutLine(refSalesOutId, sourceOutLineId);
            BigDecimal shipped = lineSupport.toBd(source.get("shipped_qty"));
            if (shipped.signum() <= 0) shipped = lineSupport.toBd(source.get("qty"));
            BigDecimal sourceAmount = lineSupport.toBd(source.get("final_amount"));
            BigDecimal price = lineSupport.resolveUnitPrice(shipped, sourceAmount, l.get("unitPrice"));
            BigDecimal tax = lineSupport.toBd(source.get("tax_rate"));
            if (tax.signum() == 0) tax = new BigDecimal("0.13");
            BigDecimal sub = qty.multiply(price);
            var taxSplit = com.dms.v4.V4Money.splitTax(sub, tax);
            String extra = "{\"sourceOutLineId\":" + sourceOutLineId + "}";
            em.createNativeQuery(
                    "INSERT INTO order_lines (order_id, seq, product_id, product_code, product_name, product_spec, batch_no, serial_no, qty, unit_price, tax_rate, sub_total, standard_price_incl_tax, final_amount, amount_excl_tax, tax_amount, extra, is_gift, created_at, updated_at) " +
                    "VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?12,?12,?13,?14,CAST(?15 AS jsonb),?16,now(),now())")
              .setParameter(1, orderId).setParameter(2, seq++)
              .setParameter(3, lineSupport.toLong(l.get("productId")))
              .setParameter(4, l.get("productCode")).setParameter(5, l.get("productName")).setParameter(6, l.get("productSpec"))
              .setParameter(7, l.get("batchNo"))
              .setParameter(8, l.get("serialNo"))
              .setParameter(9, qty).setParameter(10, price).setParameter(11, tax).setParameter(12, sub)
              .setParameter(13, taxSplit.get("excl")).setParameter(14, taxSplit.get("tax"))
              .setParameter(15, extra).setParameter(16, Boolean.TRUE.equals(l.get("isGift")))
              .executeUpdate();
        }
    }

    private BigDecimal calcTotal(Long refSalesOutId, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        BigDecimal total = BigDecimal.ZERO;
        if (lines == null) return total;
        for (Map<String, Object> l : lines) {
            Long sourceOutLineId = lineSupport.lineSourceOutLineId(l);
            Tuple source = sourceOutLine(refSalesOutId, sourceOutLineId);
            BigDecimal shipped = lineSupport.toBd(source.get("shipped_qty"));
            if (shipped.signum() <= 0) shipped = lineSupport.toBd(source.get("qty"));
            BigDecimal sourceAmount = lineSupport.toBd(source.get("final_amount"));
            BigDecimal price = lineSupport.resolveUnitPrice(shipped, sourceAmount, l.get("unitPrice"));
            total = total.add(lineSupport.calcLineTotal(lineSupport.toBd(l.get("qty")), price));
        }
        return total;
    }

    private Tuple sourceOutLine(Long refSalesOutId, Long sourceOutLineId) {
        if (refSalesOutId == null || sourceOutLineId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "原出库行不能为空");
        var rs = em.createNativeQuery("SELECT id, product_id, qty, shipped_qty, final_amount, tax_rate FROM sales_out_lines WHERE id=?1 AND sales_out_id=?2", Tuple.class)
                .setParameter(1, sourceOutLineId).setParameter(2, refSalesOutId).getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "原出库行不存在");
        return (Tuple) rs.get(0);
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
        try { m.put("reasonCode", t.get("reason_code")); } catch (Exception ignored) {}
        try {
            Object ed = t.get("expected_date");
            m.put("expectedDate", ed == null ? null : ed.toString());
        } catch (Exception ignored) {}
        m.put("amountInclTax", t.get("amount_incl_tax"));
        m.put("finalAmount", t.get("final_amount"));
        try { m.put("auditUserName", t.get("audit_user_name")); } catch (Exception ignored) {}
        m.put("status", t.get("status"));
        try { m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at"))); } catch (Exception ignored) {}
        try { m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at"))); } catch (Exception ignored) {}
        return m;
    }

    private List<Map<String, Object>> allowedActions(String status) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if ("DRAFT".equals(status)) {
            actions.add(ActionButtonSupport.action("edit", "编辑", "primary", "PUT", ""));
            actions.add(ActionButtonSupport.action("submit", "提交审批", "warning", "POST", "/submit"));
            actions.add(ActionButtonSupport.action("cancel", "取消", "danger", "POST", "/cancel"));
        } else if ("SUBMITTED".equals(status)) {
            actions.add(ActionButtonSupport.action("approve", "审批通过", "success", "POST", "/approve"));
            actions.add(ActionButtonSupport.action("reject", "驳回", "danger", "POST", "/reject"));
        } else if ("APPROVED".equals(status)) {
            actions.add(ActionButtonSupport.action("cancel", "取消", "warning", "POST", "/cancel"));
        }
        return actions;
    }

    

}
