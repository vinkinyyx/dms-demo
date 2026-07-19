/*
 * 库存查询控制器：/api/inventory
 * v3.4.4 修复：join 产品/仓库/经销商，返回丰富字段
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

import java.util.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final EntityManager em;

    @GetMapping
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> query(
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String serialNo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        UUID tid = TenantContext.getTenantId();
        int offset = (page - 1) * size;

        StringBuilder where = new StringBuilder("WHERE inv.tenant_id = ?1");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (dealerId != null) { where.append(" AND inv.dealer_id = ?").append(idx++); params.add(dealerId); }
        if (productId != null) { where.append(" AND inv.product_id = ?").append(idx++); params.add(productId); }
        if (warehouseId != null) { where.append(" AND inv.warehouse_id = ?").append(idx++); params.add(warehouseId); }
        if (batchNo != null && !batchNo.isBlank()) { where.append(" AND inv.batch_no ILIKE ?").append(idx++); params.add("%" + batchNo.trim() + "%"); }
        if (serialNo != null && !serialNo.isBlank()) { where.append(" AND inv.serial_no ILIKE ?").append(idx++); params.add("%" + serialNo.trim() + "%"); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND inv.product_id IN (SELECT id FROM products WHERE tenant_id = inv.tenant_id AND (code ILIKE ?")
                 .append(idx).append(" OR name_cn ILIKE ?").append(idx).append("))");
            idx++;
            params.add("%" + keyword.trim() + "%");
        }
        if (stockStatus != null && !stockStatus.isBlank()) { where.append(" AND inv.stock_status = ?").append(idx++); params.add(stockStatus); }

        // 总数
        String cntSql = "SELECT COUNT(*) FROM inventory inv " + where;
        var cntQ = em.createNativeQuery(cntSql);
        for (int i = 0; i < params.size(); i++) cntQ.setParameter(i + 1, params.get(i));
        long total = ((Number) cntQ.getSingleResult()).longValue();

        // 列表 join 产品/仓库/经销商
        String listSql = "SELECT inv.id, inv.tenant_id, inv.warehouse_id, inv.product_id, inv.dealer_id, " +
                "inv.batch_no, inv.serial_no, inv.qty, inv.stock_status, inv.in_source, inv.exp_date, " +
                "inv.prod_date, inv.created_at, inv.updated_at, " +
                "p.name_cn AS product_name, p.code AS product_code, p.spec AS product_spec, " +
                "p.unit AS product_unit, p.is_serial_managed, " +
                "w.name AS warehouse_name, w.code AS warehouse_code, " +
                "d.name AS dealer_name " +
                "FROM inventory inv " +
                "LEFT JOIN products p ON p.id = inv.product_id " +
                "LEFT JOIN warehouses w ON w.id = inv.warehouse_id " +
                "LEFT JOIN dealers d ON d.id = inv.dealer_id " +
                where +
                " ORDER BY inv.updated_at DESC NULLS LAST, inv.id DESC LIMIT ?" + idx + " OFFSET ?" + (idx + 1);
        var listQ = em.createNativeQuery(listSql, Tuple.class);
        for (int i = 0; i < params.size(); i++) listQ.setParameter(i + 1, params.get(i));
        listQ.setParameter(idx, size);
        listQ.setParameter(idx + 1, offset);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = listQ.getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("tenantId", t.get("tenant_id"));
            m.put("warehouseId", t.get("warehouse_id"));
            m.put("warehouseName", t.get("warehouse_name"));
            m.put("warehouseCode", t.get("warehouse_code"));
            m.put("productId", t.get("product_id"));
            m.put("productName", t.get("product_name"));
            m.put("productCode", t.get("product_code"));
            m.put("productSpec", t.get("product_spec"));
            m.put("productUnit", t.get("product_unit"));
            m.put("isSerialManaged", t.get("is_serial_managed"));
            m.put("dealerId", t.get("dealer_id"));
            m.put("dealerName", t.get("dealer_name"));
            m.put("batchNo", t.get("batch_no"));
            m.put("serialNo", t.get("serial_no"));
            m.put("qty", t.get("qty"));
            m.put("stockStatus", t.get("stock_status"));
            m.put("inSource", t.get("in_source"));
            m.put("expDate", com.dms.common.util.DateFmt.fmt(t.get("exp_date")));
            m.put("prodDate", com.dms.common.util.DateFmt.fmt(t.get("prod_date")));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("list", list);
        return ApiResponse.ok(data);
    }

    /**
     * v3.4 新增：查询产品在仓库中可选批次/序列号（用于出库下拉）
     * GET /api/inventory/available-lots?productId=X&warehouseId=Y&status=QUALIFIED
     */
    @GetMapping("/available-lots")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> availableLots(
            @RequestParam Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "QUALIFIED") String status) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "SELECT id, warehouse_id, batch_no, serial_no, qty, exp_date, stock_status " +
                "FROM inventory WHERE tenant_id = ?1 AND product_id = ?2 AND stock_status = ?3 AND qty > 0");
        if (warehouseId != null) sql.append(" AND warehouse_id = ?4");
        sql.append(" ORDER BY exp_date NULLS LAST, batch_no NULLS LAST LIMIT 500");

        var q = em.createNativeQuery(sql.toString(), Tuple.class);
        q.setParameter(1, tid).setParameter(2, productId).setParameter(3, status);
        if (warehouseId != null) q.setParameter(4, warehouseId);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();

        List<Map<String, Object>> out = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("warehouseId", t.get("warehouse_id"));
            m.put("batchNo", t.get("batch_no"));
            m.put("serialNo", t.get("serial_no"));
            m.put("qty", t.get("qty"));
            m.put("expDate", com.dms.common.util.DateFmt.fmt(t.get("exp_date")));
            m.put("stockStatus", t.get("stock_status"));
            String label;
            if (t.get("serial_no") != null && !String.valueOf(t.get("serial_no")).isEmpty()) {
                label = "SN: " + t.get("serial_no");
            } else {
                label = "批次: " + t.get("batch_no") + " (可用 " + t.get("qty") + ")";
            }
            m.put("label", label);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }
}
