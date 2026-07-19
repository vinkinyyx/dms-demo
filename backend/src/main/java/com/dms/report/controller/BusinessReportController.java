/*
 * 常规业务报表 Controller (v3.3 R6)
 *   /api/reports/sales-ranking      经销商销售业绩排行
 *   /api/reports/product-top10      产品销售 TOP10
 *   /api/reports/inventory-turnover 库存周转
 *   /api/reports/surgery-stats      手术报台统计
 *   /api/reports/receivables        应收账款
 */
package com.dms.report.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class BusinessReportController {

    private final EntityManager em;

    // 1. 经销商销售业绩排行 - 增加：编码、级别、订单均值、审批率、最近下单时间
    @GetMapping("/sales-ranking")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> salesRanking(
            @RequestParam(defaultValue = "20") int limit) {
        UUID tid = TenantContext.getTenantId();
        String sql = "SELECT o.dealer_id, d.code AS dealer_code, d.name AS dealer_name, d.level AS dealer_level, " +
                "COUNT(*) AS order_count, " +
                "COALESCE(SUM(o.amount_incl_tax), 0) AS total_amount, " +
                "COALESCE(AVG(o.amount_incl_tax), 0) AS avg_amount, " +
                "SUM(CASE WHEN o.status IN ('APPROVED','COMPLETED') THEN 1 ELSE 0 END) AS approved_count, " +
                "SUM(CASE WHEN o.status = 'DRAFT' THEN 1 ELSE 0 END) AS draft_count, " +
                "MAX(o.created_at) AS last_order_at " +
                "FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id " +
                "WHERE o.tenant_id = ?1 " +
                "GROUP BY o.dealer_id, d.code, d.name, d.level " +
                "ORDER BY total_amount DESC LIMIT ?2";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter(1, tid).setParameter(2, limit);
        return ApiResponse.ok(toList(q, "dealerId", "dealerCode", "dealerName", "dealerLevel",
                "orderCount", "totalAmount", "avgAmount", "approvedCount", "draftCount", "lastOrderAt"));
    }

    // 2. 产品销售 TOP - 增加：编码、规格、单位、订单数、经销商数、单价
    @GetMapping("/product-top10")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> productTop10(
            @RequestParam(defaultValue = "10") int limit) {
        UUID tid = TenantContext.getTenantId();
        String sql = "SELECT ol.product_id, p.code AS product_code, p.name_cn AS product_name, " +
                "p.spec AS product_spec, p.unit AS product_unit, " +
                "SUM(ol.qty) AS total_qty, " +
                "COALESCE(SUM(ol.sub_total), 0) AS total_amount, " +
                "COUNT(DISTINCT ol.order_id) AS order_count, " +
                "COUNT(DISTINCT o.dealer_id) AS dealer_count, " +
                "COALESCE(AVG(ol.unit_price), 0) AS avg_unit_price " +
                "FROM order_lines ol " +
                "JOIN orders o ON o.id = ol.order_id " +
                "LEFT JOIN products p ON p.id = ol.product_id " +
                "WHERE o.tenant_id = ?1 " +
                "GROUP BY ol.product_id, p.code, p.name_cn, p.spec, p.unit " +
                "ORDER BY total_amount DESC NULLS LAST LIMIT ?2";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter(1, tid).setParameter(2, limit);
        return ApiResponse.ok(toList(q, "productId", "productCode", "productName", "productSpec", "productUnit",
                "totalQty", "totalAmount", "orderCount", "dealerCount", "avgUnitPrice"));
    }

    // 3. 库存周转 - 增加：合格/待检/不合格分状态数量、批次数、周转天数
    @GetMapping("/inventory-turnover")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> inventoryTurnover(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "20") int limit) {
        UUID tid = TenantContext.getTenantId();
        int safeDays = Math.max(1, Math.min(3650, days));
        String sql = "SELECT inv.product_id, p.code AS product_code, p.name_cn AS product_name, " +
                "p.spec AS product_spec, p.unit AS product_unit, " +
                "COALESCE(SUM(inv.qty), 0) AS current_stock, " +
                "COALESCE(SUM(CASE WHEN inv.stock_status='QUALIFIED' THEN inv.qty ELSE 0 END), 0) AS qualified_stock, " +
                "COALESCE(SUM(CASE WHEN inv.stock_status='PENDING' THEN inv.qty ELSE 0 END), 0) AS pending_stock, " +
                "COALESCE(SUM(CASE WHEN inv.stock_status='DEFECTIVE' THEN inv.qty ELSE 0 END), 0) AS defective_stock, " +
                "COUNT(DISTINCT inv.batch_no) AS batch_count, " +
                "COALESCE(( SELECT SUM(-qty_change) FROM inventory_transactions t " +
                "  WHERE t.tenant_id = inv.tenant_id AND t.product_id = inv.product_id " +
                "  AND t.txn_type IN ('SALES_OUT','SURGERY_USE') " +
                "  AND t.at_time >= now() - INTERVAL '" + safeDays + " days' " +
                "), 0) AS recent_out_qty, " +
                "CASE WHEN COALESCE(( SELECT SUM(-qty_change) FROM inventory_transactions t " +
                "  WHERE t.tenant_id = inv.tenant_id AND t.product_id = inv.product_id " +
                "  AND t.txn_type IN ('SALES_OUT','SURGERY_USE') " +
                "  AND t.at_time >= now() - INTERVAL '" + safeDays + " days' " +
                "), 0) > 0 " +
                "THEN ROUND(COALESCE(SUM(inv.qty),0) / (COALESCE(( SELECT SUM(-qty_change) FROM inventory_transactions t " +
                "  WHERE t.tenant_id = inv.tenant_id AND t.product_id = inv.product_id " +
                "  AND t.txn_type IN ('SALES_OUT','SURGERY_USE') " +
                "  AND t.at_time >= now() - INTERVAL '" + safeDays + " days' " +
                "), 1) / " + safeDays + "), 1) ELSE NULL END AS days_of_stock " +
                "FROM inventory inv " +
                "LEFT JOIN products p ON p.id = inv.product_id " +
                "WHERE inv.tenant_id = ?1 " +
                "GROUP BY inv.product_id, p.code, p.name_cn, p.spec, p.unit, inv.tenant_id " +
                "ORDER BY current_stock DESC LIMIT ?2";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter(1, tid).setParameter(2, limit);
        return ApiResponse.ok(toList(q, "productId", "productCode", "productName", "productSpec", "productUnit",
                "currentStock", "qualifiedStock", "pendingStock", "defectiveStock",
                "batchCount", "recentOutQty", "daysOfStock"));
    }

    // 4. 手术报台统计 - 增加：编码、级别、经销商数、医生数、平均植入数、最近手术日
    @GetMapping("/surgery-stats")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> surgeryStats(
            @RequestParam(defaultValue = "20") int limit) {
        UUID tid = TenantContext.getTenantId();
        String sql = "SELECT sr.terminal_id, h.code AS hospital_code, h.name AS hospital_name, " +
                "h.level AS hospital_level, " +
                "COUNT(*) AS surgery_count, " +
                "COALESCE(SUM(l.qty),0) AS total_implants, " +
                "COUNT(DISTINCT sr.dealer_id) AS dealer_count, " +
                "COUNT(DISTINCT sr.doctor_name) AS doctor_count, " +
                "CAST(COALESCE(SUM(l.qty),0) AS numeric) / NULLIF(COUNT(*),0) AS avg_implants_per_surgery, " +
                "MAX(sr.surgery_date) AS last_surgery_date " +
                "FROM surgery_reports sr " +
                "LEFT JOIN surgery_report_lines l ON l.report_id = sr.id " +
                "LEFT JOIN hospitals h ON h.id = sr.terminal_id " +
                "WHERE sr.tenant_id = ?1 " +
                "GROUP BY sr.terminal_id, h.code, h.name, h.level " +
                "ORDER BY surgery_count DESC LIMIT ?2";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter(1, tid).setParameter(2, limit);
        return ApiResponse.ok(toList(q, "terminalId", "hospitalCode", "hospitalName", "hospitalLevel",
                "surgeryCount", "totalImplants", "dealerCount", "doctorCount",
                "avgImplantsPerSurgery", "lastSurgeryDate"));
    }

    // 5. 应收账款 - 增加：编码、级别、订单均值、账龄分档、最早未收日期
    @GetMapping("/receivables")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> receivables(
            @RequestParam(defaultValue = "20") int limit) {
        UUID tid = TenantContext.getTenantId();
        String sql = "SELECT o.dealer_id, d.code AS dealer_code, d.name AS dealer_name, d.level AS dealer_level, " +
                "COUNT(*) AS unpaid_count, " +
                "COALESCE(SUM(o.amount_incl_tax),0) AS total_receivable, " +
                "COALESCE(AVG(o.amount_incl_tax),0) AS avg_order_amount, " +
                "SUM(CASE WHEN o.created_at < now() - INTERVAL '30 days' THEN o.amount_incl_tax ELSE 0 END) AS aged_30, " +
                "SUM(CASE WHEN o.created_at < now() - INTERVAL '60 days' THEN o.amount_incl_tax ELSE 0 END) AS aged_60, " +
                "SUM(CASE WHEN o.created_at < now() - INTERVAL '90 days' THEN o.amount_incl_tax ELSE 0 END) AS aged_90, " +
                "MIN(o.created_at) AS earliest_unpaid " +
                "FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id " +
                "WHERE o.tenant_id = ?1 " +
                "  AND o.status IN ('APPROVED','SHIPPED','DRAFT','SUBMITTED') " +
                "GROUP BY o.dealer_id, d.code, d.name, d.level " +
                "ORDER BY total_receivable DESC LIMIT ?2";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter(1, tid).setParameter(2, limit);
        return ApiResponse.ok(toList(q, "dealerId", "dealerCode", "dealerName", "dealerLevel",
                "unpaidCount", "totalReceivable", "avgOrderAmount",
                "aged30", "aged60", "aged90", "earliestUnpaid"));
    }

    // 6. 报表概览
    @GetMapping("/overview")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> overview() {
        UUID tid = TenantContext.getTenantId();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalDealers", scalarLong("SELECT COUNT(*) FROM dealers WHERE tenant_id = ?1", tid));
        res.put("totalProducts", scalarLong("SELECT COUNT(*) FROM products WHERE tenant_id = ?1", tid));
        res.put("totalOrders", scalarLong("SELECT COUNT(*) FROM orders WHERE tenant_id = ?1", tid));
        res.put("totalSurgeryReports", scalarLong("SELECT COUNT(*) FROM surgery_reports WHERE tenant_id = ?1", tid));
        res.put("qualifiedStock", scalarBd(
                "SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = ?1 AND stock_status='QUALIFIED'", tid));
        res.put("pendingStock", scalarBd(
                "SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = ?1 AND stock_status='PENDING'", tid));
        res.put("defectiveStock", scalarBd(
                "SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = ?1 AND stock_status='DEFECTIVE'", tid));
        return ApiResponse.ok(res);
    }

    private long scalarLong(String sql, UUID tid) {
        try {
            var q = em.createNativeQuery(sql);
            q.setParameter(1, tid);
            Object v = q.getSingleResult();
            return v == null ? 0 : ((Number) v).longValue();
        } catch (Exception e) { return 0; }
    }
    private BigDecimal scalarBd(String sql, UUID tid) {
        try {
            var q = em.createNativeQuery(sql);
            q.setParameter(1, tid);
            Object v = q.getSingleResult();
            return v == null ? BigDecimal.ZERO : (BigDecimal) v;
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private List<Map<String, Object>> toList(jakarta.persistence.Query q, String... keys) {
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
}
