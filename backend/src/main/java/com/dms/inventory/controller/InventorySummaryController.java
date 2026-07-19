/*
 * 下单页面辅助接口
 * - 按产品ID聚合库存（可用/临期/呆滞）
 * - 经销商概览（本月订单+授信额度模拟）
 */
package com.dms.inventory.controller;

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

/**
 * 下单实时库存查询
 */
@RestController
@RequestMapping("/api/inventory-summary")
@RequiredArgsConstructor
public class InventorySummaryController {

    private final EntityManager em;

    /**
     * 按产品 ID 聚合库存
     *
     * 返回：
     *   totalQty    - 全部可用库存
     *   expiringQty - 30 天内到期
     *   staleQty    - 90 天无出入（呆滞）
     *   byWarehouse - 按仓库分组明细
     */
    @GetMapping("/by-product/{productId}")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> byProduct(@PathVariable Long productId) {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", productId);

        // 总量
        data.put("totalQty",    sum("SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = ?1 AND product_id = ?2", tenantId, productId));
        // 临期（30 天内）
        data.put("expiringQty", sum("SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = ?1 AND product_id = ?2 AND exp_date IS NOT NULL AND exp_date <= (CURRENT_DATE + 30)", tenantId, productId));
        // 呆滞（90 天无事务）
        data.put("staleQty",    sum(
                "SELECT COALESCE(SUM(i.qty),0) FROM inventory i " +
                "WHERE i.tenant_id = ?1 AND i.product_id = ?2 AND NOT EXISTS (" +
                "  SELECT 1 FROM inventory_transactions t " +
                "  WHERE t.product_id = i.product_id AND t.warehouse_id = i.warehouse_id " +
                "  AND t.at_time > (now() - INTERVAL '90 days')" +
                ")", tenantId, productId));

        // 按仓库明细
        List<Map<String, Object>> byWh = new ArrayList<>();
        try {
            var q = em.createNativeQuery(
                    "SELECT i.warehouse_id AS wid, w.code AS w_code, w.name AS w_name, " +
                    "       SUM(i.qty) AS qty, MIN(i.exp_date) AS earliest_exp " +
                    "FROM inventory i LEFT JOIN warehouses w ON w.id = i.warehouse_id " +
                    "WHERE i.tenant_id = ?1 AND i.product_id = ?2 " +
                    "GROUP BY i.warehouse_id, w.code, w.name ORDER BY qty DESC", Tuple.class);
            q.setParameter(1, tenantId).setParameter(2, productId);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("warehouseId", t.get("wid"));
                m.put("warehouseCode", t.get("w_code"));
                m.put("warehouseName", t.get("w_name"));
                m.put("qty", t.get("qty"));
                m.put("earliestExpDate", String.valueOf(t.get("earliest_exp")));
                byWh.add(m);
            }
        } catch (Exception ignored) {}
        data.put("byWarehouse", byWh);

        // 参考单价（从 products 表取当前价）
        try {
            var q = em.createNativeQuery(
                    "SELECT code, name_cn, spec, unit, current_price, tax_rate " +
                    "FROM products WHERE id = ?1", Tuple.class);
            q.setParameter(1, productId);
            @SuppressWarnings("unchecked")
            List<Tuple> rs = q.getResultList();
            if (!rs.isEmpty()) {
                Tuple t = rs.get(0);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("code", t.get("code"));
                info.put("nameCn", t.get("name_cn"));
                info.put("spec", t.get("spec"));
                info.put("unit", t.get("unit"));
                info.put("currentPrice", t.get("current_price"));
                info.put("taxRate", t.get("tax_rate"));
                data.put("productInfo", info);
            }
        } catch (Exception ignored) {}

