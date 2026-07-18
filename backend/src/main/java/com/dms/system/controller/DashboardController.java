/*
 * 综合仪表盘 + 库存统计卡 + 待办列表
 * 覆盖 US-C-09, US-B-15/17, US-HOME-04
 */
package com.dms.system.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final EntityManager em;

    /**
     * 综合看板（US-C-09）
     * 汇集 KPI + 分布 + Top 榜 + 库存告警
     */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();

        // 总量卡片
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("orders",   safeCount("SELECT COUNT(*) FROM orders WHERE tenant_id = ?1", tenantId));
        totals.put("dealers",  safeCount("SELECT COUNT(*) FROM dealers WHERE tenant_id = ?1", tenantId));
        totals.put("products", safeCount("SELECT COUNT(*) FROM products WHERE tenant_id = ?1", tenantId));
        totals.put("contracts",safeCount("SELECT COUNT(*) FROM contracts WHERE tenant_id = ?1", tenantId));
        totals.put("salesOuts",safeCount("SELECT COUNT(*) FROM sales_outs WHERE tenant_id = ?1", tenantId));
        data.put("totals", totals);

        // 订单状态分布
        data.put("orderStatusDist", queryDist(
                "SELECT status AS k, COUNT(*) AS v FROM orders WHERE tenant_id = ?1 GROUP BY status", tenantId));

        // 合同状态分布
        data.put("contractStatusDist", queryDist(
                "SELECT status AS k, COUNT(*) AS v FROM contracts WHERE tenant_id = ?1 GROUP BY status", tenantId));

        // Top 5 经销商（按订单金额）
        data.put("topDealers", queryTop(
                "SELECT d.name AS name, COALESCE(SUM(o.amount_incl_tax),0) AS value " +
                "FROM orders o JOIN dealers d ON d.id = o.dealer_id " +
                "WHERE o.tenant_id = ?1 GROUP BY d.name ORDER BY value DESC LIMIT 5", tenantId));

        // Top 5 产品（按销售数量）
        data.put("topProducts", queryTop(
                "SELECT p.name_cn AS name, COALESCE(SUM(sl.qty),0) AS value " +
                "FROM sales_out_lines sl JOIN products p ON p.id = sl.product_id " +
                "JOIN sales_outs s ON s.id = sl.sales_out_id " +
                "WHERE s.tenant_id = ?1 GROUP BY p.name_cn ORDER BY value DESC LIMIT 5", tenantId));

        // 库存告警（3 张卡）
        data.put("inventoryStats", inventoryStatsInternal(tenantId));

        return ApiResponse.ok(data);
    }

    /**
     * 库存统计（US-B-17）
     * 3 张卡：库存总量 / 临期库存（30 天内到期） / 呆滞库存（90 天无事务）
     */
    @GetMapping("/inventory-stats")
    public ApiResponse<Map<String, Object>> inventoryStats() {
        return ApiResponse.ok(inventoryStatsInternal(TenantContext.getTenantId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<String, Object> inventoryStatsInternal(UUID tenantId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 总库存量
        stats.put("totalQty", safeSum(
                "SELECT COALESCE(SUM(qty), 0) FROM inventory WHERE tenant_id = ?1", tenantId));

        // 临期库存（30 天内到期）
        stats.put("expiringQty", safeSum(
                "SELECT COALESCE(SUM(qty), 0) FROM inventory " +
                "WHERE tenant_id = ?1 AND exp_date IS NOT NULL AND exp_date <= (CURRENT_DATE + 30)", tenantId));

        // 呆滞库存（90 天无事务）
        stats.put("staleQty", safeSum(
                "SELECT COALESCE(SUM(i.qty), 0) FROM inventory i " +
                "WHERE i.tenant_id = ?1 AND NOT EXISTS (" +
                "  SELECT 1 FROM inventory_transactions t " +
                "  WHERE t.product_id = i.product_id AND t.warehouse_id = i.warehouse_id " +
                "  AND t.at_time > (now() - INTERVAL '90 days')" +
                ")", tenantId));

        // 记录条数
        stats.put("records", safeCount(
                "SELECT COUNT(*) FROM inventory WHERE tenant_id = ?1", tenantId));

        return stats;
    }

    /**
     * 待办列表（US-HOME-04）
     * 从多个业务模块聚合出当前登录用户/租户下的待办
     */
    @GetMapping("/todos")
    public ApiResponse<Map<String, Object>> todos() {
        UUID tenantId = TenantContext.getTenantId();
        List<Map<String, Object>> items = new ArrayList<>();

        // 待审批订单
        addTodos(items, tenantId,
                "SELECT id, code, 'ORDER' AS type, '订单待审批' AS title, status, created_at " +
                "FROM orders WHERE tenant_id = ?1 AND status = 'SUBMITTED' " +
                "ORDER BY created_at DESC LIMIT 10",
                "/workspace.html?menu=orders");

        // 待审批合同申请
        addTodos(items, tenantId,
                "SELECT id, code, 'CONTRACT_APP' AS type, '合同申请待审批' AS title, status, created_at " +
                "FROM contract_applications WHERE tenant_id = ?1 AND status = 'SUBMITTED' " +
                "ORDER BY created_at DESC LIMIT 10",
                "/workspace.html?menu=contract-apps");

        // 临期库存告警
        try {
            var q = em.createNativeQuery(
                    "SELECT COUNT(*) FROM inventory " +
                    "WHERE tenant_id = ?1 AND exp_date IS NOT NULL AND exp_date <= (CURRENT_DATE + 30)");
            q.setParameter(1, tenantId);
            long expCount = ((Number) q.getSingleResult()).longValue();
            if (expCount > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "INV_EXPIRING");
                m.put("title", "临期库存告警");
                m.put("count", expCount);
                m.put("link", "/workspace.html?menu=inventory");
                items.add(m);
            }
        } catch (Exception ignored) {}

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", items.size());
        data.put("items", items);
        return ApiResponse.ok(data);
    }

    // ============ 私有方法 ============

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void addTodos(List<Map<String, Object>> out, UUID tenantId, String sql, String link) {
        try {
            var q = em.createNativeQuery(sql, Tuple.class);
            q.setParameter(1, tenantId);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.get("id"));
                m.put("code", t.get("code"));
                m.put("type", t.get("type"));
                m.put("title", t.get("title"));
                m.put("status", t.get("status"));
                m.put("createdAt", String.valueOf(t.get("created_at")));
                m.put("link", link);
                out.add(m);
            }
        } catch (Exception ignored) {}
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long safeCount(String sql, UUID tenantId) {
        try {
            var q = em.createNativeQuery(sql);
            q.setParameter(1, tenantId);
            return ((Number) q.getSingleResult()).longValue();
        } catch (Exception e) { return 0L; }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public java.math.BigDecimal safeSum(String sql, UUID tenantId) {
        try {
            var q = em.createNativeQuery(sql);
            q.setParameter(1, tenantId);
            Object r = q.getSingleResult();
            return r == null ? java.math.BigDecimal.ZERO :
                   new java.math.BigDecimal(String.valueOf(r));
        } catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Map<String, Object>> queryDist(String sql, UUID tenantId) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            var q = em.createNativeQuery(sql, Tuple.class);
            q.setParameter(1, tenantId);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", t.get("k"));
                m.put("value", t.get("v"));
                list.add(m);
            }
        } catch (Exception ignored) {}
        return list;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Map<String, Object>> queryTop(String sql, UUID tenantId) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            var q = em.createNativeQuery(sql, Tuple.class);
            q.setParameter(1, tenantId);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", t.get("name"));
                m.put("value", t.get("value"));
                list.add(m);
            }
        } catch (Exception ignored) {}
        return list;
    }
}
