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
                keyword, limit, true));
    }

    /** 产品 lookup */
    @GetMapping("/products")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> products(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(genericLookup(
                "SELECT id, code, name_cn AS name, spec, unit, current_price AS price, status FROM products",
                "code", "name_cn",
                new String[]{"id", "code", "name", "spec", "unit", "price", "status"},
                keyword, limit, true));
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
                keyword, limit, true));
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
                keyword, limit, true));
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
                keyword, limit, true));
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
        StringBuilder sql = new StringBuilder(baseSql);
        Map<String, Object> params = new HashMap<>();
        List<String> conds = new ArrayList<>();
        if (filterTenant) {
            conds.add("tenant_id = :tid");
            params.put("tid", TenantContext.getTenantId());
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
