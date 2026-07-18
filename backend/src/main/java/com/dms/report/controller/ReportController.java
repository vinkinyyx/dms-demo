/*
 * 报表控制器：/api/reports/{type}/query
 */
package com.dms.report.controller;

import com.dms.common.ApiResponse;
import com.dms.report.dto.ReportQueryRequest;
import com.dms.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @PostMapping("/{type}/query")
    public ApiResponse<List<Map<String, Object>>> query(@PathVariable String type,
                                                         @RequestBody(required = false) ReportQueryRequest request) {
        Map<String, Object> filters = request == null ? Map.of() : request.getFilters();
        return ApiResponse.ok(service.query(type, filters));
    }
}
