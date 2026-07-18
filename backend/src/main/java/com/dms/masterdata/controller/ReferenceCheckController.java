/*
 * 主数据引用检查 REST 接口（US-A-02）
 * 前端在停用/删除主数据前可先调用，展示引用情况
 */
package com.dms.masterdata.controller;

import com.dms.common.ApiResponse;
import com.dms.masterdata.service.ReferenceCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reference-check")
@RequiredArgsConstructor
public class ReferenceCheckController {

    private final ReferenceCheckService service;

    @GetMapping("/product/{id}")
    public ApiResponse<Map<String, Object>> product(@PathVariable Long id) {
        return wrap(service.productReferences(id));
    }

    @GetMapping("/dealer/{id}")
    public ApiResponse<Map<String, Object>> dealer(@PathVariable Long id) {
        return wrap(service.dealerReferences(id));
    }

    @GetMapping("/hospital/{id}")
    public ApiResponse<Map<String, Object>> hospital(@PathVariable Long id) {
        return wrap(service.hospitalReferences(id));
    }

    @GetMapping("/warehouse/{id}")
    public ApiResponse<Map<String, Object>> warehouse(@PathVariable Long id) {
        return wrap(service.warehouseReferences(id));
    }

    private ApiResponse<Map<String, Object>> wrap(Map<String, Long> refs) {
        long total = service.totalRefs(refs);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("canDelete", total == 0);
        data.put("references", refs);
        data.put("description", service.describe(refs));
        return ApiResponse.ok(data);
    }
}
