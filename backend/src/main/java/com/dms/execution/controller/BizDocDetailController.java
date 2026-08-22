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
import com.dms.execution.service.BizDocDetailService;

@RestController
@RequiredArgsConstructor
@Validated
public class BizDocDetailController {

    private final BizDocDetailService service;

    @GetMapping({"/api/sales-outs/{id}/detail", "/api/sales-outs/{id}"})
    public ApiResponse<Map<String, Object>> salesOutDetail(@PathVariable Long id) {
        return service.salesOutDetail(id);
    }

    @GetMapping({"/api/receipts/{id}/detail", "/api/receipts/{id}"})
    public ApiResponse<Map<String, Object>> receiptDetail(@PathVariable Long id) {
        return service.receiptDetail(id);
    }

    @GetMapping("/api/orders/{id}/detail")
    public ApiResponse<Map<String, Object>> orderDetail(@PathVariable Long id) {
        return service.orderDetail(id);
    }

    @GetMapping("/api/purchase-orders/{id}/detail")
    public ApiResponse<Map<String, Object>> poDetail(@PathVariable Long id) {
        return service.poDetail(id);
    }

}
