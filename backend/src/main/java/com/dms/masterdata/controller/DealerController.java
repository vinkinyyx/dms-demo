/*
 * 经销商 REST 控制器。
 */
package com.dms.masterdata.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.service.DealerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dealers")
@RequiredArgsConstructor
@Validated
public class DealerController {

    private final DealerService service;

    @GetMapping
    public ApiResponse<PageResult<Dealer>> list(@Valid PageQuery pageQuery,
                                                @RequestParam(required = false) java.util.Map<String, String> allParams) {
        return ApiResponse.ok(service.list(pageQuery, allParams));
    }

    @GetMapping("/{id}")
    public ApiResponse<Dealer> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<Dealer> create(@RequestBody Dealer request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Dealer> update(@PathVariable Long id, @RequestBody Dealer request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }

    // 批量导入占位：V1 未实现
    @PostMapping("/batch-import")
    public ApiResponse<Void> batchImport() {
        return ApiResponse.ok();
    }
}
