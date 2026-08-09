/*
 * 销售订单 Controller - 完整状态机 + 自动建销售出库草稿
 * 对齐采购订单 PurchaseOrderController 的实现风格。
 *
 * 状态机：DRAFT -> SUBMITTED -> APPROVED -> SHIPPING -> COMPLETED
 *                       \-> REJECTED / CANCELLED
 *
 * 端点：/api/sales-orders（新端点，避免与旧 /api/orders JPA 逻辑冲突）
 */
package com.dms.order.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.ContentDispositionUtils;
import com.dms.common.util.DateFmt;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.TenantContext;
import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.execution.service.AutoDocGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/sales-orders")
@RequiredArgsConstructor
public class SalesOrderController {

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

        StringBuilder where = new StringBuilder(" WHERE o.tenant_id = ?1 AND o.deleted_at IS NULL AND COALESCE(o.is_red,false) = false");
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
                "SELECT o.id, o.code, o.order_type, o.dealer_id, " +
                "COALESCE(NULLIF(CAST(o.ship_snapshot AS jsonb)->>'dealerName',''), d.name) AS dealer_name, " +
                "o.warehouse_id, w.name AS warehouse_name, u.name AS audit_user_name, o.approved_at AS audit_at, " +
                "o.amount_incl_tax, o.discount_amount, o.final_amount, o.tax_amount, o.expected_date, " +
                "o.status, o.extra, o.created_at " +
                "FROM orders o " +
                "LEFT JOIN dealers d ON d.id = o.dealer_id " +
                "LEFT JOIN warehouses w ON w.id = o.warehouse_id " +
                "LEFT JOIN users u ON u.id = o.approved_by " +
                where +
                " ORDER BY o.created_at DESC LIMIT " + limitParam + " OFFSET " + offsetParam,
                Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(params.size() + 1, size);
        q.setParameter(params.size() + 2, offset);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toBrief(t));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("list", list);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        Map<String, Object> data = readOne(id, tid);
        if (data == null) return ApiResponse.fail(40404, "销售订单不存在");

        var q = em.createNativeQuery(
                "SELECT ol.id, ol.seq, ol.product_id, p.code AS p_code, p.name_cn AS p_name, p.spec AS p_spec, " +
                "ol.qty, ol.unit_price, ol.tax_rate, ol.sub_total, ol.is_gift " +
                "FROM order_lines ol LEFT JOIN products p ON p.id = ol.product_id " +
                "WHERE ol.order_id = ?1 ORDER BY ol.seq, ol.id", Tuple.class);
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
            l.put("unitPrice", t.get("unit_price"));
            l.put("taxRate", t.get("tax_rate"));
            l.put("subtotal", t.get("sub_total"));
            l.put("isGift", t.get("is_gift"));
            lines.add(l);
        }
        data.put("lines", lines);
        data.put("allowedActions", allowedActions(String.valueOf(data.get("status"))));
        return ApiResponse.ok(data);
    }

    @PostMapping
    @OperationLog(businessType = "salesOrder", action = OperationAction.CREATE, remark = "销售订单-创建")
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (body.get("dealerId") == null) return ApiResponse.fail(40001, "经销商不能为空");
        if (body.get("warehouseId") == null) return ApiResponse.fail(40001, "发货仓库不能为空");

        String code = docNoGenerator.next("SO");
        BigDecimal total = calcTotal(body);

        var insert = em.createNativeQuery(
                "INSERT INTO orders (tenant_id, code, order_type, dealer_id, warehouse_id, ship_snapshot, " +
                "amount_incl_tax, discount_amount, final_amount, tax_amount, expected_date, status, remark, extra, " +
                "created_at, updated_at, created_by) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, CAST(?6 AS jsonb), ?7, 0, ?7, 0, CAST(?8 AS date), 'DRAFT', ?9, CAST(?10 AS jsonb), now(), now(), ?11) " +
                "RETURNING id");
        insert.setParameter(1, tid);
        insert.setParameter(2, code);
        insert.setParameter(3, body.getOrDefault("orderType", "NORMAL"));
        insert.setParameter(4, toLong(body.get("dealerId")));
        insert.setParameter(5, toLong(body.get("warehouseId")));
        insert.setParameter(6, shipSnapshot(body));
        insert.setParameter(7, total);
        insert.setParameter(8, body.get("expectedDate"));
        insert.setParameter(9, body.getOrDefault("remark", ""));
        insert.setParameter(10, extraToJson(body.get("extra")));
        insert.setParameter(11, TenantContext.getUserId());
        Long id = ((Number) insert.getSingleResult()).longValue();

        insertLines(id, body);
        audit(id, "SO_CREATE");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("code", code);
        return ApiResponse.ok(res);
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "salesOrder", action = OperationAction.UPDATE, remark = "销售订单-更新")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "销售订单不存在");
        if (!"DRAFT".equals(status)) return ApiResponse.fail(40009, "仅草稿可编辑，当前状态: " + status);

        BigDecimal total = calcTotal(body);
        em.createNativeQuery(
                "UPDATE orders SET order_type = ?1, dealer_id = ?2, warehouse_id = ?3, ship_snapshot = CAST(?4 AS jsonb), " +
                "amount_incl_tax = ?5, final_amount = ?5, expected_date = CAST(?6 AS date), remark = ?7, extra = CAST(?8 AS jsonb), updated_at = now() " +
                "WHERE id = ?9 AND tenant_id = ?10")
            .setParameter(1, body.getOrDefault("orderType", "NORMAL"))
            .setParameter(2, toLong(body.get("dealerId")))
            .setParameter(3, toLong(body.get("warehouseId")))
            .setParameter(4, shipSnapshot(body))
            .setParameter(5, total)
            .setParameter(6, body.get("expectedDate"))
            .setParameter(7, body.getOrDefault("remark", ""))
            .setParameter(8, extraToJson(body.get("extra")))
            .setParameter(9, id).setParameter(10, tid)
            .executeUpdate();

        em.createNativeQuery("DELETE FROM order_lines WHERE order_id = ?1").setParameter(1, id).executeUpdate();
        insertLines(id, body);
        return ApiResponse.ok(Map.of("id", id));
    }

    @PostMapping("/{id}/submit")
    @OperationLog(businessType = "salesOrder", action = OperationAction.UPDATE, remark = "销售订单-提交审批")
    @Transactional
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        int n = em.createNativeQuery("UPDATE orders SET status='PENDING_APPROVAL', submitted_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2 AND status='DRAFT'")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (n == 0) return ApiResponse.fail(40009, "Only draft sales order can be submitted");
        try {
            StartApprovalRequest request = new StartApprovalRequest();
            request.setBusinessType("SALES_ORDER");
            request.setBusinessId(id);
            Object code = em.createNativeQuery("SELECT code FROM orders WHERE id=?1").setParameter(1, id).getSingleResult();
            request.setBusinessCode(String.valueOf(code));
            request.setTitle("Sales order approval: " + request.getBusinessCode());
            request.setBusinessSnapshot(buildApprovalSnapshot(id));
            ApprovalInstance instance = approvalService.start(request);
            boolean approved = "APPROVED".equals(instance.getStatus().name()) || "AUTO_APPROVED".equals(instance.getStatus().name());
            return ApiResponse.ok(Map.of("id", id, "newStatus", approved ? "APPROVED" : "PENDING_APPROVAL", "approvalInstanceId", instance.getId(), "autoApproved", approved));
        } catch (Exception e) {
            em.createNativeQuery("UPDATE orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2")
                    .setParameter(1, id).setParameter(2, tid).executeUpdate();
            throw e;
        }
    }

    @PostMapping("/{id}/approve")
    @OperationLog(businessType = "salesOrder", action = OperationAction.APPROVE, remark = "销售订单-审批通过")
    @Transactional
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        ApprovalInstance instance = approvalService.approveBusiness("SALES_ORDER", id, body == null ? null : String.valueOf(body.getOrDefault("comment", "")));
        em.createNativeQuery("UPDATE orders SET approved_by=?1 WHERE id=?2").setParameter(1, TenantContext.getUserId()).setParameter(2, id).executeUpdate();
        return ApiResponse.ok(Map.of("id", id, "newStatus", "APPROVED".equals(instance.getStatus().name()) ? "APPROVED" : "PENDING_APPROVAL", "approvalInstanceId", instance.getId()));
    }

    @PostMapping("/{id}/reject")
    @OperationLog(businessType = "salesOrder", action = OperationAction.REJECT, remark = "销售订单-驳回")
    @Transactional
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        ApprovalInstance instance = approvalService.rejectBusiness("SALES_ORDER", id, body == null ? null : String.valueOf(body.getOrDefault("comment", "")));
        return ApiResponse.ok(Map.of("id", id, "newStatus", instance.getStatus().name(), "approvalInstanceId", instance.getId()));
    }

    @PostMapping("/{id}/cancel")
    @OperationLog(businessType = "salesOrder", action = OperationAction.UPDATE, remark = "销售订单-取消")
    @Transactional
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "销售订单不存在");
        if (!"DRAFT".equals(status) && !"APPROVED".equals(status)) {
            return ApiResponse.fail(40009, "当前状态不允许取消: " + status);
        }

        if ("APPROVED".equals(status)) {
            Object cnt = em.createNativeQuery(
                    "SELECT COUNT(*) FROM sales_outs WHERE tenant_id = ?1 AND source_order_id = ?2 AND status NOT IN ('DRAFT','CANCELLED')")
                    .setParameter(1, tid).setParameter(2, id).getSingleResult();
            if (((Number) cnt).longValue() > 0)
                return ApiResponse.fail(40009, "存在已执行的销售出库单，不能取消销售订单");

            Object shipped = em.createNativeQuery(
                    "SELECT COALESCE(SUM(COALESCE(shipped_qty,0)),0) FROM sales_out_lines WHERE sales_out_id IN " +
                    "(SELECT id FROM sales_outs WHERE source_order_id = ?1)").setParameter(1, id).getSingleResult();
            if (new BigDecimal(String.valueOf(shipped)).signum() > 0)
                return ApiResponse.fail(40009, "已存在发货记录，不能取消销售订单");
        }

        em.createNativeQuery("UPDATE orders SET status='CANCELLED', cancelled_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        em.createNativeQuery(
                "UPDATE sales_outs SET status='CANCELLED', cancelled_at=now(), updated_at=now() " +
                "WHERE source_order_id=?1 AND tenant_id=?2 AND status IN ('DRAFT','APPROVED','PARTIAL_SHIPPED')")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        // 同步取消该订单下出库单的 DRAFT 发货子单
        em.createNativeQuery(
                "UPDATE sales_out_batches SET status='CANCELLED', cancelled_at=now(), updated_at=now() " +
                "WHERE sales_out_id IN (SELECT id FROM sales_outs WHERE source_order_id=?1 AND tenant_id=?2) AND status='DRAFT'")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();

        audit(id, "SO_CANCEL");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("status", "CANCELLED");
        return ApiResponse.ok(res);
    }

    @DeleteMapping("/{id}")
    @OperationLog(businessType = "salesOrder", action = OperationAction.DELETE, remark = "销售订单-删除")
    @Transactional
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "销售订单不存在");
        if (!"DRAFT".equals(status)) return ApiResponse.fail(40009, "仅草稿状态可删除");
        em.createNativeQuery("UPDATE orders SET deleted_at = now() WHERE id = ?1 AND tenant_id = ?2")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        return ApiResponse.ok(Map.of("id", id));
    }

    @GetMapping("/actions/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export() throws Exception {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT o.id, o.code, o.order_type, o.dealer_id, " +
                "COALESCE(NULLIF(CAST(o.ship_snapshot AS jsonb)->>'dealerName',''), d.name) AS dealer_name, " +
                "o.warehouse_id, w.name AS warehouse_name, o.amount_incl_tax, o.final_amount, " +
                "o.expected_date, o.status, o.created_at " +
                "FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id " +
                "LEFT JOIN warehouses w ON w.id = o.warehouse_id " +
                "WHERE o.tenant_id = ?1 AND o.deleted_at IS NULL AND COALESCE(o.is_red,false) = false " +
                "ORDER BY o.created_at DESC", Tuple.class);
        q.setParameter(1, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toBrief(t));

        String[] headers = {"ID", "销售单号", "订单类型", "经销商ID", "经销商名称", "仓库ID", "发货仓库", "含税金额", "最终金额", "期望到货", "状态", "创建时间"};
        String[] fieldNames = {"id", "code", "orderType", "dealerId", "dealerName", "warehouseId", "warehouseName", "amountInclTax", "finalAmount", "expectedDate", "status", "createdAt"};
        byte[] excel = ExcelExportUtils.exportMapToExcel(list, headers, fieldNames);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("销售订单列表.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }

    @PostMapping("/batch-import")
    @Transactional
    public ApiResponse<Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) return ApiResponse.fail(40001, "请选择要导入的文件");
        List<Map<String, Object>> data = ExcelImportUtils.importFromExcel(file.getInputStream(), file.getOriginalFilename());
        if (data.isEmpty()) return ApiResponse.fail(40002, "Excel 文件中没有数据");

        int success = 0, failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> row = data.get(i);
            try {
                Long dealerId = toLong(row.get("经销商ID"));
                Long warehouseId = toLong(row.get("仓库ID"));
                if (dealerId == null) throw new IllegalArgumentException("经销商ID不能为空");
                if (warehouseId == null) throw new IllegalArgumentException("仓库ID不能为空");
                String code = docNoGenerator.next("SO");
                BigDecimal amt = toBd(row.get("含税金额"));
                em.createNativeQuery(
                        "INSERT INTO orders (tenant_id, code, order_type, dealer_id, warehouse_id, amount_incl_tax, final_amount, expected_date, status, ship_snapshot, created_at, updated_at) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?6, ?7, 'DRAFT', '{}'::jsonb, now(), now())")
                        .setParameter(1, TenantContext.getTenantId())
                        .setParameter(2, code)
                        .setParameter(3, strOr(row.get("订单类型"), "NORMAL"))
                        .setParameter(4, dealerId).setParameter(5, warehouseId)
                        .setParameter(6, amt).setParameter(7, strOr(row.get("期望到货"), null))
                        .executeUpdate();
                success++;
            } catch (Exception e) {
                failed++;
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("row", i + 2); err.put("error", e.getMessage());
                errors.add(err);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", data.size()); result.put("success", success); result.put("failed", failed); result.put("errors", errors);
        return ApiResponse.ok(result);
    }

    // ==================== 辅助方法 ====================


    private Map<String, Object> buildApprovalSnapshot(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery("SELECT code, order_type, dealer_id, warehouse_id, final_amount, amount_incl_tax, expected_date FROM orders WHERE id=?1", Tuple.class)
                .setParameter(1, id).getResultList();
        Map<String, Object> snapshot = new HashMap<>();
        if (rows.isEmpty()) return snapshot;
        Tuple row = rows.get(0);
        snapshot.put("code", row.get("code"));
        snapshot.put("orderType", row.get("order_type"));
        snapshot.put("dealerId", row.get("dealer_id"));
        snapshot.put("warehouseId", row.get("warehouse_id"));
        snapshot.put("finalAmount", row.get("final_amount"));
        snapshot.put("amountInclTax", row.get("amount_incl_tax"));
        snapshot.put("expectedDate", row.get("expected_date"));
        return snapshot;
    }
    private ApiResponse<Map<String, Object>> doTransition(Long id, String fromStatus, String toStatus, String action) {
        UUID tid = TenantContext.getTenantId();
        int n = em.createNativeQuery(
                "UPDATE orders SET status = ?1, updated_at = now(), " +
                "submitted_at = CASE WHEN ?1 = 'SUBMITTED' THEN now() ELSE submitted_at END, " +
                "approved_at  = CASE WHEN ?1 = 'APPROVED'  THEN now() ELSE approved_at END, " +
                "completed_at = CASE WHEN ?1 = 'COMPLETED' THEN now() ELSE completed_at END " +
                "WHERE id = ?2 AND tenant_id = ?3 AND status = ?4")
            .setParameter(1, toStatus).setParameter(2, id).setParameter(3, tid).setParameter(4, fromStatus)
            .executeUpdate();
        if (n == 0) return ApiResponse.fail(40009, "状态不允许该操作，需要当前状态为 " + fromStatus);
        audit(id, action);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("newStatus", toStatus);
        return ApiResponse.ok(res);
    }

    private void audit(Long id, String action) {
        try {
            em.createNativeQuery(
                    "INSERT INTO audit_logs (tenant_id, user_id, action, resource_type, resource_id, ip, at_time) " +
                    "VALUES (?1, ?2, ?3, 'sales_order', ?4, '127.0.0.1', now())")
              .setParameter(1, TenantContext.getTenantId())
              .setParameter(2, TenantContext.getUserId())
              .setParameter(3, action).setParameter(4, String.valueOf(id))
              .executeUpdate();
        } catch (Exception ignored) {}
    }

    private String getStatus(Long id, UUID tid) {
        try {
            var q = em.createNativeQuery("SELECT status FROM orders WHERE id = ?1 AND tenant_id = ?2");
            q.setParameter(1, id).setParameter(2, tid);
            return String.valueOf(q.getSingleResult());
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> readOne(Long id, UUID tid) {
        try {
            var q = em.createNativeQuery(
                    "SELECT o.*, COALESCE(NULLIF(CAST(o.ship_snapshot AS jsonb)->>'dealerName',''), d.name) AS dealer_name, " +
                    "w.name AS warehouse_name, u.name AS approved_by_name " +
                    "FROM orders o " +
                    "LEFT JOIN dealers d ON d.id = o.dealer_id " +
                    "LEFT JOIN warehouses w ON w.id = o.warehouse_id " +
                    "LEFT JOIN users u ON u.id = o.approved_by " +
                    "WHERE o.id = ?1 AND o.tenant_id = ?2", Tuple.class);
            q.setParameter(1, id).setParameter(2, tid);
            @SuppressWarnings("unchecked")
            List<Tuple> rs = q.getResultList();
            if (rs.isEmpty()) return null;
            return toBrief(rs.get(0));
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> toBrief(Tuple t) {
        Map<String, Object> m = new LinkedHashMap<>();
        try { m.put("id", t.get("id")); } catch (Exception ignored) {}
        try { m.put("code", t.get("code")); } catch (Exception ignored) {}
        try { m.put("orderType", t.get("order_type")); } catch (Exception ignored) {}
        try { m.put("dealerId", t.get("dealer_id")); } catch (Exception ignored) {}
        try { m.put("dealerName", t.get("dealer_name")); } catch (Exception ignored) {}
        try { m.put("warehouseId", t.get("warehouse_id")); } catch (Exception ignored) {}
        try { m.put("warehouseName", t.get("warehouse_name")); } catch (Exception ignored) {}
        try { m.put("auditUserName", t.get("audit_user_name")); } catch (Exception ignored) {}
        try { m.put("approvedByName", t.get("approved_by_name")); } catch (Exception ignored) {}
        try { m.put("amountInclTax", t.get("amount_incl_tax")); } catch (Exception ignored) {}
        try { m.put("discountAmount", t.get("discount_amount")); } catch (Exception ignored) {}
        try { m.put("finalAmount", t.get("final_amount")); } catch (Exception ignored) {}
        try { m.put("taxAmount", t.get("tax_amount")); } catch (Exception ignored) {}
        try { m.put("expectedDate", DateFmt.fmt(t.get("expected_date"))); } catch (Exception ignored) {}
        try { m.put("status", t.get("status")); } catch (Exception ignored) {}
        try { m.put("remark", t.get("remark")); } catch (Exception ignored) {}
        try { m.put("createdAt", DateFmt.fmt(t.get("created_at"))); } catch (Exception ignored) {}
        try { m.put("submittedAt", DateFmt.fmt(t.get("submitted_at"))); } catch (Exception ignored) {}
        try { m.put("approvedAt", DateFmt.fmt(t.get("approved_at"))); } catch (Exception ignored) {}
        try { m.put("completedAt", DateFmt.fmt(t.get("completed_at"))); } catch (Exception ignored) {}
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

    private void insertLines(Long orderId, Map<String, Object> body) {
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
                    "INSERT INTO order_lines (order_id, seq, product_id, qty, unit_price, tax_rate, sub_total, is_gift, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, now(), now())")
                .setParameter(1, orderId).setParameter(2, seq++)
                .setParameter(3, toLong(l.get("productId")))
                .setParameter(4, qty).setParameter(5, price).setParameter(6, tax).setParameter(7, sub)
                .setParameter(8, Boolean.TRUE.equals(l.get("isGift")))
                .executeUpdate();
        }
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

    private String shipSnapshot(Map<String, Object> body) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            Object dName = body.get("dealerName");
            if (dName != null) snap.put("dealerName", dName);
            if (snap.isEmpty()) return "{}";
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(snap);
        } catch (Exception e) { return "{}"; }
    }

    private String extraToJson(Object extra) {
        if (extra == null) return "{}";
        if (extra instanceof String) return (String) extra;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extra); }
        catch (Exception e) { return "{}"; }
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
