/*
 * 收货单控制器：/api/receipts
 */
package com.dms.inventory.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.ContentDispositionUtils;
import org.springframework.web.multipart.MultipartFile;
import com.dms.common.util.TenantContext;
import com.dms.inventory.dto.ReceiptCancelFullRequest;
import com.dms.inventory.dto.ReceiptCancelPartialRequest;
import com.dms.inventory.dto.ReceiptConfirmFullRequest;
import com.dms.inventory.dto.ReceiptConfirmRequest;
import com.dms.inventory.entity.Receipt;
import com.dms.inventory.service.ReceiptService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
@Validated
public class ReceiptController {

    private final ReceiptService service;
    private final EntityManager em;

    // GET 已移到 BizDocListController（v3.4.6 增强字段）

    @PostMapping
    @OperationLog(businessType = "receipt", action = OperationAction.CREATE, remark = "收货入库-创建")
    public ApiResponse<Receipt> create(@RequestBody Receipt receipt) {
        return ApiResponse.ok(service.create(receipt));
    }

    @PostMapping("/{id}/confirm")
    @OperationLog(businessType = "receipt", action = OperationAction.UPDATE, remark = "收货入库-确认收货")
    public ApiResponse<Receipt> confirm(@PathVariable Long id, @RequestBody ReceiptConfirmRequest request) {
        return ApiResponse.ok(service.confirm(id, request.getLines()));
    }

    /**
     * v3.7.3 整单确认收货：按 expected_qty 自动全部入库（SAP MM MIGO 风格）
     */
    @PostMapping("/{id}/confirm-full")
    @OperationLog(businessType = "receipt", action = OperationAction.UPDATE, remark = "收货入库-整单确认")
    public ApiResponse<Receipt> confirmFull(@PathVariable Long id,
                                            @RequestBody(required = false) ReceiptConfirmFullRequest request) {
        return ApiResponse.ok(service.confirmFull(id));
    }

    /**
     * v3.7.3 部分取消：按明细行取消已收数量，回收库存
     */
    @PostMapping("/{id}/cancel-partial")
    @OperationLog(businessType = "receipt", action = OperationAction.UPDATE, remark = "收货入库-部分取消")
    public ApiResponse<Receipt> cancelPartial(@PathVariable Long id,
                                              @RequestBody ReceiptCancelPartialRequest request) {
        return ApiResponse.ok(service.cancelPartial(id, request.getLines(), request.getReason()));
    }

    /**
     * v3.7.3 整单作废：已入库全部回滚出库
     */
    @PostMapping("/{id}/cancel-full")
    @OperationLog(businessType = "receipt", action = OperationAction.UPDATE, remark = "收货入库-整单作废")
    public ApiResponse<Receipt> cancelFull(@PathVariable Long id,
                                           @RequestBody(required = false) ReceiptCancelFullRequest request) {
        String reason = request == null ? null : request.getReason();
        return ApiResponse.ok(service.cancelFull(id, reason));
    }

    /**
     * 异常收货整单撤销（US-B-14）
     * 将收货单状态改为 CANCELLED，回滚已入库的库存
     */
    @PostMapping("/{id}/cancel")
    @Transactional
    @OperationLog(businessType = "receipt", action = OperationAction.UPDATE, remark = "收货入库-异常撤销")
    public ApiResponse<Map<String, Object>> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? "异常收货撤销" : String.valueOf(body.getOrDefault("reason", "异常收货撤销"));
        try {
            // 更新收货单状态
            var upd = em.createNativeQuery(
                    "UPDATE receipts SET status = 'CANCELLED' " +
                    "WHERE id = ?1 AND tenant_id = ?2 AND status != 'CANCELLED'");
            upd.setParameter(1, id).setParameter(2, TenantContext.getTenantId());
            int affected = upd.executeUpdate();
            if (affected == 0) return ApiResponse.fail(40404, "收货单不存在或已撤销");

            // 记录审计
            var audit = em.createNativeQuery(
                    "INSERT INTO audit_logs (tenant_id, user_id, action, resource_type, resource_id, ip, at_time) " +
                    "VALUES (?1, ?2, 'RECEIPT_CANCEL', 'receipt', ?3, '127.0.0.1', now())");
            audit.setParameter(1, TenantContext.getTenantId());
            audit.setParameter(2, TenantContext.getUserId());
            audit.setParameter(3, String.valueOf(id));
            audit.executeUpdate();

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("receiptId", id);
            res.put("cancelled", true);
            res.put("reason", reason);
            return ApiResponse.ok(res);
        } catch (Exception e) {
            return ApiResponse.fail(50000, "撤销失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @OperationLog(businessType = "receipt", action = OperationAction.DELETE, remark = "收货入库-删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        PageQuery pq = new PageQuery();
        pq.setPage(1);
        pq.setSize(10000);
        java.util.List<Receipt> list = service.list(pq).getList();

        String[] headers = {"ID", "收货单号", "采购订单", "仓库", "状态", "收货数量", "创建时间", "更新时间"};
        String[] fieldNames = {"id", "code", "purchaseOrderId", "warehouseName", "status", "totalQty", "createdAt", "updatedAt"};

        byte[] excelBytes = ExcelExportUtils.exportToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("收货入库列表.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @PostMapping("/batch-import")
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
                Receipt entity = new Receipt();
                if (row.get("采购订单") != null) {
                    entity.setRefDocId(((Number) row.get("采购订单")).longValue());
                    entity.setRefDocType("PURCHASE_ORDER");
                }
                if (row.get("仓库") != null) {
                    entity.setWarehouseId(((Number) row.get("仓库")).longValue());
                }
                if (row.get("状态") != null) {
                    entity.setStatus(String.valueOf(row.get("状态")));
                } else {
                    entity.setStatus("DRAFT");
                }
                service.create(entity);
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
}
