package com.dms.report.controller;

import com.dms.common.ApiResponse;
import com.dms.report.dto.ReportQueryRequest;
import com.dms.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;
    private static final Set<String> REPORT_TYPES = Set.of(
            "sales-ranking", "product-top10", "inventory-turnover", "order-trace",
            "receivables", "surgery-stats", "sales", "contract", "authorization",
            "loan", "rebate-discount", "inventory-aging", "order-approval",
            "rebate", "discount", "order", "inventory", "invoice", "return");

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(Math.min(size, 100), 1);
        List<String> all = REPORT_TYPES.stream().sorted().toList();
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("total", all.size());
        data.put("records", all.subList(from, to));
        return ApiResponse.ok(data);
    }

    @GetMapping({"/contract","/authorization","/loan","/rebate-discount","/sales"})
    public ApiResponse<List<Map<String, Object>>> legacyGet(HttpServletRequest request) {
        String pathType = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/') + 1);
        String type = "rebate-discount".equals(pathType) ? "rebate" : pathType;
        return ApiResponse.ok(service.query(type, new java.util.HashMap<>()));
    }

    @PostMapping("/{type}/query")
    public ApiResponse<List<Map<String, Object>>> query(@PathVariable String type,
                                                        @RequestBody(required = false) ReportQueryRequest request) {
        Map<String, Object> filters = request == null || request.getFilters() == null
                ? new java.util.HashMap<>()
                : new java.util.HashMap<>(request.getFilters());
        if (request != null) {
            if (request.getRange() != null) filters.putIfAbsent("range", request.getRange());
            if (request.getStartDate() != null) filters.putIfAbsent("startDate", request.getStartDate());
            if (request.getEndDate() != null) filters.putIfAbsent("endDate", request.getEndDate());
            if (request.getLimit() != null) filters.putIfAbsent("limit", request.getLimit());
            if (request.getPage() != null) filters.putIfAbsent("page", request.getPage());
            if (request.getSize() != null) filters.putIfAbsent("size", request.getSize());
            if (request.getDealerId() != null) filters.putIfAbsent("dealerId", request.getDealerId());
            if (request.getHospitalId() != null) filters.putIfAbsent("hospitalId", request.getHospitalId());
            if (request.getProductId() != null) filters.putIfAbsent("productId", request.getProductId());
        }
        return ApiResponse.ok(service.query(type, filters));
    }
}
