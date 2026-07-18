/*
 * RMA 授权控制器：/api/rma-authorizations
 */
package com.dms.rma.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.rma.entity.RmaAuthorization;
import com.dms.rma.service.RmaAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rma-authorizations")
@RequiredArgsConstructor
@Validated
public class RmaAuthorizationController {

    private final RmaAuthorizationService service;

    @GetMapping
    public ApiResponse<PageResult<RmaAuthorization>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<RmaAuthorization> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<RmaAuthorization> create(@RequestBody RmaAuthorization req) {
        return ApiResponse.ok(service.create(req));
    }
}
