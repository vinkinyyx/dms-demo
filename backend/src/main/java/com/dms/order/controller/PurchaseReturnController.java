/*
 * 采退订单 Controller (v3.8.1)
 * 基路径 /api/purchase-returns，操作 purchase_orders 表 is_red=true 的红字采购单。
 * 状态机：DRAFT -> SUBMITTED -> APPROVED -> SHIPPING -> COMPLETED (\->REJECTED/CANCELLED)
 * 审批通过后自动生成采退出库草稿单(RGI)，出库进度回写采退单状态。
 * 采退不限制原单与数量，出库不限制库存状态。
 */
package com.dms.order.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.TenantContext;
import com.dms.common.util.PagingUtil;
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
@RequestMapping("/api/purchase-returns")
@RequiredArgsConstructor
public class PurchaseReturnController {

    private final EntityManager em;
    private final AutoDocGenerator autoDocGenerator;
    private final com.dms.common.util.DocNoGenerator docNoGenerator;
    private final ApprovalService approvalService;
    private final com.dms.collab.CrossTenantCollabService crossTenantCollab;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String createdAtFrom,
            @RequestParam(required = false) String createdAtTo,
            @RequestParam(required = false) String updatedAtFrom,
            @RequestParam(required = false) String updatedAtTo,
            @RequestParam(required = false) String finalAmountFrom,
            @RequestParam(required = false) String finalAmountTo,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String auditUserName,
            @RequestParam(required = false) String sort) {
        UUID tid = TenantContext.getTenantId();
        int safePage = PagingUtil.normalizePage(page); int safeSize = PagingUtil.normalizeSize(size); int offset = (safePage - 1) * safeSize;
        StringBuilder where = new StringBuilder(" WHERE po.tenant_id = ?1 AND po.deleted_at IS NULL AND COALESCE(po.is_red,false) = true");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (status != null && !status.isBlank()) { where.append(" AND po.status = ?").append(idx++); params.add(status); }
        if (supplierId != null) { where.append(" AND po.supplier_id = ?").append(idx++); params.add(supplierId); }
        if (warehouseId != null) { where.append(" AND po.warehouse_id = ?").append(idx++); params.add(warehouseId); }
        if (productId != null) { where.append(" AND EXISTS (SELECT 1 FROM purchase_order_lines pl WHERE pl.po_id = po.id AND pl.product_id = ?)").append(idx++); params.add(productId); }
        if (createdAtFrom != null && !createdAtFrom.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(createdAtFrom, true); if (__t != null) { where.append(" AND po.created_at >= ?").append(idx++); params.add(__t); } }
        if (createdAtTo != null && !createdAtTo.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(createdAtTo, false); if (__t != null) { where.append(com.dms.common.util.SpecUtil.hasTime(createdAtTo) ? " AND po.created_at <= ?" : " AND po.created_at < ?").append(idx++); params.add(__t); } }
        if (updatedAtFrom != null && !updatedAtFrom.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(updatedAtFrom, true); if (__t != null) { where.append(" AND po.updated_at >= ?").append(idx++); params.add(__t); } }
        if (updatedAtTo != null && !updatedAtTo.isBlank()) { java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(updatedAtTo, false); if (__t != null) { where.append(com.dms.common.util.SpecUtil.hasTime(updatedAtTo) ? " AND po.updated_at <= ?" : " AND po.updated_at < ?").append(idx++); params.add(__t); } }
        if (finalAmountFrom != null && !finalAmountFrom.isBlank()) { where.append(" AND po.final_amount >= ?").append(idx++); params.add(new java.math.BigDecimal(finalAmountFrom)); }
        if (finalAmountTo != null && !finalAmountTo.isBlank()) { where.append(" AND po.final_amount <= ?").append(idx++); params.add(new java.math.BigDecimal(finalAmountTo)); }
        if (code != null && !code.isBlank()) { where.append(" AND po.code ILIKE ?").append(idx++); params.add("%" + code.trim() + "%"); }
        if (orderType != null && !orderType.isBlank()) { where.append(" AND po.order_type = ?").append(idx++); params.add(orderType); }
        if (auditUserName != null && !auditUserName.isBlank()) { where.append(" AND EXISTS (SELECT 1 FROM users u2 WHERE u2.id = po.approved_by AND u2.name ILIKE ?)").append(idx++); params.add("%" + auditUserName.trim() + "%"); }

        var qCnt = em.createNativeQuery("SELECT COUNT(*) FROM purchase_orders po " + where);
        for (int i = 0; i < params.size(); i++) qCnt.setParameter(i + 1, params.get(i));
        long total = ((Number) qCnt.getSingleResult()).longValue();

        String orderSql = " ORDER BY po.created_at DESC";
        if (sort != null && !sort.isBlank()) {
            String[] sp = sort.split(",");
            String f = sp[0].trim();
            String dir = sp.length > 1 && "asc".equalsIgnoreCase(sp[1].trim()) ? "ASC" : "DESC";
            switch (f) {
                case "updatedAt" -> orderSql = " ORDER BY po.updated_at " + dir;
                case "createdAt" -> orderSql = " ORDER BY po.created_at " + dir;
                case "finalAmount" -> orderSql = " ORDER BY po.final_amount " + dir;
                case "amountInclTax" -> orderSql = " ORDER BY po.amount_incl_tax " + dir;
                case "code" -> orderSql = " ORDER BY po.code " + dir;
                default -> { }
            }
        }
        String limitParam = "?" + idx++;
        String offsetParam = "?" + idx++;
        var q = em.createNativeQuery(
                "SELECT po.id, po.code, po.order_type, po.supplier_id, COALESCE(NULLIF(po.supplier_name,''), s.name) AS supplier_name, " +
                "po.warehouse_id, w.name AS warehouse_name, po.return_reason, po.amount_incl_tax, po.final_amount, " +
                "po.expected_date, po.status, po.approved_at, u.name AS audit_user_name, po.created_at, po.updated_at " +
                "FROM purchase_orders po LEFT JOIN suppliers s ON s.id=po.supplier_id " +
                "LEFT JOIN warehouses w ON w.id=po.warehouse_id LEFT JOIN users u ON u.id=po.approved_by " +
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

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT po.*, COALESCE(NULLIF(po.supplier_name,''), s.name) AS supplier_name, w.name AS warehouse_name, p0.code AS ref_po_code " +
                "FROM purchase_orders po LEFT JOIN suppliers s ON s.id=po.supplier_id " +
                "LEFT JOIN warehouses w ON w.id=po.warehouse_id LEFT JOIN purchase_orders p0 ON p0.id=po.ref_po_id " +
                "WHERE po.id=?1 AND po.tenant_id=?2 AND COALESCE(po.is_red,false)=true", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rs = q.getResultList();
        if (rs.isEmpty()) return ApiResponse.fail(40404, "采退订单不存在");
        Tuple t = rs.get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id); data.put("code", t.get("code")); data.put("orderType", t.get("order_type"));
        data.put("supplierId", t.get("supplier_id")); data.put("supplierName", t.get("supplier_name"));
        data.put("warehouseId", t.get("warehouse_id")); data.put("warehouseName", t.get("warehouse_name"));
        data.put("refPoId", t.get("ref_po_id")); data.put("refPoCode", t.get("ref_po_code"));
        data.put("returnReason", t.get("return_reason"));
        data.put("remark", t.get("remark")); data.put("status", t.get("status"));
        data.put("amountInclTax", t.get("amount_incl_tax")); data.put("finalAmount", t.get("final_amount"));
        data.put("expectedDate", t.get("expected_date"));
        try { data.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at"))); } catch (Exception ignored) {}
        var lq = em.createNativeQuery(
                "SELECT pol.id, pol.seq, pol.product_id, pol.qty, pol.unit_price, pol.tax_rate, pol.subtotal, " +
                "p.code AS p_code, p.name_cn AS p_name, p.spec AS p_spec, p.unit AS p_unit, p.is_serial_managed " +
                "FROM purchase_order_lines pol LEFT JOIN products p ON p.id=pol.product_id WHERE pol.po_id=?1 ORDER BY pol.seq, pol.id", Tuple.class);
        lq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> lrows = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : lrows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.get("id")); m.put("seq", l.get("seq")); m.put("productId", l.get("product_id"));
            m.put("productCode", l.get("p_code")); m.put("productName", l.get("p_name"));
            m.put("productSpec", l.get("p_spec")); m.put("unit", l.get("p_unit"));
            m.put("qty", l.get("qty")); m.put("unitPrice", l.get("unit_price"));
            m.put("taxRate", l.get("tax_rate")); m.put("subtotal", l.get("subtotal"));
            m.put("isSerialManaged", l.get("is_serial_managed"));
            lines.add(m);
        }
        data.put("lines", lines);
        data.put("allowedActions", allowedActions(String.valueOf(data.get("status"))));
        return ApiResponse.ok(data);
    }

    @PostMapping
    @OperationLog(businessType = "purchaseReturn", action = OperationAction.CREATE, remark = "采退订单-创建")
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (body.get("supplierId") == null) return ApiResponse.fail(40001, "供应商不能为空");
        if (body.get("warehouseId") == null) return ApiResponse.fail(40001, "出库仓库不能为空");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null || lines.isEmpty()) return ApiResponse.fail(40001, "采退明细不能为空");

        String code = docNoGenerator.next("RP");
        BigDecimal total = calcTotal(body);

        var insertPo = em.createNativeQuery(
                "INSERT INTO purchase_orders (tenant_id, code, order_type, is_red, supplier_id, supplier_name, warehouse_id, ref_po_id, return_reason, " +
                "amount_incl_tax, final_amount, expected_date, status, remark, extra, created_at, updated_at) " +
                "VALUES (?1,?2,'RETURN',true,?3,?4,?5,?6,?7,?8,?8,CAST(?9 AS date),'DRAFT',?10,CAST(?11 AS jsonb),now(),now()) RETURNING id");
        insertPo.setParameter(1, tid).setParameter(2, code)
                .setParameter(3, toLong(body.get("supplierId")))
                .setParameter(4, body.getOrDefault("supplierName", ""))
                .setParameter(5, toLong(body.get("warehouseId")))
                .setParameter(6, toLong(body.get("refPoId")))
                .setParameter(7, body.get("returnReason"))
                .setParameter(8, total)
                .setParameter(9, body.get("expectedDate"))
                .setParameter(10, body.getOrDefault("remark", ""))
                .setParameter(11, "{}");
        Long id = ((Number) insertPo.getSingleResult()).longValue();
        insertLines(id, body);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("code", code);
        return ApiResponse.ok(res);
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "purchaseReturn", action = OperationAction.UPDATE, remark = "采退订单-更新")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "采退订单不存在");
        if (!"DRAFT".equals(status)) return ApiResponse.fail(40009, "仅草稿可编辑，当前状态: " + status);

        BigDecimal total = calcTotal(body);
        em.createNativeQuery(
                "UPDATE purchase_orders SET supplier_id=?1, supplier_name=?2, warehouse_id=?3, ref_po_id=?4, return_reason=?5, " +
                "amount_incl_tax=?6, final_amount=?6, expected_date=CAST(?7 AS date), remark=?8, updated_at=now() WHERE id=?9 AND tenant_id=?10")
          .setParameter(1, toLong(body.get("supplierId"))).setParameter(2, body.getOrDefault("supplierName", ""))
          .setParameter(3, toLong(body.get("warehouseId"))).setParameter(4, toLong(body.get("refPoId")))
          .setParameter(5, body.get("returnReason"))
          .setParameter(6, total).setParameter(7, body.get("expectedDate"))
          .setParameter(8, body.getOrDefault("remark", ""))
          .setParameter(9, id).setParameter(10, tid).executeUpdate();
        em.createNativeQuery("DELETE FROM purchase_order_lines WHERE po_id=?1").setParameter(1, id).executeUpdate();
        insertLines(id, body);
        return ApiResponse.ok(Map.of("id", id));
    }

    @PostMapping("/{id}/submit")
    @Transactional
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        int n = em.createNativeQuery("UPDATE purchase_orders SET status='PENDING_APPROVAL', submitted_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2 AND status='DRAFT' AND COALESCE(is_red,false)=true")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (n == 0) return ApiResponse.fail(40009, "Only draft purchase return can be submitted");
        try {
            StartApprovalRequest request = new StartApprovalRequest();
            request.setBusinessType("PURCHASE_RETURN");
            request.setBusinessId(id);
            Object code = em.createNativeQuery("SELECT code FROM purchase_orders WHERE id=?1").setParameter(1, id).getSingleResult();
            request.setBusinessCode(String.valueOf(code));
            request.setTitle("Purchase return approval: " + request.getBusinessCode());
            request.setBusinessSnapshot(buildApprovalSnapshot(id));
            ApprovalInstance instance = approvalService.start(request);
            boolean approved = "APPROVED".equals(instance.getStatus().name()) || "AUTO_APPROVED".equals(instance.getStatus().name());
            // v4.5.4 跨租户反向：供应商为平台厂家时，自动转厂家红字销退草稿（对码缺失抛错整体回滚）
            try {
                crossTenantCollab.onPurchaseReturnSubmitted(id);
            } catch (com.dms.common.BusinessException be) {
                throw be;
            } catch (Exception ce) {
                log.error("跨租户采退转销退失败 prPoId={}", id, ce);
                throw ce;
            }
            return ApiResponse.ok(Map.of("id", id, "newStatus", approved ? "APPROVED" : "PENDING_APPROVAL", "approvalInstanceId", instance.getId(), "autoApproved", approved));
        } catch (Exception e) {
            em.createNativeQuery("UPDATE purchase_orders SET status='DRAFT', updated_at=now() WHERE id=?1 AND tenant_id=?2").setParameter(1, id).setParameter(2, tid).executeUpdate();
            throw e;
        }
    }

    private Map<String, Object> buildApprovalSnapshot(Long id) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery("SELECT code, order_type, supplier_id, supplier_name, warehouse_id, final_amount, amount_incl_tax, expected_date FROM purchase_orders WHERE id=?1", Tuple.class).setParameter(1, id).getResultList();
        Map<String, Object> snapshot = new HashMap<>();
        if (rows.isEmpty()) return snapshot;
        Tuple row = rows.get(0);
        snapshot.put("code", row.get("code"));
        snapshot.put("orderType", row.get("order_type"));
        snapshot.put("supplierId", row.get("supplier_id"));
        snapshot.put("supplierName", row.get("supplier_name"));
        snapshot.put("warehouseId", row.get("warehouse_id"));
        snapshot.put("finalAmount", row.get("final_amount"));
        snapshot.put("amountInclTax", row.get("amount_incl_tax"));
        snapshot.put("expectedDate", row.get("expected_date"));
        return snapshot;
    }

    @PostMapping("/{id}/approve")
    @OperationLog(businessType = "purchaseReturn", action = OperationAction.APPROVE, remark = "采退订单-审批通过")
    @Transactional
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id) {
        ApprovalInstance instance = approvalService.approveBusiness("PURCHASE_RETURN", id, null);
        em.createNativeQuery("UPDATE purchase_orders SET approved_by=?1 WHERE id=?2")
          .setParameter(1, TenantContext.getUserId()).setParameter(2, id).executeUpdate();
        return ApiResponse.ok(Map.of("id", id, "newStatus", "APPROVED".equals(instance.getStatus().name()) || "AUTO_APPROVED".equals(instance.getStatus().name()) ? "APPROVED" : "PENDING_APPROVAL", "approvalInstanceId", instance.getId()));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id) {
        ApprovalInstance instance = approvalService.rejectBusiness("PURCHASE_RETURN", id, null);
        return ApiResponse.ok(Map.of("id", id, "newStatus", instance.getStatus().name(), "approvalInstanceId", instance.getId()));
    }

    @PostMapping("/{id}/cancel")
    @OperationLog(businessType = "purchaseReturn", action = OperationAction.UPDATE, remark = "采退订单-取消")
    @Transactional
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        String status = getStatus(id, tid);
        if (status == null) return ApiResponse.fail(40404, "采退订单不存在");
        if (!"DRAFT".equals(status) && !"APPROVED".equals(status))
            return ApiResponse.fail(40009, "当前状态不允许取消: " + status);
        if ("APPROVED".equals(status)) {
            Object cnt = em.createNativeQuery(
                    "SELECT COUNT(*) FROM sales_outs WHERE source_po_id=?1 AND tenant_id=?2 AND status NOT IN ('DRAFT','CANCELLED')")
                    .setParameter(1, id).setParameter(2, tid).getSingleResult();
            if (((Number) cnt).longValue() > 0)
                return ApiResponse.fail(40009, "存在已执行的出库单，不能取消采退订单");
            Object shp = em.createNativeQuery(
                    "SELECT COALESCE(SUM(COALESCE(shipped_qty,0)),0) FROM sales_out_lines WHERE sales_out_id IN " +
                    "(SELECT id FROM sales_outs WHERE source_po_id=?1)").setParameter(1, id).getSingleResult();
            if (new BigDecimal(String.valueOf(shp)).signum() > 0)
                return ApiResponse.fail(40009, "已存在发货记录，不能取消采退订单");
        }
        em.createNativeQuery("UPDATE purchase_orders SET status='CANCELLED', completed_at=now(), updated_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        em.createNativeQuery(
                "UPDATE sales_outs SET status='CANCELLED', cancelled_at=now(), updated_at=now() WHERE source_po_id=?1 AND tenant_id=?2 AND status IN ('DRAFT','APPROVED','PARTIAL_SHIPPED')")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        em.createNativeQuery(
                "UPDATE sales_out_batches SET status='CANCELLED', cancelled_at=now(), updated_at=now() WHERE sales_out_id IN " +
                "(SELECT id FROM sales_outs WHERE source_po_id=?1 AND tenant_id=?2) AND status='DRAFT'")
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
        if (status == null) return ApiResponse.fail(40404, "采退订单不存在");
        if (!"DRAFT".equals(status)) return ApiResponse.fail(40009, "仅草稿状态可删除");
        em.createNativeQuery("UPDATE purchase_orders SET deleted_at=now() WHERE id=?1 AND tenant_id=?2")
          .setParameter(1, id).setParameter(2, tid).executeUpdate();
        return ApiResponse.ok(Map.of("id", id));
    }

    // ==================== 辅助 ====================
    @SuppressWarnings("unchecked")
    private void insertLines(Long poId, Map<String, Object> body) {
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
                    "INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, unit_price, tax_rate, subtotal, created_at) " +
                    "VALUES (?1,?2,?3,?4,?5,?6,?7,now())")
              .setParameter(1, poId).setParameter(2, seq++)
              .setParameter(3, toLong(l.get("productId")))
              .setParameter(4, qty).setParameter(5, price).setParameter(6, tax).setParameter(7, sub)
              .executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private BigDecimal calcTotal(Map<String, Object> body) {
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) for (Map<String, Object> l : lines)
            total = total.add(toBd(l.get("qty")).multiply(toBd(l.get("unitPrice"))));
        return total;
    }

    private ApiResponse<Map<String, Object>> doTransition(Long id, String from, String to) {
        UUID tid = TenantContext.getTenantId();
        int n = em.createNativeQuery(
                "UPDATE purchase_orders SET status=?1, updated_at=now(), " +
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
            var q = em.createNativeQuery("SELECT status FROM purchase_orders WHERE id=?1 AND tenant_id=?2");
            q.setParameter(1, id).setParameter(2, tid);
            return String.valueOf(q.getSingleResult());
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> toBrief(Tuple t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id")); m.put("code", t.get("code")); m.put("orderType", t.get("order_type"));
        m.put("supplierId", t.get("supplier_id")); m.put("supplierName", t.get("supplier_name"));
        m.put("warehouseId", t.get("warehouse_id")); m.put("warehouseName", t.get("warehouse_name"));
        m.put("returnReason", t.get("return_reason"));
        m.put("amountInclTax", t.get("amount_incl_tax")); m.put("finalAmount", t.get("final_amount"));
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
}
