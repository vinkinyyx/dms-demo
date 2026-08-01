/*
 * 移库控制器：/api/stock-moves
 * v3.4.13: 接受前端扁平结构（含批次/序列号/数量）+ 提供详情接口 + 操作日志
 * 跨仓语义：源仓库 → 目标仓库。
 */
package com.dms.inventory.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
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
@RequestMapping("/api/stock-moves")
@RequiredArgsConstructor
public class StockMoveController {

    private final EntityManager em;
    private final InventoryStatusOps inventoryOps;
    private final AuditLogService opLog;

    @PostMapping
    @Transactional
    @OperationLog(businessType = "stockMove", action = OperationAction.CREATE, remark = "库存移动-创建")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");

        String moveType = strOr(body.get("moveType"), "WAREHOUSE_TRANSFER");
        if (!"STATUS_ADJUST".equals(moveType) && !"WAREHOUSE_TRANSFER".equals(moveType)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "moveType 非法，应为 STATUS_ADJUST 或 WAREHOUSE_TRANSFER");
        }

        Long fromWh = toLong(body.get("fromWarehouseId"));
        Long toWh = toLong(body.get("toWarehouseId"));
        String remark = strOr(body.get("remark"), "");
        String headerFromStatus = strOr(body.get("fromStockStatus"), null);
        String headerToStatus = strOr(body.get("toStockStatus"), null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null || lines.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "移动明细不能为空");
        if (fromWh == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "源仓库必填");

        if ("STATUS_ADJUST".equals(moveType)) {
            toWh = fromWh;
        } else {
            if (toWh == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "目标仓库必填");
            if (fromWh.equals(toWh)) throw new BusinessException(ErrorCode.PARAM_INVALID, "跨仓移动的源仓库与目标仓库不能相同");
        }

        List<Map<String, Object>> norm = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Map<String, Object> l = lines.get(i);
            Long invId = toLong(l.get("srcInventoryId"));
            BigDecimal qty = toBd(l.get("qty"));
            if (invId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "第 " + (i + 1) + " 行必须从库存中选择");
            if (qty == null || qty.signum() <= 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "第 " + (i + 1) + " 行数量必须 > 0");

            var invQ = em.createNativeQuery(
                    "SELECT id, product_id, warehouse_id, batch_no, serial_no, stock_status, qty " +
                    "FROM inventory WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
            invQ.setParameter(1, invId).setParameter(2, tid);
            @SuppressWarnings("unchecked")
            List<Tuple> rs = invQ.getResultList();
            if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "第 " + (i + 1) + " 行选择的库存不存在");
            Tuple inv = rs.get(0);

            Long whId = ((Number) inv.get("warehouse_id")).longValue();
            if (!whId.equals(fromWh)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "第 " + (i + 1) + " 行库存不属于源仓库");
            Long productId = ((Number) inv.get("product_id")).longValue();
            String batchNo = inv.get("batch_no") == null ? null : String.valueOf(inv.get("batch_no"));
            String serialNo = inv.get("serial_no") == null ? null : String.valueOf(inv.get("serial_no"));
            String curStatus = inv.get("stock_status") == null ? "QUALIFIED" : String.valueOf(inv.get("stock_status"));
            BigDecimal onHand = new BigDecimal(String.valueOf(inv.get("qty")));
            if (onHand.compareTo(qty) < 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "第 " + (i + 1) + " 行数量 " + qty + " 超过在库数量 " + onHand);
            if (serialNo != null && qty.compareTo(BigDecimal.ONE) != 0) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "第 " + (i + 1) + " 行是序列号库存，数量必须为 1");

            String fromStatus = strOr(l.get("fromStockStatus"), strOr(headerFromStatus, curStatus));
            if (!fromStatus.equals(curStatus)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "第 " + (i + 1) + " 行库存状态已变化（当前 " + curStatus + "），请刷新后重试");
            String toStatus = strOr(l.get("toStockStatus"), headerToStatus);
            if (toStatus == null || toStatus.isBlank()) toStatus = "WAREHOUSE_TRANSFER".equals(moveType) ? fromStatus : "QUALIFIED";
            if (!isValidStockStatus(toStatus)) throw new BusinessException(ErrorCode.PARAM_INVALID, "第 " + (i + 1) + " 行目标库存状态非法: " + toStatus);
            if (fromStatus.equals(toStatus) && fromWh.equals(toWh)) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "第 " + (i + 1) + " 行源状态与目标状态相同且同仓，无需调整");

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("invId", invId); m.put("productId", productId); m.put("batchNo", batchNo); m.put("serialNo", serialNo);
            m.put("qty", qty); m.put("fromStatus", fromStatus); m.put("toStatus", toStatus); m.put("stockBatchId", toLong(l.get("stockBatchId")));
            norm.add(m);
        }

        String code = nextMoveCode(tid);
        var ins = em.createNativeQuery(
                "INSERT INTO stock_moves (tenant_id, code, src_warehouse_id, dst_warehouse_id, move_type, " +
                "from_stock_status, to_stock_status, status, reason, operator_id, at_time, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 'COMPLETED', ?8, ?9, now(), now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, fromWh).setParameter(4, toWh)
           .setParameter(5, moveType).setParameter(6, norm.isEmpty() ? null : String.valueOf(norm.get(0).get("fromStatus")))
           .setParameter(7, norm.isEmpty() ? null : String.valueOf(norm.get(0).get("toStatus")))
           .setParameter(8, remark).setParameter(9, TenantContext.getUserId());
        Long moveId = ((Number) ins.getSingleResult()).longValue();

        Long userId = TenantContext.getUserId();
        for (Map<String, Object> m : norm) {
            em.createNativeQuery(
                    "INSERT INTO stock_move_lines (move_id, product_id, batch_no, serial_no, qty, src_inventory_id, " +
                    "from_stock_status, to_stock_status, stock_batch_id, created_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, now())")
              .setParameter(1, moveId).setParameter(2, m.get("productId")).setParameter(3, m.get("batchNo"))
              .setParameter(4, m.get("serialNo")).setParameter(5, m.get("qty")).setParameter(6, m.get("invId"))
              .setParameter(7, m.get("fromStatus")).setParameter(8, m.get("toStatus")).setParameter(9, m.get("stockBatchId"))
              .executeUpdate();

            inventoryOps.deductById(tid, (Long) m.get("invId"), (BigDecimal) m.get("qty"),
                    "STATUS_ADJUST".equals(moveType) ? "STATUS_ADJUST_OUT" : "MOVE_OUT", "stock_move", moveId, userId);
            inventoryOps.addByKey(tid, (Long) m.get("productId"), toWh, (String) m.get("batchNo"), (String) m.get("serialNo"),
                    (String) m.get("toStatus"), (BigDecimal) m.get("qty"),
                    "STATUS_ADJUST".equals(moveType) ? "STATUS_ADJUST_IN" : "MOVE_IN", "stock_move", moveId, userId);
            syncSerialAfterMove(tid, (Long) m.get("productId"), fromWh, toWh,
                    (String) m.get("batchNo"), (String) m.get("serialNo"), (String) m.get("toStatus"));
        }

        opLog.log("stock_move", moveId, "CREATE",
                ("STATUS_ADJUST".equals(moveType) ? "库存状态调整 " : "库存移动 ") + code + "，" + norm.size() + " 行");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", moveId); res.put("code", code);
        res.put("moveType", moveType);
        res.put("fromWarehouseId", fromWh); res.put("toWarehouseId", toWh);
        res.put("lineCount", norm.size());
        res.put("message", "STATUS_ADJUST".equals(moveType) ? "库存状态调整完成" : "库存移动完成");
        return ApiResponse.ok(res);
    }

    private void syncSerialAfterMove(UUID tid, Long productId, Long fromWh, Long toWh,
                                     String batchNo, String serialNo, String toStatus) {
        if (serialNo == null || serialNo.isBlank()) return;
        if (fromWh.equals(toWh)) {
            em.createNativeQuery(
                    "UPDATE stock_serials SET stock_status = ?1 " +
                    "WHERE tenant_id = ?2 AND product_id = ?3 AND warehouse_id = ?4 " +
                    "  AND batch_no = ?5 AND serial_no = ?6 AND shipped_at IS NULL")
              .setParameter(1, toStatus)
              .setParameter(2, tid).setParameter(3, productId).setParameter(4, fromWh)
              .setParameter(5, batchNo).setParameter(6, serialNo)
              .executeUpdate();
            return;
        }

        int existing = em.createNativeQuery(
                "SELECT 1 FROM stock_serials " +
                "WHERE tenant_id = ?1 AND batch_no = ?2 AND serial_no = ?3 AND warehouse_id = ?4 AND shipped_at IS NULL")
          .setParameter(1, tid).setParameter(2, batchNo).setParameter(3, serialNo).setParameter(4, toWh)
          .getResultList().size();
        if (existing > 0) {
            em.createNativeQuery(
                    "UPDATE stock_serials SET product_id = ?1, stock_status = ?2, shipped_at = NULL " +
                    "WHERE tenant_id = ?3 AND batch_no = ?4 AND serial_no = ?5 AND warehouse_id = ?6")
              .setParameter(1, productId).setParameter(2, toStatus)
              .setParameter(3, tid).setParameter(4, batchNo).setParameter(5, serialNo).setParameter(6, toWh)
              .executeUpdate();
            em.createNativeQuery(
                    "UPDATE stock_serials SET stock_status = ?1, shipped_at = now() " +
                    "WHERE tenant_id = ?2 AND product_id = ?3 AND batch_no = ?4 AND serial_no = ?5 AND warehouse_id = ?6")
              .setParameter(1, toStatus)
              .setParameter(2, tid).setParameter(3, productId).setParameter(4, batchNo).setParameter(5, serialNo).setParameter(6, fromWh)
              .executeUpdate();
        } else {
            em.createNativeQuery(
                    "UPDATE stock_serials SET warehouse_id = ?1, stock_status = ?2, shipped_at = NULL " +
                    "WHERE tenant_id = ?3 AND product_id = ?4 AND batch_no = ?5 AND serial_no = ?6 AND warehouse_id = ?7")
              .setParameter(1, toWh).setParameter(2, toStatus)
              .setParameter(3, tid).setParameter(4, productId).setParameter(5, batchNo).setParameter(6, serialNo).setParameter(7, fromWh)
              .executeUpdate();
        }
    }
    private boolean isValidStockStatus(String s) {
        return "QUALIFIED".equals(s) || "DEFECTIVE".equals(s) || "QUARANTINED".equals(s) || "PENDING".equals(s);
    }

    private String nextMoveCode(UUID tid) {
        String date = java.time.LocalDate.now().toString().replace("-", "");
        Object seqObj = em.createNativeQuery(
                "INSERT INTO doc_no_sequences (tenant_id, prefix, date_key, last_seq) " +
                "VALUES (?1, 'MV', ?2, 1) " +
                "ON CONFLICT (tenant_id, prefix, date_key) " +
                "DO UPDATE SET last_seq = doc_no_sequences.last_seq + 1 RETURNING last_seq")
                .setParameter(1, tid).setParameter(2, date).getSingleResult();
        long seq = ((Number) seqObj).longValue();
        return String.format("MV-%s-%05d", date, seq);
    }

    @GetMapping({"/{id}/detail", "/{id}"})
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT m.id, m.code, m.src_warehouse_id, sw.name AS src_name, m.dst_warehouse_id, dw.name AS dst_name, " +
                "m.move_type, m.from_stock_status, m.to_stock_status, m.status, m.reason, m.created_at, m.updated_at " +
                "FROM stock_moves m " +
                "LEFT JOIN warehouses sw ON sw.id = m.src_warehouse_id " +
                "LEFT JOIN warehouses dw ON dw.id = m.dst_warehouse_id " +
                "WHERE m.id = ?1 AND m.tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "库存移动单不存在");
        Tuple t = (Tuple) rs.get(0);
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("id", t.get("id"));
        head.put("code", t.get("code"));
        head.put("fromWarehouseId", t.get("src_warehouse_id"));
        head.put("fromWarehouseName", t.get("src_name"));
        head.put("toWarehouseId", t.get("dst_warehouse_id"));
        head.put("toWarehouseName", t.get("dst_name"));
        head.put("moveType", t.get("move_type"));
        head.put("fromStockStatus", t.get("from_stock_status"));
        head.put("toStockStatus", t.get("to_stock_status"));
        head.put("status", t.get("status"));
        head.put("remark", t.get("reason"));
        head.put("createdAt", DateFmt.fmt(t.get("created_at")));
        head.put("updatedAt", DateFmt.fmt(t.get("updated_at")));

        var lq = em.createNativeQuery(
                "SELECT l.product_id, p.name_cn AS product_name, p.code AS product_code, p.is_serial_managed, " +
                "l.batch_no, l.serial_no, l.qty, l.from_stock_status, l.to_stock_status, l.src_inventory_id " +
                "FROM stock_move_lines l LEFT JOIN products p ON p.id = l.product_id " +
                "WHERE l.move_id = ?1 ORDER BY l.id", Tuple.class);
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
            m.put("isSerialManaged", l.get("is_serial_managed"));
            m.put("qty", l.get("qty"));
            m.put("fromStockStatus", l.get("from_stock_status"));
            m.put("toStockStatus", l.get("to_stock_status"));
            m.put("srcInventoryId", l.get("src_inventory_id"));
            lines.add(m);
        }
        head.put("lines", lines);
        return ApiResponse.ok(head);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        int aff = em.createNativeQuery("DELETE FROM stock_moves WHERE id = ?1 AND tenant_id = ?2")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "库存移动单不存在");
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ResponseEntity<byte[]> export() throws Exception {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT m.id, m.code, m.src_warehouse_id, sw.name AS src_name, m.dst_warehouse_id, dw.name AS dst_name, " +
                "m.move_type, m.from_stock_status, m.to_stock_status, m.status, m.reason, m.created_at, m.updated_at " +
                "FROM stock_moves m " +
                "LEFT JOIN warehouses sw ON sw.id = m.src_warehouse_id " +
                "LEFT JOIN warehouses dw ON dw.id = m.dst_warehouse_id " +
                "WHERE m.tenant_id = ?1", Tuple.class);
        q.setParameter(1, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("fromWarehouseId", t.get("src_warehouse_id"));
            m.put("fromWarehouseName", t.get("src_name"));
            m.put("toWarehouseId", t.get("dst_warehouse_id"));
            m.put("toWarehouseName", t.get("dst_name"));
            m.put("moveType", t.get("move_type"));
            m.put("fromStockStatus", t.get("from_stock_status"));
            m.put("toStockStatus", t.get("to_stock_status"));
            m.put("status", t.get("status"));
            m.put("remark", t.get("reason"));
            m.put("createdAt", DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }

        String[] headers = {"ID", "移动单号", "源仓库ID", "源仓库名称", "目标仓库ID", "目标仓库名称", "移动类型", "源库存状态", "目标库存状态", "状态", "备注", "创建时间", "更新时间"};
        String[] fieldNames = {"id", "code", "fromWarehouseId", "fromWarehouseName", "toWarehouseId", "toWarehouseName", "moveType", "fromStockStatus", "toStockStatus", "status", "remark", "createdAt", "updatedAt"};

        byte[] excelBytes = ExcelExportUtils.exportMapToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("库存移动列表.xlsx"))
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
                Long fromWarehouseId = toLong(row.get("源仓库ID"));
                Long toWarehouseId = toLong(row.get("目标仓库ID"));
                String reason = strOr(row.get("备注"), null);
                String status = strOr(row.get("状态"), "DRAFT");

                if (fromWarehouseId == null) {
                    throw new IllegalArgumentException("源仓库ID不能为空");
                }
                if (toWarehouseId == null) {
                    throw new IllegalArgumentException("目标仓库ID不能为空");
                }

                String sql = "INSERT INTO stock_moves (src_warehouse_id, dst_warehouse_id, reason, status, tenant_id) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5)";
                em.createNativeQuery(sql)
                        .setParameter(1, fromWarehouseId)
                        .setParameter(2, toWarehouseId)
                        .setParameter(3, reason)
                        .setParameter(4, status)
                        .setParameter(5, TenantContext.getTenantId())
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
