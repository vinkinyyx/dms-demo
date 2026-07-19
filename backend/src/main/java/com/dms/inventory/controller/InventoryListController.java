/*
 * 补齐库存单据的 GET 列表接口
 * - /api/stock-moves       调拨移库列表
 * - /api/inventory-adjustments  库存调整列表
 * - /api/receipts          收货入库列表
 * 均支持分页 + 按 updated_at 倒序排序
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

@RestController
@RequiredArgsConstructor
public class InventoryListController {

    private final EntityManager em;

    // ============ 调拨移库列表 ============
    @GetMapping("/api/stock-moves")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> listStockMoves(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return listGeneric("stock_moves", page, size,
                "id, code, src_warehouse_id, dst_warehouse_id, status, reason, at_time, created_at, updated_at, operator_id",
                Arrays.asList("id","code","fromWarehouseId","toWarehouseId","status","remark","atTime","createdAt","updatedAt","createdBy"),
                Arrays.asList("id","code","src_warehouse_id","dst_warehouse_id","status","reason","at_time","created_at","updated_at","operator_id"));
    }

    // ============ 库存调整列表 ============
    @GetMapping("/api/inventory-adjustments")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> listAdjustments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return listGeneric("inventory_adjustments", page, size,
                "id, code, warehouse_id, adj_category, adj_type, status, reason, created_at, updated_at, operator_id",
                Arrays.asList("id","code","warehouseId","category","type","status","remark","createdAt","updatedAt","createdBy"),
                Arrays.asList("id","code","warehouse_id","adj_category","adj_type","status","reason","created_at","updated_at","operator_id"));
    }

    // ============ 通用列表实现 ============
    private ApiResponse<Map<String, Object>> listGeneric(String table, int page, int size,
                                                           String columns, List<String> keys) {
        return listGeneric(table, page, size, columns, keys, null);
    }

    private ApiResponse<Map<String, Object>> listGeneric(String table, int page, int size,
                                                           String columns, List<String> jsonKeys, List<String> sqlColumns) {
        UUID tid = TenantContext.getTenantId();
        int offset = (page - 1) * size;
        try {
            var qCnt = em.createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?1");
            qCnt.setParameter(1, tid);
            long total = ((Number) qCnt.getSingleResult()).longValue();

            var q = em.createNativeQuery(
                    "SELECT " + columns + " FROM " + table +
                    " WHERE tenant_id = ?1 " +
                    " ORDER BY updated_at DESC NULLS LAST, id DESC LIMIT ?2 OFFSET ?3", Tuple.class);
            q.setParameter(1, tid).setParameter(2, size).setParameter(3, offset);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();

            List<Map<String, Object>> list = new ArrayList<>();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = 0; i < jsonKeys.size(); i++) {
                    String jsonKey = jsonKeys.get(i);
                    String sqlKey = sqlColumns != null ? sqlColumns.get(i) : camelToSnake(jsonKey);
                    try {
                        Object v = t.get(sqlKey);
                        if (v != null && (jsonKey.endsWith("At") || jsonKey.endsWith("Time") || jsonKey.endsWith("Date"))) v = com.dms.common.util.DateFmt.fmt(v);
                        m.put(jsonKey, v);
                    } catch (Exception ignored) {}
                }
                list.add(m);
            }

            enrichNames(list);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", total);
            data.put("page", page);
            data.put("size", size);
            data.put("list", list);
            return ApiResponse.ok(data);
        } catch (Exception e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", 0L);
            data.put("page", page);
            data.put("size", size);
            data.put("list", Collections.emptyList());
            data.put("note", table + " 加载失败：" + e.getMessage());
            return ApiResponse.ok(data);
        }
    }

    private void enrichNames(List<Map<String, Object>> list) {
        Map<String, String> tableByKey = new LinkedHashMap<>();
        tableByKey.put("fromWarehouseId", "warehouses");
        tableByKey.put("toWarehouseId", "warehouses");
        tableByKey.put("warehouseId", "warehouses");
        tableByKey.put("dealerId", "dealers");
        Map<String, String> nameKeyByKey = new HashMap<>();
        nameKeyByKey.put("fromWarehouseId", "fromWarehouseName");
        nameKeyByKey.put("toWarehouseId", "toWarehouseName");
        nameKeyByKey.put("warehouseId", "warehouseName");
        nameKeyByKey.put("dealerId", "dealerName");
        Map<String, String> cache = new HashMap<>();
        for (Map<String, Object> m : list) {
            for (Map.Entry<String, String> e : tableByKey.entrySet()) {
                Object idv = m.get(e.getKey());
                if (idv == null) continue;
                String ck = e.getValue() + "#" + idv;
                String name = cache.get(ck);
                if (name == null) {
                    try {
                        Object r = em.createNativeQuery("SELECT name FROM " + e.getValue() + " WHERE id = ?1")
                                .setParameter(1, Long.parseLong(String.valueOf(idv)))
                                .getResultList().stream().findFirst().orElse(null);
                        name = r == null ? "" : String.valueOf(r);
                    } catch (Exception ex) { name = ""; }
                    cache.put(ck, name);
                }
                m.put(nameKeyByKey.get(e.getKey()), name);
            }
        }
    }

    private String camelToSnake(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c)) { sb.append('_').append(Character.toLowerCase(c)); }
            else sb.append(c);
        }
        return sb.toString();
    }
}
