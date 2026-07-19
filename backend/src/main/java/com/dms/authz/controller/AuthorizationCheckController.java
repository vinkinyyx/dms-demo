/*
 * 授权预检 API：下单页在选中经销商 + 产品后调用
 * 返回该经销商当前是否有授权可下这些产品线的产品
 */
package com.dms.authz.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/authorizations")
@RequiredArgsConstructor
public class AuthorizationCheckController {

    private final EntityManager em;

    /**
     * 查询经销商当前有效的授权（产品分类 + 医院列表）
     * 用于下单页在选择经销商后限制可选产品
     */
    @GetMapping("/effective/{dealerId}")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> effective(@PathVariable Long dealerId) {
        UUID tid = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("dealerId", dealerId);
        res.put("checkedAt", today.toString());

        Set<String> allCategoryIds = new LinkedHashSet<>();
        Set<String> allTerminalIds = new LinkedHashSet<>();
        List<Map<String, Object>> effective = new ArrayList<>();

        try {
            var q = em.createNativeQuery(
                    "SELECT id, category_ids, product_lines, terminal_ids, valid_from, valid_to, status " +
                    "FROM authorizations " +
                    "WHERE tenant_id = ?1 AND dealer_id = ?2 AND status = 'active' " +
                    "  AND valid_from <= ?3 AND valid_to >= ?3 " +
                    "  AND deleted_at IS NULL", Tuple.class);
            q.setParameter(1, tid).setParameter(2, dealerId).setParameter(3, today);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.get("id"));
                String cat = safeStr(t.get("category_ids"));
                String pl = safeStr(t.get("product_lines"));
                String tm = safeStr(t.get("terminal_ids"));
                m.put("categoryIds", cat);
                m.put("productLines", pl);
                m.put("terminalIds", tm);
                m.put("validFrom", String.valueOf(t.get("valid_from")));
                m.put("validTo", String.valueOf(t.get("valid_to")));
                effective.add(m);
                addSplit(cat, allCategoryIds);
                addSplit(tm, allTerminalIds);
            }
        } catch (Exception ignored) {}

        res.put("effectiveCount", effective.size());
        res.put("effective", effective);
        res.put("categoryIds", new ArrayList<>(allCategoryIds));
        res.put("terminalIds", new ArrayList<>(allTerminalIds));
        res.put("hasEffective", !effective.isEmpty());

        return ApiResponse.ok(res);
    }

    /**
     * 下单前校验：给定产品列表，返回哪些产品该经销商可以下单
     * 按产品分类 category_id 检查
     */
    @PostMapping("/check-products")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> checkProducts(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long dealerId = toLong(body.get("dealerId"));
        @SuppressWarnings("unchecked")
        List<Object> productIds = (List<Object>) body.getOrDefault("productIds", Collections.emptyList());
        LocalDate today = LocalDate.now();

        Map<String, Object> res = new LinkedHashMap<>();

        // 1. 拿到经销商当前有效授权 → 产品分类集合
        Set<String> allowedCategories = new LinkedHashSet<>();
        try {
            var q = em.createNativeQuery(
                    "SELECT category_ids FROM authorizations " +
                    "WHERE tenant_id = ?1 AND dealer_id = ?2 AND status = 'active' " +
                    "  AND valid_from <= ?3 AND valid_to >= ?3 " +
                    "  AND deleted_at IS NULL");
            q.setParameter(1, tid).setParameter(2, dealerId).setParameter(3, today);
            List<?> rows = q.getResultList();
            for (Object r : rows) addSplit(safeStr(r), allowedCategories);
        } catch (Exception ignored) {}

        if (allowedCategories.isEmpty()) {
            res.put("hasAuthorization", false);
            res.put("message", "该经销商无有效授权");
            res.put("allowedCategories", Collections.emptyList());
            res.put("results", Collections.emptyList());
            return ApiResponse.ok(res);
        }

        // 2. 查产品的 category_id
        List<Map<String, Object>> results = new ArrayList<>();
        int authorized = 0;
        for (Object pidObj : productIds) {
            Long pid = toLong(pidObj);
            String categoryId = null;
            try {
                var q = em.createNativeQuery(
                        "SELECT category_id FROM products WHERE id = ?1 AND tenant_id = ?2");
                q.setParameter(1, pid).setParameter(2, tid);
                List<?> rs = q.getResultList();
                if (!rs.isEmpty() && rs.get(0) != null) categoryId = String.valueOf(rs.get(0));
            } catch (Exception ignored) {}

            boolean ok = categoryId != null && allowedCategories.contains(categoryId);
            if (ok) authorized++;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("productId", pid);
            r.put("categoryId", categoryId);
            r.put("authorized", ok);
            r.put("reason", ok ? "OK" : "该产品所属分类 [" + categoryId + "] 不在授权范围");
            results.add(r);
        }

        res.put("hasAuthorization", true);
        res.put("allowedCategories", new ArrayList<>(allowedCategories));
        res.put("total", productIds.size());
        res.put("authorized", authorized);
        res.put("results", results);
        return ApiResponse.ok(res);
    }

    private void addSplit(String v, Set<String> target) {
        if (v == null || v.isEmpty()) return;
        for (String s : v.split("[,，]")) {
            String ss = s.trim();
            if (!ss.isEmpty()) target.add(ss);
        }
    }

    private String safeStr(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o);
        return "null".equals(s) ? "" : s;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