        return ApiResponse.ok(data);
    }

    /**
     * 经销商概览：本月订单数 + 授信额度模拟
     */
    @GetMapping("/dealer-overview/{dealerId}")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> dealerOverview(@PathVariable Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dealerId", dealerId);

        // 经销商基本信息
        try {
            var q = em.createNativeQuery(
                    "SELECT code, name, level, contact_name, contact_phone, gsp_status, status " +
                    "FROM dealers WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
            q.setParameter(1, dealerId).setParameter(2, tenantId);
            @SuppressWarnings("unchecked")
            List<Tuple> rs = q.getResultList();
            if (!rs.isEmpty()) {
                Tuple t = rs.get(0);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("code", t.get("code"));
                info.put("name", t.get("name"));
                info.put("level", t.get("level"));
                info.put("contactName", t.get("contact_name"));
                info.put("contactPhone", t.get("contact_phone"));
                info.put("gspStatus", t.get("gsp_status"));
                info.put("status", t.get("status"));
                data.put("dealerInfo", info);
            }
        } catch (Exception ignored) {}

        // 本月订单
        data.put("monthOrders", cnt(
                "SELECT COUNT(*) FROM orders WHERE tenant_id = ?1 AND dealer_id = ?2 " +
                "AND created_at >= date_trunc('month', now())", tenantId, dealerId));
        data.put("monthAmount", sum(
                "SELECT COALESCE(SUM(amount_incl_tax),0) FROM orders WHERE tenant_id = ?1 AND dealer_id = ?2 " +
                "AND created_at >= date_trunc('month', now())", tenantId, dealerId));

        // 累计订单
        data.put("totalOrders", cnt(
                "SELECT COUNT(*) FROM orders WHERE tenant_id = ?1 AND dealer_id = ?2", tenantId, dealerId));
        data.put("totalAmount", sum(
                "SELECT COALESCE(SUM(amount_incl_tax),0) FROM orders WHERE tenant_id = ?1 AND dealer_id = ?2", tenantId, dealerId));

        // 待付款金额（模拟：以 SUBMITTED / APPROVED 状态计）
        BigDecimal pendingAmount = sum(
                "SELECT COALESCE(SUM(amount_incl_tax),0) FROM orders WHERE tenant_id = ?1 AND dealer_id = ?2 " +
                "AND status IN ('SUBMITTED','APPROVED')", tenantId, dealerId);
        data.put("pendingAmount", pendingAmount);

        // 授信额度模拟（一级 50 万，二级 20 万）
        BigDecimal creditLimit;
        try {
            var q = em.createNativeQuery("SELECT level FROM dealers WHERE id = ?1");
            q.setParameter(1, dealerId);
            String level = String.valueOf(q.getSingleResult());
            creditLimit = "T1".equals(level) ? new BigDecimal("500000") : new BigDecimal("200000");
        } catch (Exception e) {
            creditLimit = new BigDecimal("100000");
        }
        data.put("creditLimit", creditLimit);
        data.put("remainingCredit", creditLimit.subtract(pendingAmount).max(BigDecimal.ZERO));

        // 最近 5 单
        List<Map<String, Object>> recentOrders = new ArrayList<>();
        try {
            var q = em.createNativeQuery(
                    "SELECT id, code, order_type, amount_incl_tax, status, created_at " +
                    "FROM orders WHERE tenant_id = ?1 AND dealer_id = ?2 " +
                    "ORDER BY created_at DESC LIMIT 5", Tuple.class);
            q.setParameter(1, tenantId).setParameter(2, dealerId);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.get("id"));
                m.put("code", t.get("code"));
                m.put("orderType", t.get("order_type"));
                m.put("amount", t.get("amount_incl_tax"));
                m.put("status", t.get("status"));
                m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
                recentOrders.add(m);
            }
        } catch (Exception ignored) {}
        data.put("recentOrders", recentOrders);

        return ApiResponse.ok(data);
    }

    private BigDecimal sum(String sql, Object... params) {
        try {
            var q = em.createNativeQuery(sql);
            for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
            Object r = q.getSingleResult();
            return r == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(r));
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private long cnt(String sql, Object... params) {
        try {
            var q = em.createNativeQuery(sql);
            for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
            return ((Number) q.getSingleResult()).longValue();
        } catch (Exception e) { return 0L; }
    }
}
