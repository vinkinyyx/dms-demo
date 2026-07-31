/*
 * 库存调整控制器：/api/inventory-adjustments
 * v3.4.13: 接受前端扁平结构（含批次/序列号/数量）+ 提供详情接口 + 操作日志
 *   category=IN  → 增加库存 (盘盈/数据修正)
 *   category=OUT → 扣减库存 (盘亏/报损)
 * 单仓增减语义（盘盈/盘亏）。
 */
package com.dms.inventory.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.DateFmt;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.ContentDispositionUtils;
import org.springframework.web.multipart.MultipartFile;
import com.dms.common.util.TenantContext;
import com.dms.execution.service.AuditLogService;
import com.dms.inventory.service.InventoryStatusOps;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/inventory-adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final EntityManager em;
    private final InventoryStatusOps inventoryOps;
    private final AuditLogService opLog;

    @PostMapping
    @Transactional
    @OperationLog(businessType = "inventoryAdjustment", action = OperationAction.CREATE, remark = "库存调整-创建")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");

        Long warehouseId = toLong(body.get("warehouseId"));
        String category = String.valueOf(body.getOrDefault("category", "IN")).toUpperCase();
        String type = strOr(body.get("type"), "STOCKTAKE");
        String remark = strOr(body.get("remark"), "");
        String stockStatus = strOr(body.get("stockStatus"), "QUALIFIED");

        if (warehouseId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "仓库必填");
        if (!"IN".equals(category) && !"OUT".equals(category)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "category 必须为 IN(盘盈) 或 OUT(盘亏)");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "调整明细不能为空");
        }

        String code = "ADJ-" + java.time.LocalDate.now().toString().replace("-", "") + "-" + (System.currentTimeMillis() % 100000);

        var ins = em.createNativeQuery(
                "INSERT INTO inventory_adjustments (tenant_id, code, warehouse_id, adj_category, adj_type, status, reason, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, 'COMPLETED', ?6, now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, warehouseId)
                .setParameter(4, category).setParameter(5, type).setParameter(6, remark);
        Long adjId = ((Number) ins.getSingleResult()).longValue();

        for (Map<String, Object> l : lines) {
            Long productId = toLong(l.get("productId"));
            BigDecimal qty = toBd(l.get("qty"));
            String batchNo = strOr(l.get("batchNo"), null);
            String serialNo = strOr(l.get("serialNo"), null);
            if (productId == null || qty == null || qty.signum() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "明细 productId/qty 必填且 > 0");
            }
            em.createNativeQuery(
                    "INSERT INTO adjustment_lines (adjustment_id, product_id, batch_no, serial_no, qty) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, adjId).setParameter(2, productId).setParameter(3, batchNo)
                .setParameter(4, serialNo).setParameter(5, qty)
                .executeUpdate();

            BigDecimal delta = "IN".equals(category) ? qty : qty.negate();
            inventoryOps.change(tid, productId, warehouseId, batchNo, delta, stockStatus, "ADJ_" + category, "adjustment", adjId);
        }

        opLog.log("inventory_adjustment", adjId, "CREATE",
                "库存调整 " + code + "，" + ("IN".equals(category) ? "盘盈" : "盘亏") + " " + lines.size() + " 项");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", adjId);
        res.put("code", code);
        res.put("category", category);
        res.put("warehouseId", warehouseId);
        res.put("lineCount", lines.size());
        res.put("message", "库存调整完成");
        return ApiResponse.ok(res);
    }

    @GetMapping({"/{id}/detail", "/{id}"})
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT a.id, a.code, a.warehouse_id, w.name AS warehouse_name, a.adj_category, a.adj_type, " +
                "a.status, a.reason, a.created_at, a.updated_at " +
                "FROM inventory_adjustments a LEFT JOIN warehouses w ON w.id = a.warehouse_id " +
                "WHERE a.id = ?1 AND a.tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "库存调整单不存在");
        Tuple t = (Tuple) rs.get(0);
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("id", t.get("id"));
        head.put("code", t.get("code"));
        head.put("warehouseId", t.get("warehouse_id"));
        head.put("warehouseName", t.get("warehouse_name"));
        head.put("category", t.get("adj_category"));
        head.put("type", t.get("adj_type"));
        head.put("status", t.get("status"));
        head.put("remark", t.get("reason"));
        head.put("createdAt", DateFmt.fmt(t.get("created_at")));
        head.put("updatedAt", DateFmt.fmt(t.get("updated_at")));

        var lq = em.createNativeQuery(
                "SELECT l.product_id, p.name_cn AS product_name, p.code AS product_code, " +
                "l.batch_no, l.serial_no, l.qty " +
                "FROM adjustment_lines l LEFT JOIN products p ON p.id = l.product_id " +
                "WHERE l.adjustment_id = ?1 ORDER BY l.id", Tuple.class);
        lq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : ls) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", l.get("product_id"));
            m.put("productName", l.get("product_name"));
            m.put("productCode", l.get("product_code"));
            m.put("batchNo", l.get("batch_no"));
            m.put("serialNo", l.get("serial_no"));
            m.put("qty", l.get("qty"));
            lines.add(m);
        }
        head.put("lines", lines);
        return ApiResponse.ok(head);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @OperationLog(businessType = "inventoryAdjustment", action = OperationAction.DELETE, remark = "库存调整-删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        int aff = em.createNativeQuery("DELETE FROM inventory_adjustments WHERE id = ?1 AND tenant_id = ?2")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "库存调整单不存在");
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ResponseEntity<byte[]> export() throws Exception {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT a.id, a.code, a.warehouse_id, w.name AS warehouse_name, a.adj_category, a.adj_type, " +
                "a.status, a.reason, a.created_at, a.updated_at " +
                "FROM inventory_adjustments a LEFT JOIN warehouses w ON w.id = a.warehouse_id " +
                "WHERE a.tenant_id = ?1", Tuple.class);
        q.setParameter(1, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("warehouseId", t.get("warehouse_id"));
            m.put("warehouseName", t.get("warehouse_name"));
            m.put("category", t.get("adj_category"));
            m.put("type", t.get("adj_type"));
            m.put("status", t.get("status"));
            m.put("reason", t.get("reason"));
            m.put("createdAt", DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }

        String[] headers = {"ID", "调整单号", "仓库ID", "仓库名称", "调整方向", "调整类型", "状态", "原因", "创建时间", "更新时间"};
        String[] fieldNames = {"id", "code", "warehouseId", "warehouseName", "category", "type", "status", "reason", "createdAt", "updatedAt"};

        byte[] excelBytes = ExcelExportUtils.exportMapToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("库存调整列表.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @PostMapping("/batch-import")
    @Transactional
    public ApiResponse<java.util.Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ApiResponse.fail(40001, "请选择要导入的文件");
        }

        java.util.List<java.util.Map<String, Object>> data = ExcelImportUtils.importFromExcel(file.getInputStream(), file.getOriginalFilename());
        if (data.isEmpty()) {
            return ApiResponse.fail(40002, "Excel 文件中没有数据");
        }

        int success = 0, failed = 0;
        java.util.List<java.util.Map<String, Object>> errors = new java.util.ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            java.util.Map<String, Object> row = data.get(i);
            try {
                Long warehouseId = toLong(row.get("仓库ID"));
                String category = strOr(row.get("调整方向"), "IN");
                String type = strOr(row.get("调整类型"), "STOCKTAKE");
                String reason = strOr(row.get("原因"), null);
                String status = strOr(row.get("状态"), "DRAFT");

                if (warehouseId == null) {
                    throw new IllegalArgumentException("仓库ID不能为空");
                }

                String sql = "INSERT INTO inventory_adjustments (warehouse_id, adj_category, adj_type, reason, status, tenant_id) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
                em.createNativeQuery(sql)
                        .setParameter(1, warehouseId)
                        .setParameter(2, category)
                        .setParameter(3, type)
                        .setParameter(4, reason)
                        .setParameter(5, status)
                        .setParameter(6, TenantContext.getTenantId())
                        .executeUpdate();
                success++;
            } catch (Exception e) {
                failed++;
                java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
                err.put("row", i + 2);
                err.put("error", e.getMessage());
                errors.add(err);
            }
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", data.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        return ApiResponse.ok(result);
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
    private BigDecimal toBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
    private String strOr(Object o, String d) {
        if (o == null) return d;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? d : s;
    }
}
