/*
 * 租户 REST 控制器，暴露超管使用的租户 CRUD 接口。
 */
package com.dms.tenant.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.tenant.dto.TenantCreateRequest;
import com.dms.tenant.dto.TenantDTO;
import com.dms.tenant.dto.TenantUpdateRequest;
import com.dms.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 租户接口：GET/POST/PUT /api/tenants，供超管调用。
 */
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    public ApiResponse<PageResult<TenantDTO>> list(@Valid PageQuery pageQuery,
                                                   @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(tenantService.list(pageQuery, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<TenantDTO> get(@PathVariable UUID id) {
        return ApiResponse.ok(tenantService.get(id));
    }

    @PostMapping
    public ApiResponse<TenantDTO> create(@Valid @RequestBody TenantCreateRequest request) {
        return ApiResponse.ok(tenantService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TenantDTO> update(@PathVariable UUID id,
                                         @Valid @RequestBody TenantUpdateRequest request) {
        return ApiResponse.ok(tenantService.updateBasic(id, request));
    }
}
