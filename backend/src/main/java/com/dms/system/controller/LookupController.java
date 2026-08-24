/*
 * 通用 Lookup 只读控制器：为前端下拉/选择器提供轻量数据源
 * 返回统一结构 {value, label, extra}，便于 Picker 组件消费
 */
package com.dms.system.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import com.dms.common.util.PagingUtil;
import com.dms.security.DataScope;
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
    private final DataScope dataScope;

    /** 经销商 lookup */
    @GetMapping("/dealers")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> dealers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        java.util.Set<Long> allowed = dataScope.accessibleDealerIds();
        if (allowed != null && allowed.isEmpty()) {
            return ApiResponse.ok(java.util.Collections.emptyList());
        }
        String baseSql = "SELECT id, code, name, level, status FROM dealers";
        StringBuilder extra = new StringBuilder();
        if (allowed != null) {
            extra.append(" id IN (");
            extra.append(String.join(",", allowed.stream().map(String::valueOf).toList()));
            extra.append(")");
        }
        return ApiResponse.ok(genericLookup(
                baseSql,
                "code", "name",
                new String[]{"id", "code", "name", "level", "status"},
                keyword, limit, true, true, extra.toString()));
    }

    /** 产品 lookup - 支持按经销商授权过滤（v3.4.5） */
    @GetMapping("/products")
    @Transactional(readOnly = true)
    public ApiResponse<?> products(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "false") boolean excludeBundle) {
        UUID tid = TenantContext.getTenantId();
        boolean paged = page != null && size != null && size > 0;
        int pageSize = paged ? size : limit;
        int safePage = PagingUtil.normalizePage(page == null ? 1 : page); int safeSize = PagingUtil.normalizeSize(size == null ? limit : size); int offset = paged ? (safePage - 1) * safeSize : 0;
        // v3.7.9: 模糊搜索增加规格 spec；支持分页 page/size 返回 {total,list}；默认上限 500
        StringBuilder from = new StringBuilder(" FROM products p ");
        boolean withDealer = dealerId != null;
        // 降级：若该经销商没有任何有效 ORDER 授权记录，返回全部在售产品，避免因缺授权数据阻断下单/报台。
        if (withDealer) {
            var authCheck = em.createNativeQuery(
                    "SELECT COUNT(1) FROM authorizations a WHERE a.tenant_id = :tid AND a.dealer_id = :did " +
                    "  AND COALESCE(a.status,'active') = 'active' " +
                    "  AND (a.auth_type IS NULL OR a.auth_type = 'ORDER') " +
                    "  AND (a.valid_from IS NULL OR a.valid_from <= CURRENT_DATE) " +
                    "  AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)");
            authCheck.setParameter("tid", tid);
            authCheck.setParameter("did", dealerId);
            long authCount = ((Number) authCheck.getSingleResult()).longValue();
            if (authCount == 0) {
                withDealer = false;
            }
        }
        if (withDealer) {
            from.append("JOIN authorizations a ON a.tenant_id = p.tenant_id AND a.dealer_id = :did " +
                    "  AND COALESCE(a.status,'active') = 'active' " +
                    "  AND (a.auth_type IS NULL OR a.auth_type = 'ORDER') " +
                    "  AND (a.valid_from IS NULL OR a.valid_from <= CURRENT_DATE) " +
                    "  AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE) " +
                    "  AND ( " +
                    "     a.product_id IS NULL " +
                    "     OR a.product_id = p.id " +
                    "     OR (a.category_ids IS NOT NULL AND a.category_ids <> '' " +
                    "         AND CAST(p.category_id AS text) = ANY(string_to_array(a.category_ids, ','))) " +
                    "  ) ");
        }
        String where = "WHERE p.tenant_id = :tid AND p.deleted_at IS NULL " +
                ((keyword != null && !keyword.isBlank()) ? " AND (p.code ILIKE :kw OR p.name_cn ILIKE :kw OR p.spec ILIKE :kw) " : "")
                + (excludeBundle ? " AND NOT EXISTS(SELECT 1 FROM product_bundles pb WHERE pb.tenant_id = p.tenant_id AND pb.product_id = p.id AND pb.version_status = 'active' AND pb.deleted_at IS NULL) " : "");
        String selectCols = "DISTINCT p.id, p.code, p.name_cn AS name, p.spec, p.unit, p.unit_type, " +
                "p.current_price AS price, " +
                "(SELECT sales_price FROM product_prices pp WHERE pp.product_id = p.id AND pp.partner_type='GLOBAL' " +
                " AND pp.tenant_id = p.tenant_id LIMIT 1) AS price_retail, " +
                "p.is_serial_managed, p.status, " +
                "EXISTS(SELECT 1 FROM product_bundles pb WHERE pb.tenant_id = p.tenant_id AND pb.product_id = p.id AND pb.version_status = 'active' AND pb.deleted_at IS NULL LIMIT 1) AS is_bom";

        long total = 0;
        if (paged) {
            var cq = em.createNativeQuery("SELECT COUNT(DISTINCT p.id) " + from + where);
            cq.setParameter("tid", tid);
            if (withDealer) cq.setParameter("did", dealerId);
            if (keyword != null && !keyword.isBlank()) cq.setParameter("kw", "%" + keyword + "%");
            total = ((Number) cq.getSingleResult()).longValue();
        }

        var q = em.createNativeQuery("SELECT " + selectCols + from + where + " ORDER BY p.code LIMIT :lim OFFSET :off", Tuple.class);
        q.setParameter("tid", tid).setParameter("lim", pageSize).setParameter("off", offset);
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
            m.put("isSerialManaged", r.get("is_serial_managed"));
            m.put("status", r.get("status"));
            m.put("isBom", Boolean.TRUE.equals(r.get("is_bom")));
            m.put("value", r.get("id"));
            m.put("label", String.valueOf(r.get("code")) + " 路 " + String.valueOf(r.get("name")));
            out.add(m);
        }
        if (paged) {
            Map<String, Object> pg = new LinkedHashMap<>();
            pg.put("total", total);
            pg.put("page", page);
            pg.put("size", safeSize);
            pg.put("list", out);
            return ApiResponse.ok(pg);
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

    /** 产品层次 lookup */
    @GetMapping("/product-lines")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> productLines(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(genericLookup(
                "SELECT id, code, name, level, status FROM product_lines",
                "code", "name",
                new String[]{"id", "code", "name", "level", "status"},
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

    /** 发货单 lookup（供销退单选择原发货单，支持按时间范围/经销商/批号多维搜索） */
    @GetMapping("/sales-outs")
    @Transactional(readOnly = true)
    public ApiResponse<?> salesOuts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String productCode,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UUID tid = TenantContext.getTenantId();
        boolean paged = page != null && size != null && size > 0;
        int pageSize = paged ? size : limit;
        int safePage = page == null || page < 1 ? 1 : page;
        int offset = paged ? (safePage - 1) * pageSize : 0;

        boolean hasBatch = batchNo != null && !batchNo.isBlank();
        boolean hasProduct = productCode != null && !productCode.isBlank();
        boolean hasLineFilter = hasBatch || hasProduct;

        StringBuilder from = new StringBuilder(
                "FROM sales_outs so LEFT JOIN dealers d ON d.id = so.dealer_id " +
                "LEFT JOIN warehouses w ON w.id = so.warehouse_id ");
        if (hasLineFilter) {
            from.append("LEFT JOIN sales_out_batches sob ON sob.sales_out_id = so.id AND COALESCE(sob.status,'CONFIRMED') <> 'CANCELLED' ");
            from.append("LEFT JOIN sales_out_batch_lines sol ON sol.batch_id = sob.id ");
            if (hasProduct) {
                from.append("LEFT JOIN products p ON p.id = sol.product_id ");
            }
        }

        StringBuilder where = new StringBuilder("WHERE so.tenant_id = :tid AND COALESCE(so.is_red,false) = false AND so.deleted_at IS NULL ");
        if (status != null && !status.isBlank()) {
            where.append(" AND so.status = :status ");
        } else {
            where.append(" AND so.status IN ('COMPLETED','PARTIAL_SHIPPED','SHIPPED') ");
        }
        if (dealerId != null) where.append(" AND so.dealer_id = :dealer ");
        if (dateFrom != null && !dateFrom.isBlank()) where.append(" AND so.shipped_at >= :df ");
        if (dateTo != null && !dateTo.isBlank()) where.append(" AND so.shipped_at < (:dt)::date + INTERVAL '1 day' ");
        if (keyword != null && !keyword.isBlank()) where.append(" AND (so.code ILIKE :kw OR d.name ILIKE :kw) ");
        if (hasBatch) where.append(" AND sol.batch_no ILIKE :bno ");
        if (hasProduct) where.append(" AND (p.code ILIKE :pcode OR p.name_cn ILIKE :pcode) ");

        long total = 0;
        if (paged) {
            String countSql = "SELECT COUNT(DISTINCT so.id) " + from + where;
            var cq = em.createNativeQuery(countSql);
            cq.setParameter("tid", tid);
            if (status != null && !status.isBlank()) cq.setParameter("status", status);
            if (dealerId != null) cq.setParameter("dealer", dealerId);
            if (dateFrom != null && !dateFrom.isBlank()) cq.setParameter("df", java.sql.Date.valueOf(dateFrom));
            if (dateTo != null && !dateTo.isBlank()) cq.setParameter("dt", java.sql.Date.valueOf(dateTo));
            if (keyword != null && !keyword.isBlank()) cq.setParameter("kw", "%" + keyword + "%");
            if (hasBatch) cq.setParameter("bno", "%" + batchNo + "%");
            if (hasProduct) cq.setParameter("pcode", "%" + productCode + "%");
            total = ((Number) cq.getSingleResult()).longValue();
        }

        String sql = "SELECT DISTINCT so.id, so.code, so.status, so.business_type, so.dealer_id, d.name AS dealer_name, " +
                "so.warehouse_id, w.name AS warehouse_name, so.amount_incl_tax AS amount, so.shipped_at, so.created_at " +
                from + where + " ORDER BY so.id DESC LIMIT :lim OFFSET :off";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter("tid", tid).setParameter("lim", pageSize).setParameter("off", offset);
        if (status != null && !status.isBlank()) q.setParameter("status", status);
        if (dealerId != null) q.setParameter("dealer", dealerId);
        if (dateFrom != null && !dateFrom.isBlank()) q.setParameter("df", java.sql.Date.valueOf(dateFrom));
        if (dateTo != null && !dateTo.isBlank()) q.setParameter("dt", java.sql.Date.valueOf(dateTo));
        if (keyword != null && !keyword.isBlank()) q.setParameter("kw", "%" + keyword + "%");
        if (hasBatch) q.setParameter("bno", "%" + batchNo + "%");
        if (hasProduct) q.setParameter("pcode", "%" + productCode + "%");

        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("code", r.get("code"));
            m.put("status", r.get("status"));
            m.put("businessType", r.get("business_type"));
            m.put("dealerId", r.get("dealer_id"));
            m.put("dealerName", r.get("dealer_name"));
            m.put("warehouseId", r.get("warehouse_id"));
            m.put("warehouseName", r.get("warehouse_name"));
            m.put("amount", r.get("amount"));
            m.put("shippedAt", r.get("shipped_at"));
            m.put("createdAt", r.get("created_at"));
            m.put("value", r.get("id"));
            m.put("label", r.get("code") + " · " + (r.get("dealer_name") == null ? "" : r.get("dealer_name")));
            out.add(m);
        }
        if (paged) {
            Map<String, Object> pg = new LinkedHashMap<>();
            pg.put("total", total);
            pg.put("page", safePage);
            pg.put("size", pageSize);
            pg.put("list", out);
            return ApiResponse.ok(pg);
        }
        return ApiResponse.ok(out);
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
        return genericLookup(baseSql, codeCol, nameCol, outCols, keyword, limit, filterTenant, filterDeleted, null);
    }

    private List<Map<String, Object>> genericLookup(
            String baseSql, String codeCol, String nameCol,
            String[] outCols, String keyword, int limit, boolean filterTenant, boolean filterDeleted,
            String extraWhere) {
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
        if (extraWhere != null && !extraWhere.isBlank()) {
            conds.add("(" + extraWhere + ")");
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


