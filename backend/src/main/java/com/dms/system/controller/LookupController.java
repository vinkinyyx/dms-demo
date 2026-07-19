/*
 * 通用 Lookup 只读控制器：为前端下拉/选择器提供轻量数据源
 * 返回统一结构 {value, label, extra}，便于 Picker 组件消费
 */
package com.dms.system.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 前端下拉/选择器统一数据源。
 * 所有接口都：过滤当前租户、限制返回条数、支持 keyword 模糊搜索。
 */
@RestController
@RequestMapping("/api/lookups")
@RequiredArgsConstructor
public class LookupController {

    private final EntityManager em;

    /** 经销商 lookup */
    @GetMapping("/dealers")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> dealers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(genericLookup(
                "SELECT id, code, name, level, status FROM dealers",
                "code", "name",
                new String[]{"id", "code", "name", "level", "status"},
                keyword, limit, true, true));
    }

    /** 产品 lookup - 支持按经销商授权过滤（v3.4.5） */
    @GetMapping("/products")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> products(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(defaultValue = "50") int limit) {
        UUID tid = TenantContext.getTenantId();
        // v3.4.10: 与 AuthorizationService.check 语义一致 - 授权可以是 product_id 精确匹配 OR category 范围
        // v3.4.9: 增加 unit_type + 从 product_prices 取 sales_price（GLOBAL 兜底）
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT p.id, p.code, p.name_cn AS name, p.spec, p.unit, p.unit_type, " +
                "p.current_price AS price, " +
                "(SELECT sales_price FROM product_prices pp WHERE pp.product_id = p.id AND pp.partner_type='GLOBAL' " +
                " AND pp.tenant_id = p.tenant_id LIMIT 1) AS price_retail, " +
                "p.status FROM products p ");
        boolean withDealer = dealerId != null;
        if (withDealer) {
            sql.append("JOIN authorizations a ON a.tenant_id = p.tenant_id AND a.dealer_id = :did " +
                    "  AND COALESCE(a.status,'active') = 'active' " +
                    "  AND (a.auth_type IS NULL OR a.auth_type = 'ORDER') " +
                    "  AND (a.valid_from IS NULL OR a.valid_from <= CURRENT_DATE) " +
                    "  AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE) " +
                    "  AND ( " +
                    "     a.product_id IS NULL " +   /* 通配所有产品 */
                    "     OR a.product_id = p.id " +  /* 精确匹配 */
                    "     OR (a.category_ids IS NOT NULL AND a.category_ids <> '' " +
                    "         AND CAST(p.category_id AS text) = ANY(string_to_array(a.category_ids, ','))) " +
                    "  ) ");
        }
        sql.append("WHERE p.tenant_id = :tid AND p.deleted_at IS NULL ");
        if (keyword != null && !keyword.isBlank()) sql.append(" AND (p.code ILIKE :kw OR p.name_cn ILIKE :kw) ");
        sql.append(" ORDER BY p.code LIMIT :lim");
        var q = em.createNativeQuery(sql.toString(), Tuple.class);
        q.setParameter("tid", tid).setParameter("lim", limit);
        if (withDealer) q.setParameter("did", dealerId);
        if (keyword != null && !keyword.isBlank()) q.setParameter("kw", "%" + keyword + "%");
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("code", r.get("code"));
            m.put("name", r.get("name"));
            m.put("nameCn", r.get("name"));
            m.put("spec", r.get("spec"));
            m.put("unit", r.get("unit"));
            m.put("unitType", r.get("unit_type") == null ? "EA" : r.get("unit_type"));
            m.put("price", r.get("price"));
            m.put("priceRetail", r.get("price_retail"));
            m.put("status", r.get("status"));
            m.put("value", r.get("id"));
            String c = String.valueOf(r.get("code"));
            String n = String.valueOf(r.get("name"));
            m.put("label", c + " · " + n);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    /** v3.4.9: 供应商下拉 */
    @GetMapping("/suppliers")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> suppliers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "200") int limit) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder("SELECT id, code, name, contact_person, contact_phone, status " +
                "FROM suppliers WHERE tenant_id = :tid AND (deleted_at IS NULL) AND (status = 'active' OR status IS NULL) ");
        if (keyword != null && !keyword.isBlank()) sql.append(" AND (code ILIKE :kw OR name ILIKE :kw) ");
        sql.append(" ORDER BY code LIMIT :lim");
        var q = em.createNativeQuery(sql.toString(), Tuple.class);
        q.setParameter("tid", tid).setParameter("lim", limit);
        if (keyword != null && !keyword.isBlank()) q.setParameter("kw", "%" + keyword + "%");
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("code", r.get("code"));
            m.put("name", r.get("name"));
            m.put("contactPerson", r.get("contact_person") == null ? "" : r.get("contact_person"));
            m.put("contactPhone", r.get("contact_phone") == null ? "" : r.get("contact_phone"));
            m.put("status", r.get("status"));
            m.put("value", r.get("id"));
            m.put("label", r.get("code") + " · " + r.get("name"));
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    /** 医院/终端 lookup */
    @GetMapping("/hospitals")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> hospitals(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(genericLookup(
                "SELECT id, code, name, level, status FROM hospitals",
                "code", "name",
                new String[]{"id", "code", "name", "level", "status"},
                keyword, limit, true, true));
    }

    /** 仓库 lookup */
    @GetMapping("/warehouses")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> warehouses(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(genericLookup(
                "SELECT id, code, name, type, status FROM warehouses",
                "code", "name",
                new String[]{"id", "code", "name", "type", "status"},
                keyword, limit, true, true));
    }

    /** 产品分类 lookup */
    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> categories(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(genericLookup(
                "SELECT id, code, name FROM product_categories",
                "code", "name",
                new String[]{"id", "code", "name"},
                keyword, limit, true, true));
    }

    /** 区域 lookup */
    @GetMapping("/regions")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> regions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(genericLookup(
                "SELECT id, code, name, level FROM regions",
                "code", "name",
                new String[]{"id", "code", "name", "level"},
                keyword, limit, true));
    }

    /** 合同 lookup */
    @GetMapping("/contracts")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> contracts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(genericLookup(
                "SELECT id, code, category, status FROM contracts",
                "code", "code",
                new String[]{"id", "code", "category", "status"},
                keyword, limit, true));
    }

    /** 订单 lookup */
    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> orders(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(genericLookup(
                "SELECT id, code, order_type AS type, status, amount_incl_tax AS amount FROM orders",
                "code", "code",
                new String[]{"id", "code", "type", "status", "amount"},
                keyword, limit, true));
    }

    /** 组织单元 lookup */
    @GetMapping("/org-units")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> orgUnits(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(genericLookup(
                "SELECT id, code, name, type FROM org_units",
                "code", "name",
                new String[]{"id", "code", "name", "type"},
                keyword, limit, true));
    }

    /**
     * 通用 lookup 实现。
     * @param baseSql 基础 SELECT ... FROM ...（不含 WHERE）
     * @param codeCol 模糊搜索的编码列名
     * @param nameCol 模糊搜索的名称列名
     * @param outCols 输出的列（用于组装 Map）
     * @param keyword 关键字（对 code 与 name 做 LIKE）
     * @param limit 结果条数上限
     * @param filterTenant 是否加 tenant_id 过滤
     */
    private List<Map<String, Object>> genericLookup(
            String baseSql, String codeCol, String nameCol,
            String[] outCols, String keyword, int limit, boolean filterTenant) {
        return genericLookup(baseSql, codeCol, nameCol, outCols, keyword, limit, filterTenant, false);
    }

    private List<Map<String, Object>> genericLookup(
            String baseSql, String codeCol, String nameCol,
            String[] outCols, String keyword, int limit, boolean filterTenant, boolean filterDeleted) {
        StringBuilder sql = new StringBuilder(baseSql);
        Map<String, Object> params = new HashMap<>();
        List<String> conds = new ArrayList<>();
        if (filterTenant) {
            conds.add("tenant_id = :tid");
            params.put("tid", TenantContext.getTenantId());
        }
        if (filterDeleted) {
            conds.add("deleted_at IS NULL");
        }
        if (keyword != null && !keyword.isBlank()) {
            conds.add("(" + codeCol + " ILIKE :kw OR " + nameCol + " ILIKE :kw)");
            params.put("kw", "%" + keyword + "%");
        }
        if (!conds.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", conds));
        sql.append(" ORDER BY ").append(codeCol).append(" LIMIT :lim");
        params.put("lim", limit);

        var q = em.createNativeQuery(sql.toString(), Tuple.class);
        params.forEach(q::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (String col : outCols) m.put(col, r.get(col));
            // 统一 value/label 便于前端直接消费
            m.put("value", m.get("id"));
            String c = String.valueOf(m.getOrDefault("code", ""));
            String n = String.valueOf(m.getOrDefault("name", ""));
            m.put("label", c.equals(n) || n.isBlank() ? c : (c + " · " + n));
            list.add(m);
        }
        return list;
    }
}
