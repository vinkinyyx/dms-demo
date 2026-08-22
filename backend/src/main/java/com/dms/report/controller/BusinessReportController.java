package com.dms.report.controller;

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
import com.dms.report.service.BusinessReportService;

@RequestMapping("/api/reports")
@RestController
@RequiredArgsConstructor
@Validated
public class BusinessReportController {

    private final BusinessReportService service;

    @GetMapping("/sales-ranking")
    public ApiResponse<List<Map<String, Object>>> salesRanking( @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) Long dealerId, @RequestParam(required = false) String level, @RequestParam(required = false) String region, @RequestParam(required = false) String status, @RequestParam(required = false) String orderType, @RequestParam(defaultValue = "50") int limit) {
        return service.salesRanking(from, to, dealerId, level, region, status, orderType, limit);
    }

    @GetMapping("/product-top10")
    public ApiResponse<List<Map<String, Object>>> productTop10( @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) Long dealerId, @RequestParam(required = false) Long productId, @RequestParam(required = false) String categoryCode, @RequestParam(defaultValue = "50") int limit) {
        return service.productTop10(from, to, dealerId, productId, categoryCode, limit);
    }

    @GetMapping("/inventory-turnover")
    public ApiResponse<List<Map<String, Object>>> inventoryTurnover( @RequestParam(required = false) Long productId, @RequestParam(required = false) String categoryCode, @RequestParam(defaultValue = "100") int limit) {
        return service.inventoryTurnover(productId, categoryCode, limit);
    }

    @GetMapping("/surgery-stats")
    public ApiResponse<List<Map<String, Object>>> surgeryStats( @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) Long hospitalId, @RequestParam(defaultValue = "100") int limit) {
        return service.surgeryStats(from, to, hospitalId, limit);
    }

    @GetMapping("/receivables")
    public ApiResponse<List<Map<String, Object>>> receivables( @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) Long dealerId, @RequestParam(required = false) String level, @RequestParam(required = false) String region, @RequestParam(defaultValue = "100") int limit) {
        return service.receivables(from, to, dealerId, level, region, limit);
    }

    @GetMapping("/order-trace")
    public ApiResponse<List<Map<String, Object>>> orderTrace( @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) Long dealerId, @RequestParam(required = false) String status, @RequestParam(required = false) String orderType, @RequestParam(defaultValue = "100") int limit) {
        return service.orderTrace(from, to, dealerId, status, orderType, limit);
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return service.overview();
    }

    @GetMapping("/inventory-aging")
    public ApiResponse<List<Map<String, Object>>> inventoryAging( @RequestParam(required = false) String categoryCode, @RequestParam(required = false) Long dealerId, @RequestParam(defaultValue = "180") int agingDays, @RequestParam(defaultValue = "100") int limit) {
        return service.inventoryAging(categoryCode, dealerId, agingDays, limit);
    }

    @GetMapping("/order-approval-stats")
    public ApiResponse<List<Map<String, Object>>> orderApprovalStats( @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(defaultValue = "200") int limit) {
        return service.orderApprovalStats(from, to, limit);
    }

    @GetMapping("/product-sales-detail")
    public ApiResponse<List<Map<String, Object>>> productSalesDetail( @RequestParam Long productId, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(defaultValue = "100") int limit) {
        return service.productSalesDetail(productId, from, to, limit);
    }

    @GetMapping("/dealer-orders")
    public ApiResponse<List<Map<String, Object>>> dealerOrders( @RequestParam Long dealerId, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(required = false) Boolean unpaidOnly, @RequestParam(defaultValue = "100") int limit) {
        return service.dealerOrders(dealerId, from, to, unpaidOnly, limit);
    }

    @GetMapping("/hospital-surgery")
    public ApiResponse<List<Map<String, Object>>> hospitalSurgery( @RequestParam Long hospitalId, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(defaultValue = "100") int limit) {
        return service.hospitalSurgery(hospitalId, from, to, limit);
    }

    @GetMapping("/order-detail-child/{orderId}")
    public ApiResponse<Map<String, Object>> orderDetailChild(@PathVariable Long orderId) {
        return service.orderDetailChild(orderId);
    }

}
