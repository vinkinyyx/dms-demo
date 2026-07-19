/*
 * 首页仪表盘数据 API (v3.4)
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

@RestController("v34DashboardController")
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final EntityManager em;

    /**
     * v3.4.7: 根据 period 生成 orders 表的时间过滤 SQL 片段（如 " AND created_at >= ..."）
     */
    private String periodFilter(String period, String colPrefix) {
        if (period == null || period.isBlank() || "all".equalsIgnoreCase(period)) return "";
        String col = (colPrefix == null ? "" : colPrefix + ".") + "created_at";
        switch (period) {
            case "today":   return " AND " + col + " >= date_trunc('day', now())";
            case "week":    return " AND " + col + " >= date_trunc('week', now())";
            case "month":   return " AND " + col + " >= date_trunc('month', now())";
            case "quarter": return " AND " + col + " >= date_trunc('quarter', now())";
            case "year":    return " AND " + col + " >= date_trunc('year', now())";
            default:        return "";
        }
    }
    private String orderCommonFilter(String period, Long dealerId, String status, String orderType, String prefix) {
        StringBuilder sb = new StringBuilder(periodFilter(period, prefix));
        String p = (prefix == null ? "" : prefix + ".");
        if (dealerId != null) sb.append(" AND ").append(p).append("dealer_id = ").append(dealerId);
        if (status != null && !status.isBlank()) sb.append(" AND ").append(p).append("status = '").append(status.replace("'","''")).append("'");
        if (orderType != null && !orderType.isBlank()) sb.append(" AND ").append(p).append("order_type = '").append(orderType.replace("'","''")).append("'");
        return sb.toString();
    }

    /**
     * KPI 卡片数据 - v3.4.7 支持筛选
     */
    @GetMapping("/kpi")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> kpi(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType) {
        UUID tid = TenantContext.getTenantId();
        String flt = orderCommonFilter(period, dealerId, status, orderType, null);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalSales", scalarBd(
                "SELECT COALESCE(SUM(amount_incl_tax),0) FROM orders WHERE tenant_id = ?1 AND (is_red IS NULL OR is_red = false)" + flt, tid));
        res.put("totalOrders", scalarLong("SELECT COUNT(*) FROM orders WHERE tenant_id = ?1" + flt, tid));
        res.put("activeDealers", scalarLong(
                "SELECT COUNT(*) FROM dealers WHERE tenant_id = ?1 AND (status = 'active' OR status IS NULL)", tid));
        res.put("totalProducts", scalarLong("SELECT COUNT(*) FROM products WHERE tenant_id = ?1", tid));
        res.put("qualifiedStock", scalarBd(
                "SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = ?1 AND stock_status='QUALIFIED'", tid));
        res.put("pendingStock", scalarBd(
                "SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = ?1 AND stock_status='PENDING'", tid));
        res.put("defectiveStock", scalarBd(
                "SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = ?1 AND stock_status='DEFECTIVE'", tid));
        res.put("totalSurgeries", scalarLong("SELECT COUNT(*) FROM surgery_reports WHERE tenant_id = ?1", tid));
        return ApiResponse.ok(res);
    }

    /**
     * 库存三状态占比（环形图数据）
     */
    @GetMapping("/inventory-pie")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> inventoryPie(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT stock_status, COALESCE(SUM(qty),0) AS total FROM inventory " +
                "WHERE tenant_id = ?1 GROUP BY stock_status", Tuple.class);
        q.setParameter(1, tid);
        return ApiResponse.ok(collect(q, "name", "value"));
    }

    /**
     * 近 12 个月销售趋势
     */
    @GetMapping("/sales-trend")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> salesTrend(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType) {
        UUID tid = TenantContext.getTenantId();
        String flt = orderCommonFilter(period, dealerId, status, orderType, null);
        var q = em.createNativeQuery(
                "SELECT TO_CHAR(created_at, 'YYYY-MM') AS ym, " +
                "COALESCE(SUM(amount_incl_tax),0) AS amount, " +
                "COUNT(*) AS cnt " +
                "FROM orders WHERE tenant_id = ?1 " +
                "  AND created_at >= now() - INTERVAL '12 months' " + flt +
                "GROUP BY ym ORDER BY ym", Tuple.class);
        q.setParameter(1, tid);
        return ApiResponse.ok(collect(q, "month", "amount", "count"));
    }

    /**
     * 订单状态漏斗
     */
    @GetMapping("/order-funnel")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> orderFunnel(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType) {
        UUID tid = TenantContext.getTenantId();
        String flt = orderCommonFilter(period, dealerId, status, orderType, null);
        var q = em.createNativeQuery(
                "SELECT COALESCE(status,'UNKNOWN') AS status, COUNT(*) AS cnt " +
                "FROM orders WHERE tenant_id = ?1 " + flt + " GROUP BY status " +
                "ORDER BY CASE COALESCE(status,'UNKNOWN') " +
                "  WHEN 'DRAFT' THEN 1 WHEN 'SUBMITTED' THEN 2 WHEN 'APPROVED' THEN 3 " +
                "  WHEN 'SHIPPED' THEN 4 WHEN 'COMPLETED' THEN 5 ELSE 9 END", Tuple.class);
        q.setParameter(1, tid);
        return ApiResponse.ok(collect(q, "name", "value"));
    }

    /**
     * 销售 TOP 5 经销商
     */
    @GetMapping("/top-dealers")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> topDealers(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType) {
        UUID tid = TenantContext.getTenantId();
        String flt = orderCommonFilter(period, dealerId, status, orderType, "o");
        // 如果用户没有指定 period，默认限制为本月
        String timeFilter = (period == null || period.isBlank()) ? "  AND o.created_at >= DATE_TRUNC('month', now()) " : "";
        var q = em.createNativeQuery(
                "SELECT COALESCE(d.name, CAST(o.dealer_id AS TEXT)) AS dealer_name, " +
                "COALESCE(SUM(o.amount_incl_tax),0) AS amount " +
                "FROM orders o LEFT JOIN dealers d ON d.id = o.dealer_id " +
                "WHERE o.tenant_id = ?1 " + timeFilter + flt +
                "GROUP BY o.dealer_id, d.name " +
                "ORDER BY amount DESC LIMIT 5", Tuple.class);
        q.setParameter(1, tid);
        return ApiResponse.ok(collect(q, "name", "value"));
    }

    /**
     * TOP 医院手术数
     */
    @GetMapping("/top-hospitals")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> topHospitals(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT COALESCE(h.name, CAST(sr.terminal_id AS TEXT)) AS name, COUNT(*) AS value " +
                "FROM surgery_reports sr LEFT JOIN hospitals h ON h.id = sr.terminal_id " +
                "WHERE sr.tenant_id = ?1 GROUP BY sr.terminal_id, h.name " +
                "ORDER BY value DESC LIMIT 10", Tuple.class);
        q.setParameter(1, tid);
        return ApiResponse.ok(collect(q, "name", "value"));
    }

    /**
     * 最近 7 天销售/手术/入库对比
     */
    @GetMapping("/activity-7d")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> activity7d(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType) {
        UUID tid = TenantContext.getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orders", getDailyCount("orders", tid));
        data.put("surgeries", getDailyCount("surgery_reports", tid));
        data.put("receipts", getDailyCount("receipts", tid));
        return ApiResponse.ok(data);
    }

    private List<Map<String, Object>> getDailyCount(String table, UUID tid) {
        var q = em.createNativeQuery(
                "SELECT TO_CHAR(created_at, 'MM-DD') AS d, COUNT(*) AS c " +
                "FROM " + table + " WHERE tenant_id = ?1 AND created_at >= now() - INTERVAL '7 days' " +
                "GROUP BY d ORDER BY d", Tuple.class);
        q.setParameter(1, tid);
        return collect(q, "date", "count");
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
    private List<Map<String, Object>> collect(jakarta.persistence.Query q, String... keys) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) m.put(keys[i], t.get(i));
            out.add(m);
        }
        return out;
    }
}
