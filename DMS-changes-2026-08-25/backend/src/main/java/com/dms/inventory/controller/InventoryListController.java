/*
 * 补齐库存单据的 GET 列表接口
 * - /api/stock-moves            调拨移库列表
 * - /api/inventory-adjustments  库存调整列表
 * 均支持分页 + 全字段过滤 + 按 updated_at 倒序排序
 */
package com.dms.inventory.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import com.dms.common.util.PagingUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String createdAtFrom,
            @RequestParam(required = false) String createdAtTo,
            @RequestParam(required = false) String updatedAtFrom,
            @RequestParam(required = false) String updatedAtTo,
            @RequestParam(required = false) String moveType,
            @RequestParam(required = false) String fromWarehouseName,
            @RequestParam(required = false) String toWarehouseName,
            @RequestParam(required = false) String fromStockStatus,
            @RequestParam(required = false) String toStockStatus) {
        Map<String, Object> f = new HashMap<>();
        f.put("id", id);
        f.put("code", code);
        f.put("status", status);
        f.put("createdAtFrom", createdAtFrom);
        f.put("createdAtTo", createdAtTo);
        f.put("updatedAtFrom", updatedAtFrom);
        f.put("updatedAtTo", updatedAtTo);
        f.put("moveType", moveType);
        f.put("fromWarehouseName", fromWarehouseName);
        f.put("toWarehouseName", toWarehouseName);
        f.put("fromStockStatus", fromStockStatus);
        f.put("toStockStatus", toStockStatus);
        return listGeneric("stock_moves", "sm", page, size, sort, f,
                "sm.id, sm.code, sm.src_warehouse_id, sm.dst_warehouse_id, sm.move_type, sm.from_stock_status, sm.to_stock_status, sm.status, sm.reason, sm.at_time, sm.created_at, sm.updated_at, sm.operator_id",
                Arrays.asList("id","code","fromWarehouseId","toWarehouseId","moveType","fromStockStatus","toStockStatus","status","remark","atTime","createdAt","updatedAt","createdBy"),
                Arrays.asList("id","code","src_warehouse_id","dst_warehouse_id","move_type","from_stock_status","to_stock_status","status","reason","at_time","created_at","updated_at","operator_id"),
                true);
    }

    // ============ 库存调整列表 ============
    @GetMapping("/api/inventory-adjustments")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> listAdjustments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String createdAtFrom,
            @RequestParam(required = false) String createdAtTo,
            @RequestParam(required = false) String updatedAtFrom,
            @RequestParam(required = false) String updatedAtTo,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type) {
        Map<String, Object> f = new HashMap<>();
        f.put("id", id);
        f.put("code", code);
        f.put("status", status);
        f.put("createdAtFrom", createdAtFrom);
        f.put("createdAtTo", createdAtTo);
        f.put("updatedAtFrom", updatedAtFrom);
        f.put("updatedAtTo", updatedAtTo);
        f.put("category", category);
        f.put("type", type);
        return listGeneric("inventory_adjustments", "ia", page, size, sort, f,
                "ia.id, ia.code, ia.warehouse_id, ia.adj_category, ia.adj_type, ia.status, ia.reason, ia.created_at, ia.updated_at, ia.operator_id",
                Arrays.asList("id","code","warehouseId","category","type","status","remark","createdAt","updatedAt","createdBy"),
                Arrays.asList("id","code","warehouse_id","adj_category","adj_type","status","reason","created_at","updated_at","operator_id"),
                false);
    }

    // ============ 通用列表实现 ============
    private ApiResponse<Map<String, Object>> listGeneric(String table, String alias, int page, int size, String sort,
                                                          Map<String, Object> filters,
                                                          String columns, List<String> jsonKeys, List<String> sqlColumns,
                                                          boolean isStockMove) {
        UUID tid = TenantContext.getTenantId();
        int safePage = PagingUtil.normalizePage(page);
        int safeSize = PagingUtil.normalizeSize(size);
        int offset = (safePage - 1) * safeSize;
        try {
            StringBuilder joins = new StringBuilder();
            StringBuilder where = new StringBuilder(" WHERE ").append(alias).append(".tenant_id = ?1");
            List<Object> params = new ArrayList<>();
            params.add(tid);
            int idx = 2;

            Object idVal = filters.get("id");
            if (idVal != null) {
                where.append(" AND ").append(alias).append(".id = ?").append(idx++);
                params.add(Long.valueOf(idVal.toString().trim()));
            }
            String code = asString(filters.get("code"));
            if (code != null) {
                where.append(" AND ").append(alias).append(".code ILIKE ?").append(idx++);
                params.add("%" + code + "%");
            }
            String status = asString(filters.get("status"));
            if (status != null) {
                where.append(" AND ").append(alias).append(".status = ?").append(idx++);
                params.add(status);
            }
            String createdAtFrom = asString(filters.get("createdAtFrom"));
            if (createdAtFrom != null) {
                java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(createdAtFrom, true);
                if (__t != null) { where.append(" AND ").append(alias).append(".created_at >= ?").append(idx++); params.add(__t); }
            }
            String createdAtTo = asString(filters.get("createdAtTo"));
            if (createdAtTo != null) {
                java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(createdAtTo, false);
                if (__t != null) {
                    where.append(com.dms.common.util.SpecUtil.hasTime(createdAtTo)
                            ? " AND " + alias + ".created_at <= ?"
                            : " AND " + alias + ".created_at < ?").append(idx++);
                    params.add(__t);
                }
            }
            String updatedAtFrom = asString(filters.get("updatedAtFrom"));
            if (updatedAtFrom != null) {
                java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(updatedAtFrom, true);
                if (__t != null) { where.append(" AND ").append(alias).append(".updated_at >= ?").append(idx++); params.add(__t); }
            }
            String updatedAtTo = asString(filters.get("updatedAtTo"));
            if (updatedAtTo != null) {
                java.sql.Timestamp __t = com.dms.common.util.SpecUtil.rangeBound(updatedAtTo, false);
                if (__t != null) {
                    where.append(com.dms.common.util.SpecUtil.hasTime(updatedAtTo)
                            ? " AND " + alias + ".updated_at <= ?"
                            : " AND " + alias + ".updated_at < ?").append(idx++);
                    params.add(__t);
                }
            }

            if (isStockMove) {
                String moveType = asString(filters.get("moveType"));
                if (moveType != null) {
                    where.append(" AND ").append(alias).append(".move_type = ?").append(idx++);
                    params.add(moveType);
                }
                String fromWarehouseName = asString(filters.get("fromWarehouseName"));
                if (fromWarehouseName != null) {
                    joins.append(" LEFT JOIN warehouses fw ON fw.id = ").append(alias).append(".src_warehouse_id");
                    where.append(" AND fw.name ILIKE ?").append(idx++);
                    params.add("%" + fromWarehouseName + "%");
                }
                String toWarehouseName = asString(filters.get("toWarehouseName"));
                if (toWarehouseName != null) {
                    joins.append(" LEFT JOIN warehouses tw ON tw.id = ").append(alias).append(".dst_warehouse_id");
                    where.append(" AND tw.name ILIKE ?").append(idx++);
                    params.add("%" + toWarehouseName + "%");
                }
                String fromStockStatus = asString(filters.get("fromStockStatus"));
                if (fromStockStatus != null) {
                    where.append(" AND ").append(alias).append(".from_stock_status = ?").append(idx++);
                    params.add(fromStockStatus);
                }
                String toStockStatus = asString(filters.get("toStockStatus"));
                if (toStockStatus != null) {
                    where.append(" AND ").append(alias).append(".to_stock_status = ?").append(idx++);
                    params.add(toStockStatus);
                }
            } else {
                String category = asString(filters.get("category"));
                if (category != null) {
                    where.append(" AND ").append(alias).append(".adj_category = ?").append(idx++);
                    params.add(category);
                }
                String type = asString(filters.get("type"));
                if (type != null) {
                    where.append(" AND ").append(alias).append(".adj_type = ?").append(idx++);
                    params.add(type);
                }
            }

            String countSql = "SELECT COUNT(*) FROM " + table + " " + alias + joins + where;
            var qCnt = em.createNativeQuery(countSql);
            for (int i = 0; i < params.size(); i++) qCnt.setParameter(i + 1, params.get(i));
            long total = ((Number) qCnt.getSingleResult()).longValue();

            String orderSql = buildOrderSql(alias, sort, isStockMove);

            String limitParam = "?" + idx++;
            String offsetParam = "?" + idx++;
            String dataSql = "SELECT " + columns + " FROM " + table + " " + alias + joins + where
                    + orderSql + " LIMIT " + limitParam + " OFFSET " + offsetParam;
            var q = em.createNativeQuery(dataSql, Tuple.class);
            for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
            q.setParameter(params.size() + 1, safeSize);
            q.setParameter(params.size() + 2, offset);

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
                        if (v != null && (jsonKey.endsWith("At") || jsonKey.endsWith("Time") || jsonKey.endsWith("Date")))
                            v = com.dms.common.util.DateFmt.fmt(v);
                        m.put(jsonKey, v);
                    } catch (Exception ignored) {}
                }
                list.add(m);
            }

            enrichNames(list);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", total);
            data.put("page", safePage);
            data.put("size", safeSize);
            data.put("list", list);
            return ApiResponse.ok(data);
        } catch (Exception e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", 0L);
            data.put("page", page);
            data.put("size", safeSize);
            data.put("list", Collections.emptyList());
            data.put("note", table + " 加载失败：" + e.getMessage());
            return ApiResponse.ok(data);
        }
    }

    private String buildOrderSql(String alias, String sort, boolean isStockMove) {
        String defaultOrder = " ORDER BY " + alias + ".updated_at DESC NULLS LAST, " + alias + ".id DESC";
        if (sort == null || sort.isBlank()) return defaultOrder;
        String[] sp = sort.split(",");
        String f = sp[0].trim();
        String dir = sp.length > 1 && "asc".equalsIgnoreCase(sp[1].trim()) ? "ASC" : "DESC";
        Set<String> allowed = isStockMove
                ? Set.of("id", "code", "status", "moveType", "atTime", "createdAt", "updatedAt", "fromStockStatus", "toStockStatus")
                : Set.of("id", "code", "status", "category", "type", "createdAt", "updatedAt");
        if (!allowed.contains(f)) return defaultOrder;
        String col = switch (f) {
            case "moveType" -> "move_type";
            case "atTime" -> "at_time";
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            case "fromStockStatus" -> "from_stock_status";
            case "toStockStatus" -> "to_stock_status";
            case "category" -> "adj_category";
            case "type" -> "adj_type";
            default -> f;
        };
        return " ORDER BY " + alias + "." + col + " " + dir + " NULLS LAST, " + alias + ".id DESC";
    }

    private String asString(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
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