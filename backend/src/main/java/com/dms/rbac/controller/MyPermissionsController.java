/*
 * 当前用户权限码查询：/api/me/permissions
 * 供前端 v-has 指令初始化用（一次拉全量 set）。
 */
package com.dms.rbac.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.rbac.service.PermissionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MyPermissionsController {

    private final PermissionQueryService permissionQueryService;

    @GetMapping("/permissions")
    public ApiResponse<Set<String>> myPermissions() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(permissionQueryService.loadPermissionsForUser(userId));
    }
}