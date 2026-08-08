/*
 * 常规业务报表 Controller v4.1
 *   全部支持以下公共筛选参数（均为可选）：
 *     from, to          - 下单日期 yyyy-MM-dd（默认近 90 天）
 *     dealerId          - 经销商 ID
 *     level             - 经销商级别
 *     region            - 区域（模糊匹配）
 *     status            - 订单状态（DRAFT/APPROVED/SHIPPING/COMPLETED/...）
 *     orderType         - 订单类型（NORMAL/URGENT）
 *     productId         - 产品 ID（产品/订单追溯使用）
 *     hospitalId        - 医院 ID（手术报台使用）
 *     limit             - 返回行数上限
 *
 *   v4.1.1 新增端点：
 *     /api/reports/inventory-aging       库存呆滞/超期
 *     /api/reports/order-approval-stats  拒单率/审批时长
 */
package com.dms.report.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class BusinessReportController {

    private final EntityManager em;
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static class F {
        StringBuilder w = new StringBuilder();
        Map<String, Object> p = new LinkedHashMap<>();
    }

    private F buildCommon(String from, String to, Long dealerId, String level, String region,
                          String status, String orderType) {
        F f = new F();
        if (from != null && !from.isBlank()) {
            f.w.append(" AND o.created_at >= :fromDate");
            f.p.put("fromDate", LocalDate.parse(from.trim(), DF).atStartOfDay());
        }
        if (to != null && !to.isBlank()) {
            f.w.append(" AND o.created_at < :toDate");
            f.p.put("toDate", LocalDate.parse(to.trim(), DF).plusDays(1).atStartOfDay());
        }
        if (dealerId != null) { f.w.append(" AND o.dealer_id = :dealerId"); f.p.put("dealerId", dealerId); }
        if (level != null && !level.isBlank()) { f.w.append(" AND d.level = :level"); f.p.put("level", level); }
        if (region != null && !region.isBlank()) { f.w.append(" AND d.region_id = :regionId "); f.p.put("regionId", region); }
        if (status != null && !status.isBlank()) { f.w.append(" AND o.status = :status"); f.p.put("status", status); }
        if (orderType != null && !orderType.isBlank()) { f.w.append(" AND o.order_type = :orderType"); f.p.put("orderType", orderType); }
        return f;
    }

    private void bindParams(Query q, Map<String, Object> params) { params.forEach(q::setParameter); }

    // 1. 经销商销售业绩排行
    @GetMapping("/sales-ranking")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> salesRanking(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType,
            @RequestParam(defaultValue = "50") int limit) {
        UUID tid = TenantContext.getTenantId();
        F f = buildCommon(from, to, dealerId, level, region, status, orderType);
        String sql = "SELECT o.dealer_id, d.code AS dealer_code, d.name AS dealer_name, d.level AS dealer_level, " +
                "       d.region_id AS region_id, r.name AS region, " +
                "       COUNT(*) AS order_count, " +
                "       COALESCE(SUM(o.amount_incl_tax), 0) AS total_amount, " +
                "       COALESCE(AVG(o.amount_incl_tax), 0) AS avg_amount, " +
                "       SUM(CASE WHEN o.status IN ('APPROVED','COMPLETED','SHIPPING') THEN 1 ELSE 0 END) AS approved_count, " +
                "       SUM(CASE WHEN o.status = 'DRAFT' THEN 1 ELSE 0 END) AS draft_count, " +
                "       SUM(CASE WHEN o.status IN ('CANCELLED','REJECTED') THEN 1 ELSE 0 END) AS cancelled_count, " +
                "       MAX(o.created_at) AS last_order_at, " +
                "       MIN(o.created_at) AS first_order_at " +
                "FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id LEFT JOIN regions r ON r.id = d.region_id " +
                "WHERE o.tenant_id = :tenantId AND o.is_red IS NOT TRUE " + f.w +
                " GROUP BY o.dealer_id, d.code, d.name, d.level, d.region_id, r.name " +
                " ORDER BY total_amount DESC NULLS LAST LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter("tenantId", tid);
        bindParams(q, f.p);
        q.setParameter("limit", Math.max(1, Math.min(500, limit)));
        return ApiResponse.ok(toList(q, "dealerId", "dealerCode", "dealerName", "dealerLevel", "regionId", "region",
                "orderCount", "totalAmount", "avgAmount",
                "approvedCount", "draftCount", "cancelledCount",
                "lastOrderAt", "firstOrderAt"));
    }

    // 2. 产品销售 TOP
    @GetMapping("/product-top10")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> productTop10(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(defaultValue = "50") int limit) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder w = new StringBuilder(" AND o.tenant_id = :tenantId AND o.is_red IS NOT TRUE ");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tenantId", tid);
        if (from != null && !from.isBlank()) { w.append(" AND o.created_at >= :fromDate "); p.put("fromDate", LocalDate.parse(from, DF).atStartOfDay()); }
        if (to != null && !to.isBlank()) { w.append(" AND o.created_at < :toDate "); p.put("toDate", LocalDate.parse(to, DF).plusDays(1).atStartOfDay()); }
        if (dealerId != null) { w.append(" AND o.dealer_id = :dealerId "); p.put("dealerId", dealerId); }
        if (productId != null) { w.append(" AND ol.product_id = :productId "); p.put("productId", productId); }
        if (categoryCode != null && !categoryCode.isBlank()) { w.append(" AND p.category_code = :categoryCode "); p.put("categoryCode", categoryCode); }
        String sql = "SELECT ol.product_id, p.code AS product_code, p.name_cn AS product_name, " +
                " p.spec AS product_spec, p.unit AS product_unit, c.name AS category_name, " +
                " COALESCE(SUM(ol.qty), 0) AS total_qty, " +
                " COALESCE(SUM(ol.sub_total), 0) AS total_amount, " +
                " COUNT(DISTINCT ol.order_id) AS order_count, " +
                " COUNT(DISTINCT o.dealer_id) AS dealer_count, " +
                " COALESCE(AVG(ol.unit_price), 0) AS avg_unit_price, " +
                " MAX(o.created_at) AS last_sale_at " +
                " FROM order_lines ol " +
                " JOIN orders o ON o.id = ol.order_id " +
                " LEFT JOIN products p ON p.id = ol.product_id " +
                " LEFT JOIN product_categories c ON c.id = p.category_id " +
                " WHERE 1=1 " + w +
                " GROUP BY ol.product_id, p.code, p.name_cn, p.spec, p.unit, c.name " +
                " ORDER BY total_amount DESC NULLS LAST LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        bindParams(q, p);
        q.setParameter("limit", Math.max(1, Math.min(500, limit)));
        return ApiResponse.ok(toList(q, "productId", "productCode", "productName", "productSpec", "productUnit",
                "categoryName", "totalQty", "totalAmount", "orderCount", "dealerCount", "avgUnitPrice", "lastSaleAt"));
    }

    // 3. 库存周转
    @GetMapping("/inventory-turnover")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> inventoryTurnover(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(defaultValue = "100") int limit) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder w = new StringBuilder(" WHERE inv.tenant_id = :tenantId ");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tenantId", tid);
        if (productId != null) { w.append(" AND inv.product_id = :productId "); p.put("productId", productId); }
        if (categoryCode != null && !categoryCode.isBlank()) { w.append(" AND p.category_code = :categoryCode "); p.put("categoryCode", categoryCode); }
        String sql = "SELECT inv.product_id, p.code AS product_code, p.name_cn AS product_name, " +
                " p.spec AS product_spec, p.unit AS product_unit, c.name AS category_name, " +
                " COALESCE(SUM(inv.qty), 0) AS current_stock, " +
                " COALESCE(SUM(CASE WHEN inv.stock_status='QUALIFIED' THEN inv.qty ELSE 0 END), 0) AS qualified_stock, " +
                " COALESCE(SUM(CASE WHEN inv.stock_status='PENDING' THEN inv.qty ELSE 0 END), 0) AS pending_stock, " +
                " COALESCE(SUM(CASE WHEN inv.stock_status='DEFECTIVE' THEN inv.qty ELSE 0 END), 0) AS defective_stock, " +
                " 0 AS recent_in_qty, 0 AS recent_out_qty, " +
                " COALESCE(EXTRACT(DAY FROM (now() - MIN(inv.prod_date))), 0) AS avg_age_days " +
                " FROM inventory inv " +
                " LEFT JOIN products p ON p.id = inv.product_id " +
                " LEFT JOIN product_categories c ON c.id = p.category_id " +
                " WHERE inv.tenant_id = :tenantId " +
                " GROUP BY inv.product_id, p.code, p.name_cn, p.spec, p.unit, c.name " +
                " ORDER BY current_stock DESC LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter("tenantId", tid);
        q.setParameter("limit", Math.max(1, Math.min(500, limit)));
        return ApiResponse.ok(toList(q, "productId", "productCode", "productName", "productSpec", "productUnit",
                "categoryName", "currentStock", "qualifiedStock", "pendingStock", "defectiveStock",
                "recentInQty", "recentOutQty", "avgAgeDays"));
    }

    // 4. 手术报台统计
    @GetMapping("/surgery-stats")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> surgeryStats(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long hospitalId,
            @RequestParam(defaultValue = "100") int limit) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder w = new StringBuilder(" AND sr.tenant_id = :tenantId ");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tenantId", tid);
        if (from != null && !from.isBlank()) { w.append(" AND sr.surgery_date >= :fromDate "); p.put("fromDate", LocalDate.parse(from, DF)); }
        if (to != null && !to.isBlank()) { w.append(" AND sr.surgery_date < :toDate "); p.put("toDate", LocalDate.parse(to, DF).plusDays(1)); }
        if (hospitalId != null) { w.append(" AND sr.terminal_id = :hospitalId "); p.put("hospitalId", hospitalId); }
        String sql = "SELECT sr.terminal_id AS hospital_id, t.code AS hospital_code, t.name AS hospital_name, NULL AS hospital_level, " +
                " NULL AS city, NULL AS province, " +
                " COUNT(*) AS surgery_count, " +
                " COALESCE(SUM(srl.qty), 0) AS total_implants, " +
                " COUNT(DISTINCT srl.product_id) AS product_count, " +
                " COUNT(DISTINCT sr.doctor_name) AS doctor_count, " +
                " MAX(sr.surgery_date) AS last_surgery_at, " +
                " MIN(sr.surgery_date) AS first_surgery_at " +
                " FROM surgery_reports sr " +
                " LEFT JOIN hospitals t ON t.id = sr.terminal_id " +
                " LEFT JOIN surgery_report_lines srl ON srl.report_id = sr.id " +
                " WHERE 1=1 " + w +
                " GROUP BY sr.terminal_id, t.code, t.name " +
                " ORDER BY surgery_count DESC LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        bindParams(q, p);
        q.setParameter("limit", Math.max(1, Math.min(500, limit)));
        return ApiResponse.ok(toList(q, "hospitalId", "hospitalCode", "hospitalName", "hospitalLevel",
                "city", "province", "surgeryCount", "totalImplants", "productCount", "doctorCount",
                "lastSurgeryAt", "firstSurgeryAt"));
    }

    // 5. 应收款项
    @GetMapping("/receivables")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> receivables(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "100") int limit) {
        UUID tid = TenantContext.getTenantId();
        F f = buildCommon(from, to, dealerId, level, region, null, null);
        // 应收只看"未完成"订单
        String extra = " AND o.status NOT IN ('COMPLETED','CANCELLED','REJECTED') ";
        String sql = "SELECT o.dealer_id, d.code AS dealer_code, d.name AS dealer_name, d.level AS dealer_level, d.region_id, r.name AS region, " +
                " COUNT(*) AS unpaid_count, " +
                " COALESCE(SUM(o.amount_incl_tax), 0) AS total_receivable, " +
                " COALESCE(SUM(o.amount_incl_tax) FILTER (WHERE now() - o.created_at < INTERVAL '30 days'), 0) AS age30, " +
                " COALESCE(SUM(o.amount_incl_tax) FILTER (WHERE now() - o.created_at >= INTERVAL '30 days' AND now() - o.created_at < INTERVAL '60 days'), 0) AS age60, " +
                " COALESCE(SUM(o.amount_incl_tax) FILTER (WHERE now() - o.created_at >= INTERVAL '60 days' AND now() - o.created_at < INTERVAL '90 days'), 0) AS age90, " +
                " COALESCE(SUM(o.amount_incl_tax) FILTER (WHERE now() - o.created_at >= INTERVAL '90 days'), 0) AS age_over_90, " +
                " MAX(o.created_at) AS oldest_unpaid_at " +
                " FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id LEFT JOIN regions r ON r.id = d.region_id " +
                " WHERE o.tenant_id = :tenantId " + extra + f.w +
                " GROUP BY o.dealer_id, d.code, d.name, d.level, d.region_id, r.name " +
                " ORDER BY total_receivable DESC NULLS LAST LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter("tenantId", tid);
        bindParams(q, f.p);
        q.setParameter("limit", Math.max(1, Math.min(500, limit)));
        return ApiResponse.ok(toList(q, "dealerId", "dealerCode", "dealerName", "dealerLevel", "regionId", "region",
                "unpaidCount", "totalReceivable", "age30", "age60", "age90", "ageOver90", "oldestUnpaidAt"));
    }

    // 6. 订单追溯
    @GetMapping("/order-trace")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> orderTrace(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType,
            @RequestParam(defaultValue = "100") int limit) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder w = new StringBuilder(" AND o.tenant_id = :tenantId ");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tenantId", tid);
        if (from != null && !from.isBlank()) { w.append(" AND o.created_at >= :fromDate "); p.put("fromDate", LocalDate.parse(from, DF).atStartOfDay()); }
        if (to != null && !to.isBlank()) { w.append(" AND o.created_at < :toDate "); p.put("toDate", LocalDate.parse(to, DF).plusDays(1).atStartOfDay()); }
        if (dealerId != null) { w.append(" AND o.dealer_id = :dealerId "); p.put("dealerId", dealerId); }
        if (status != null && !status.isBlank()) { w.append(" AND o.status = :status "); p.put("status", status); }
        if (orderType != null && !orderType.isBlank()) { w.append(" AND o.order_type = :orderType "); p.put("orderType", orderType); }
        String sql = "SELECT o.id AS order_id, o.code AS order_code, o.order_type, " +
                " o.dealer_id, d.name AS dealer_name, " +
                " o.status AS approval_status, o.amount_incl_tax AS total_amount, " +
                " (SELECT COUNT(*) FROM order_lines ol WHERE ol.order_id = o.id) AS product_count, " +
                " o.created_at AS order_date, " +
                " (SELECT MIN(h.at_time) FROM order_status_history h WHERE h.order_id = o.id AND h.to_status='APPROVED') AS approved_at, " +
                " (SELECT MAX(so.created_at) FROM sales_outs so WHERE so.source_order_id = o.id) AS shipped_at, " +
                " (SELECT MAX(r.created_at) FROM receipts r WHERE r.ref_doc_id = o.id AND r.ref_doc_type = 'ORDER') AS received_at, " +
                " (SELECT MAX(so.status) FROM sales_outs so WHERE so.source_order_id = o.id) AS shipment_status " +
                " FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id " +
                " WHERE 1=1 " + w +
                " ORDER BY o.created_at DESC LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        bindParams(q, p);
        q.setParameter("limit", Math.max(1, Math.min(500, limit)));
        return ApiResponse.ok(toList(q, "orderId", "orderCode", "orderType", "dealerId", "dealerName",
                "approvalStatus", "totalAmount", "productCount", "orderDate",
                "approvedAt", "shippedAt", "receivedAt", "shipmentStatus"));
    }

    // 7. 报表概览
    @GetMapping("/overview")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> overview() {
        UUID tid = TenantContext.getTenantId();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalDealers", scalarLong("SELECT COUNT(*) FROM dealers WHERE tenant_id = :tid", tid));
        res.put("activeDealers", scalarLong("SELECT COUNT(*) FROM dealers WHERE tenant_id = :tid AND status='active'", tid));
        res.put("totalProducts", scalarLong("SELECT COUNT(*) FROM products WHERE tenant_id = :tid", tid));
        res.put("totalOrders", scalarLong("SELECT COUNT(*) FROM orders WHERE tenant_id = :tid", tid));
        res.put("monthOrders", scalarLong("SELECT COUNT(*) FROM orders WHERE tenant_id = :tid AND created_at >= date_trunc('month', now())", tid));
        res.put("totalSurgeryReports", scalarLong("SELECT COUNT(*) FROM surgery_reports WHERE tenant_id = :tid", tid));
        res.put("monthSales", scalarBd("SELECT COALESCE(SUM(amount_incl_tax),0) FROM orders WHERE tenant_id=:tid AND is_red IS NOT TRUE AND created_at >= date_trunc('month', now())", tid));
        res.put("qualifiedStock", scalarBd("SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = :tid AND stock_status='QUALIFIED'", tid));
        res.put("pendingStock", scalarBd("SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = :tid AND stock_status='PENDING'", tid));
        res.put("defectiveStock", scalarBd("SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = :tid AND stock_status='DEFECTIVE'", tid));
        return ApiResponse.ok(res);
    }

    // 8. 库存呆滞/超期（v4.1.1 新增）
    @GetMapping("/inventory-aging")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> inventoryAging(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(defaultValue = "180") int agingDays,
            @RequestParam(defaultValue = "100") int limit) {
        UUID tid = TenantContext.getTenantId();
        int safeDays = Math.max(30, Math.min(3650, agingDays));
        StringBuilder w = new StringBuilder(" WHERE inv.tenant_id = :tenantId AND inv.stock_status='QUALIFIED' AND inv.qty > 0 ");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tenantId", tid);
        p.put("agingDays", safeDays);
        if (categoryCode != null && !categoryCode.isBlank()) { w.append(" AND p.category_code = :categoryCode "); p.put("categoryCode", categoryCode); }
        if (dealerId != null) { w.append(" AND inv.dealer_id = :dealerId "); p.put("dealerId", dealerId); }
        String sql = "SELECT inv.id AS inventory_id, inv.product_id, p.code AS product_code, p.name_cn AS product_name, " +
                " p.spec AS product_spec, c.name AS category_name, " +
                " inv.batch_no, inv.serial_no, " +
                " inv.prod_date, inv.exp_date, inv.qty, " +
                " CASE WHEN inv.exp_date IS NULL THEN 99999 ELSE (inv.exp_date - current_date) END AS days_to_expire, " +
                " CASE WHEN inv.prod_date IS NULL THEN 0 ELSE (current_date - inv.prod_date) END AS age_days, " +
                " CASE " +
                "   WHEN inv.exp_date IS NOT NULL AND inv.exp_date < current_date THEN 'EXPIRED' " +
                "   WHEN inv.exp_date IS NOT NULL AND inv.exp_date - current_date <= 30 THEN 'NEAR_EXPIRE' " +
                "   WHEN inv.prod_date IS NOT NULL AND (current_date - inv.prod_date) >= :agingDays THEN 'STAGNANT' " +
                "   ELSE 'NORMAL' END AS age_bucket " +
                " FROM inventory inv " +
                " LEFT JOIN products p ON p.id = inv.product_id " +
                " LEFT JOIN product_categories c ON c.id = p.category_id " +
                " WHERE inv.tenant_id = :tenantId " +
                " ORDER BY age_days DESC LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        bindParams(q, p);
        q.setParameter("limit", Math.max(1, Math.min(500, limit)));
        return ApiResponse.ok(toList(q, "inventoryId", "productId", "productCode", "productName",
                "productSpec", "categoryName", "batchNo", "serialNo",
                "prodDate", "expDate", "qty", "daysToExpire", "ageDays", "ageBucket"));
    }

    // 9. 拒单率/审批时长（v4.1.1 新增）
    @GetMapping("/order-approval-stats")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> orderApprovalStats(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "200") int limit) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder w = new StringBuilder(" WHERE o.tenant_id = :tenantId AND o.is_red IS NOT TRUE ");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tenantId", tid);
        if (from != null && !from.isBlank()) { w.append(" AND o.created_at >= :fromDate "); p.put("fromDate", LocalDate.parse(from, DF).atStartOfDay()); }
        if (to != null && !to.isBlank()) { w.append(" AND o.created_at < :toDate "); p.put("toDate", LocalDate.parse(to, DF).plusDays(1).atStartOfDay()); }
        String sql = "SELECT o.id AS order_id, o.code AS order_code, " +
                " o.dealer_id, d.name AS dealer_name, " +
                " o.status AS current_status, " +
                " o.created_at AS submit_at, " +
                " (SELECT MIN(h.at_time) FROM order_status_history h WHERE h.order_id = o.id AND h.to_status='APPROVED') AS approved_at, " +
                " (SELECT MIN(h.at_time) FROM order_status_history h WHERE h.order_id = o.id AND h.to_status IN ('REJECTED','CANCELLED')) AS rejected_at, " +
                " CASE WHEN EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = o.id AND h.to_status IN ('REJECTED','CANCELLED')) THEN 'REJECTED' " +
                "      WHEN EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = o.id AND h.to_status='APPROVED') THEN 'APPROVED' " +
                "      ELSE o.status END AS result, " +
                " u.username AS approver_name " +
                " FROM orders o " +
                " LEFT JOIN dealers d ON d.id = o.dealer_id " +
                " LEFT JOIN order_status_history h ON h.order_id = o.id AND h.to_status='APPROVED' " +
                " LEFT JOIN users u ON u.id = h.operator_id " +
                " WHERE o.tenant_id = :tenantId AND o.is_red IS NOT TRUE " +
                " ORDER BY o.created_at DESC LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter("tenantId", tid);
        q.setParameter("limit", Math.max(1, Math.min(1000, limit)));
        return ApiResponse.ok(toList(q, "orderId", "orderCode", "dealerId", "dealerName",
                "currentStatus", "submitAt", "approvedAt", "rejectedAt", "result", "approverName"));
    }

    private long scalarLong(String sql, UUID tid) {
        try {
            var q = em.createNativeQuery(sql);
            q.setParameter("tid", tid);
            Object v = q.getSingleResult();
            return v == null ? 0 : ((Number) v).longValue();
        } catch (Exception e) { return 0; }
    }
    private BigDecimal scalarBd(String sql, UUID tid) {
        try {
            var q = em.createNativeQuery(sql);
            q.setParameter("tid", tid);
            Object v = q.getSingleResult();
            return v == null ? BigDecimal.ZERO : (BigDecimal) v;
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private List<Map<String, Object>> toList(Query q, String... keys) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            List<jakarta.persistence.TupleElement<?>> elems = t.getElements();
            for (int i = 0; i < elems.size() && i < keys.length; i++) {
                m.put(keys[i], t.get(i));
            }
            out.add(m);
        }
        return out;
    }

    // ============= v4.1.2 报表穿透子接口 =============

    // 订单明细子接口（产品销售 TOP10 / 库存穿透）
    @GetMapping("/product-sales-detail")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> productSalesDetail(
            @RequestParam Long productId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "100") int limit) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder w = new StringBuilder(" AND o.tenant_id = :tenantId AND ol.product_id = :productId ");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tenantId", tid);
        p.put("productId", productId);
        if (from != null && !from.isBlank()) { w.append(" AND o.created_at >= :fromDate "); p.put("fromDate", LocalDate.parse(from, DF).atStartOfDay()); }
        if (to != null && !to.isBlank()) { w.append(" AND o.created_at < :toDate "); p.put("toDate", LocalDate.parse(to, DF).plusDays(1).atStartOfDay()); }
        String sql = "SELECT ol.order_id AS orderId, o.code AS orderCode, o.status, " +
                " o.dealer_id AS dealerId, d.name AS dealerName, " +
                " ol.qty, ol.unit_price AS unitPrice, ol.sub_total AS subTotal, " +
                " o.amount_incl_tax AS orderTotal, " +
                " o.created_at AS orderDate " +
                " FROM order_lines ol " +
                " JOIN orders o ON o.id = ol.order_id " +
                " LEFT JOIN dealers d ON d.id = o.dealer_id " +
                " WHERE 1=1 " + w +
                " ORDER BY o.created_at DESC LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        bindParams(q, p);
        q.setParameter("limit", Math.max(1, Math.min(500, limit)));
        return ApiResponse.ok(toList(q, "orderId", "orderCode", "status", "dealerId", "dealerName",
                "qty", "unitPrice", "subTotal", "orderTotal", "orderDate"));
    }

    // 经销商订单明细子接口（销售业绩排行 / 应收穿透）
    @GetMapping("/dealer-orders")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> dealerOrders(
            @RequestParam Long dealerId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Boolean unpaidOnly,
            @RequestParam(defaultValue = "100") int limit) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder w = new StringBuilder(" AND o.tenant_id = :tenantId AND o.dealer_id = :dealerId ");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tenantId", tid);
        p.put("dealerId", dealerId);
        if (from != null && !from.isBlank()) { w.append(" AND o.created_at >= :fromDate "); p.put("fromDate", LocalDate.parse(from, DF).atStartOfDay()); }
        if (to != null && !to.isBlank()) { w.append(" AND o.created_at < :toDate "); p.put("toDate", LocalDate.parse(to, DF).plusDays(1).atStartOfDay()); }
        if (Boolean.TRUE.equals(unpaidOnly)) { w.append(" AND o.status NOT IN ('COMPLETED','CANCELLED','REJECTED') "); }
        String sql = "SELECT o.id AS orderId, o.code AS orderCode, o.status, o.order_type AS orderType, " +
                " o.amount_incl_tax AS totalAmount, o.final_amount AS finalAmount, " +
                " (SELECT COALESCE(SUM(ol.qty),0) FROM order_lines ol WHERE ol.order_id = o.id) AS productCount, " +
                " o.created_at AS orderDate, o.submitted_at AS submittedAt, o.approved_at AS approvedAt " +
                " FROM orders o " +
                " WHERE 1=1 " + w +
                " ORDER BY o.created_at DESC LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        bindParams(q, p);
        q.setParameter("limit", Math.max(1, Math.min(500, limit)));
        return ApiResponse.ok(toList(q, "orderId", "orderCode", "status", "orderType",
                "totalAmount", "finalAmount", "productCount", "orderDate", "submittedAt", "approvedAt"));
    }

    // 医院/终端的手术明细（手术报台统计穿透）
    @GetMapping("/hospital-surgery")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> hospitalSurgery(
            @RequestParam Long hospitalId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "100") int limit) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder w = new StringBuilder(" AND sr.tenant_id = :tenantId AND sr.terminal_id = :hospitalId ");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tenantId", tid);
        p.put("hospitalId", hospitalId);
        if (from != null && !from.isBlank()) { w.append(" AND sr.surgery_date >= :fromDate "); p.put("fromDate", LocalDate.parse(from, DF)); }
        if (to != null && !to.isBlank()) { w.append(" AND sr.surgery_date < :toDate "); p.put("toDate", LocalDate.parse(to, DF).plusDays(1)); }
        String sql = "SELECT sr.id AS reportId, sr.code, sr.surgery_date AS surgeryDate, " +
                " sr.patient_info AS patientInfo, sr.doctor_name AS doctorName, " +
                " sr.status, " +
                " (SELECT COALESCE(SUM(srl.qty),0) FROM surgery_report_lines srl WHERE srl.report_id = sr.id) AS implantQty, " +
                " sr.created_at AS createdAt " +
                " FROM surgery_reports sr " +
                " WHERE 1=1 " + w +
                " ORDER BY sr.surgery_date DESC LIMIT :limit";
        var q = em.createNativeQuery(sql, Tuple.class);
        bindParams(q, p);
        q.setParameter("limit", Math.max(1, Math.min(500, limit)));
        return ApiResponse.ok(toList(q, "reportId", "code", "surgeryDate", "patientInfo",
                "doctorName", "status", "implantQty", "createdAt"));
    }

    // 订单行（订单追溯点击行的子明细）
    @GetMapping("/order-detail-child/{orderId}")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> orderDetailChild(@PathVariable Long orderId) {
        UUID tid = TenantContext.getTenantId();
        // 行项目
        var q1 = em.createNativeQuery(
                "SELECT ol.id AS lineId, ol.product_id AS productId, p.code AS productCode, p.name_cn AS productName, " +
                " ol.qty, ol.unit_price AS unitPrice, ol.sub_total AS subTotal, ol.batch_no AS batchNo " +
                " FROM order_lines ol JOIN products p ON p.id = ol.product_id " +
                " WHERE ol.order_id = :oid ORDER BY ol.id", Tuple.class);
        q1.setParameter("oid", orderId);
        List<Map<String, Object>> lines = toList(q1, "lineId", "productId", "productCode", "productName",
                "qty", "unitPrice", "subTotal", "batchNo");
        // 状态历史
        var q2 = em.createNativeQuery(
                "SELECT id, from_status AS fromStatus, to_status AS toStatus, at_time AS atTime, comment AS remark " +
                " FROM order_status_history WHERE order_id = :oid ORDER BY at_time", Tuple.class);
        q2.setParameter("oid", orderId);
        List<Map<String, Object>> history = toList(q2, "id", "fromStatus", "toStatus", "atTime", "remark");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("lines", lines);
        res.put("history", history);
        return ApiResponse.ok(res);
    }

}