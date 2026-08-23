package com.dms.order.controller;

import com.dms.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.order.dto.TransferResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import com.dms.order.service.PurchaseOrderService;

@RequestMapping("/api/purchase-orders")
@RestController
@RequiredArgsConstructor
@Validated
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @GetMapping
    public ApiResponse<Map<String, Object>> list( @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String status, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String createdAtFrom, @RequestParam(required = false) String createdAtTo, @RequestParam(required = false) String updatedAtFrom, @RequestParam(required = false) String updatedAtTo, @RequestParam(required = false) String totalAmountFrom, @RequestParam(required = false) String totalAmountTo, @RequestParam(required = false) String finalAmountFrom, @RequestParam(required = false) String finalAmountTo, @RequestParam(required = false) String sort) {
        return service.list(page, size, status, supplierId, warehouseId, createdAtFrom, createdAtTo, updatedAtFrom, updatedAtTo, totalAmountFrom, totalAmountTo, finalAmountFrom, finalAmountTo, sort);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.CREATE, remark = "采购订单-创建")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return service.create(body);
    }

    @PostMapping("/transfer")
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.CREATE, remark = "采购订单传输")
    public ApiResponse<TransferResponse> transfer(@RequestBody(required = false) Map<String, Object> body) {
        return service.transfer(body);
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.UPDATE, remark = "采购订单-更新")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.UPDATE, remark = "采购订单-提交审批")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.APPROVE, remark = "采购订单-审批通过")
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.approve(id, body);
    }

    @PostMapping("/{id}/reject")
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.REJECT, remark = "采购订单-驳回")
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.reject(id, body);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/receive")
    public ApiResponse<Map<String, Object>> receive(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.receive(id, body);
    }

    @DeleteMapping("/{id}")
    @OperationLog(businessType = "purchaseOrder", action = OperationAction.DELETE, remark = "采购订单-删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return service.delete(id);
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        return service.export();
    }

    @PostMapping("/batch-import")
    public ApiResponse<java.util.Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        return service.batchImport(file);
    }

}
