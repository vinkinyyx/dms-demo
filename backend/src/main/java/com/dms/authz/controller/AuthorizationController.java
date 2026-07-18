/*
 * 授权 REST 控制器：/api/authorizations、/api/temp-authorizations。
 */
package com.dms.authz.controller;

import com.dms.authz.dto.AuthorizationCheckRequest;
import com.dms.authz.dto.AuthorizationCheckResult;
import com.dms.authz.entity.Authorization;
import com.dms.authz.entity.TempAuthorization;
import com.dms.authz.service.AuthorizationService;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class AuthorizationController {

    private final AuthorizationService service;

    @GetMapping("/api/authorizations")
    public ApiResponse<PageResult<Authorization>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @PostMapping("/api/authorizations")
    public ApiResponse<Authorization> create(@RequestBody Authorization request) {
        return ApiResponse.ok(service.create(request));
    }

    @PostMapping("/api/authorizations/check")
    public ApiResponse<List<AuthorizationCheckResult>> check(@RequestBody AuthorizationCheckRequest request) {
        return ApiResponse.ok(service.check(request));
    }

    @PostMapping("/api/temp-authorizations")
    public ApiResponse<TempAuthorization> createTemp(@RequestBody TempAuthorization request) {
        return ApiResponse.ok(service.createTemp(request));
    }
}
