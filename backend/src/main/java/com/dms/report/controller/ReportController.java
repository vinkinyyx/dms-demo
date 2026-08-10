package com.dms.report.controller;

import com.dms.common.ApiResponse;
import com.dms.report.dto.ReportQueryRequest;
import com.dms.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpServletRequest;
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
            "contract", "order", "inventory", "sales", "authorization",
            "loan", "invoice", "rebate", "discount", "return");

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

    @PostMapping("/{type}/query")
    public ApiResponse<List<Map<String, Object>>> query(@PathVariable String type,
                                                         @RequestBody(required = false) ReportQueryRequest request) {
        Map<String, Object> filters = request == null || request.getFilters() == null
                ? Map.of()
                : request.getFilters();
        return ApiResponse.ok(service.query(type, filters));
    }

    @GetMapping({"/contract", "/authorization", "/loan", "/rebate-discount", "/rebate", "/discount", "/order", "/inventory", "/sales", "/invoice", "/return"})
    public ApiResponse<List<Map<String, Object>>> compatibilityGet(HttpServletRequest request,
                                                                    @RequestParam(required = false) String authType,
                                                                    @RequestParam(required = false) String periodYyyymm) {
        String type = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/') + 1);
        Map<String, Object> filters = new LinkedHashMap<>();
        if (authType != null && !authType.isBlank()) {
            filters.put("authType", authType);
        }
        if (periodYyyymm != null && !periodYyyymm.isBlank()) {
            filters.put("periodYyyymm", periodYyyymm);
        }
        String queryType = "rebate-discount".equals(type) ? "rebate" : type;
        return ApiResponse.ok(service.query(queryType, filters));
    }
}