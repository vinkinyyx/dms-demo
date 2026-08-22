package com.dms.execution.controller;

import com.dms.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import com.dms.common.ApiResponse;
import com.dms.execution.service.OrderApprovalExecutionService;

@RestController
@RequiredArgsConstructor
@Validated
public class OrderApprovalExecutionController {

    private final OrderApprovalExecutionService service;

    @PostMapping("/api/orders-approval/{id}/approve")
    public ApiResponse<Map<String, Object>> approveOrder(@PathVariable Long id) {
        return service.approveOrder(id);
    }

    @PostMapping("/api/purchase-orders-approval/{id}/approve")
    public ApiResponse<Map<String, Object>> approvePO(@PathVariable Long id) {
        return service.approvePO(id);
    }

    @PostMapping("/api/sales-outs/{id}/execute")
    public ApiResponse<Map<String, Object>> executeSalesOut(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.executeSalesOut(id, body);
    }

    @PostMapping("/api/sales-outs/{id}/cancel-draft")
    public ApiResponse<Map<String, Object>> cancelSalesOut(@PathVariable Long id) {
        return service.cancelSalesOut(id);
    }

    @PostMapping("/api/receipts/{id}/execute")
    public ApiResponse<Map<String, Object>> executeReceipt(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.executeReceipt(id, body);
    }

    @PostMapping("/api/receipts/{id}/cancel-draft")
    public ApiResponse<Map<String, Object>> cancelReceipt(@PathVariable Long id) {
        return service.cancelReceipt(id);
    }

}
