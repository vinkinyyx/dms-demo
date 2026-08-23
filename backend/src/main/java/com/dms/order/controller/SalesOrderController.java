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
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import com.dms.order.service.SalesOrderService;

@RequestMapping("/api/sales-orders")
@RestController
@RequiredArgsConstructor
@Validated
public class SalesOrderController {

    private final SalesOrderService service;

    @GetMapping
    public ApiResponse<Map<String, Object>> list( @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String status, @RequestParam(required = false) Long dealerId, @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String createdFrom, @RequestParam(required = false) String createdTo, @RequestParam(required = false) String createdAt, @RequestParam(required = false) String createdAtFrom, @RequestParam(required = false) String createdAtTo, @RequestParam(required = false) String updatedAtFrom, @RequestParam(required = false) String updatedAtTo, @RequestParam(required = false) String finalAmountFrom, @RequestParam(required = false) String finalAmountTo, @RequestParam(required = false) String code, @RequestParam(required = false) String keyword, @RequestParam(required = false) String sort) {
        return service.list(page, size, status, dealerId, warehouseId, createdFrom, createdTo, createdAt, createdAtFrom, createdAtTo, updatedAtFrom, updatedAtTo, finalAmountFrom, finalAmountTo, code, keyword, sort);
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        return service.preview(body);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @OperationLog(businessType = "salesOrder", action = OperationAction.CREATE, remark = "销售订单-创建")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "salesOrder", action = OperationAction.UPDATE, remark = "update sales order")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(businessType = "salesOrder", action = OperationAction.UPDATE, remark = "submit sales order")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    @OperationLog(businessType = "salesOrder", action = OperationAction.APPROVE, remark = "销售订单-审批通过")
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.approve(id, body);
    }

    @PostMapping("/{id}/reject")
    @OperationLog(businessType = "salesOrder", action = OperationAction.REJECT, remark = "销售订单-驳回")
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.reject(id, body);
    }

    @PostMapping("/{id}/cancel")
    @OperationLog(businessType = "salesOrder", action = OperationAction.UPDATE, remark = "cancel sales order")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/simulate-ship")
    public ApiResponse<Map<String, Object>> simulateShip(@PathVariable Long id) {
        return service.simulateShip(id);
    }

    @DeleteMapping("/{id}")
    @OperationLog(businessType = "salesOrder", action = OperationAction.DELETE, remark = "销售订单-删除")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        return service.delete(id);
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        return service.export();
    }

    @PostMapping("/batch-import")
    public ApiResponse<Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        return service.batchImport(file);
    }

}
