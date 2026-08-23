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
import com.dms.order.service.SalesReturnService;

@RequestMapping("/api/sales-returns")
@RestController
@RequiredArgsConstructor
@Validated
public class SalesReturnController {

    private final SalesReturnService service;

    @GetMapping
    public ApiResponse<Map<String, Object>> list( @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String status, @RequestParam(required = false) Long dealerId, @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String createdAtFrom, @RequestParam(required = false) String createdAtTo, @RequestParam(required = false) String updatedAtFrom, @RequestParam(required = false) String updatedAtTo, @RequestParam(required = false) String finalAmountFrom, @RequestParam(required = false) String finalAmountTo, @RequestParam(required = false) String sort) {
        return service.list(page, size, status, dealerId, warehouseId, createdAtFrom, createdAtTo, updatedAtFrom, updatedAtTo, finalAmountFrom, finalAmountTo, sort);
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String status, @RequestParam(required = false) Long dealerId, @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String reasonCode) {
        return service.export(status, dealerId, warehouseId, reasonCode);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/shipped-outs")
    public ApiResponse<List<Map<String, Object>>> shippedOuts( @RequestParam(required = false) Long orderId, @RequestParam(required = false) Long dealerId, @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate, @RequestParam(required = false) String keyword, @RequestParam(required = false) String batchNo, @RequestParam(required = false) String serialNo, @RequestParam(required = false) Long productId) {
        return service.shippedOuts(orderId, dealerId, startDate, endDate, keyword, batchNo, serialNo, productId);
    }

    @GetMapping("/shipped-outs/{salesOutId}/lines")
    public ApiResponse<Map<String, Object>> shippedOutLines(@PathVariable Long salesOutId) {
        return service.shippedOutLines(salesOutId);
    }

    @PostMapping
    @OperationLog(businessType = "salesReturn", action = OperationAction.CREATE, remark = "销退订单-创建")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "salesReturn", action = OperationAction.UPDATE, remark = "销退订单-更新")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/create-red-out")
    @OperationLog(businessType = "salesReturn", action = OperationAction.CREATE, remark = "销退订单-生成红字销售出库")
    public ApiResponse<Map<String, Object>> createRedOut(@PathVariable Long id) {
        return service.createRedOut(id);
    }

    @PostMapping("/{id}/submit")
    @OperationLog(businessType = "salesReturn", action = OperationAction.UPDATE, remark = "销退订单-提交审批")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    @OperationLog(businessType = "salesReturn", action = OperationAction.APPROVE, remark = "销退订单-审批通过")
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id) {
        return service.reject(id);
    }

    @PostMapping("/{id}/cancel")
    @OperationLog(businessType = "salesReturn", action = OperationAction.UPDATE, remark = "销退订单-取消")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        return service.delete(id);
    }

}
