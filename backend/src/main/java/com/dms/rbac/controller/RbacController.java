/*
 * RBAC 控制器：暴露角色管理与用户角色分配接口。
 */
package com.dms.rbac.controller;

import com.dms.common.ApiResponse;
import com.dms.rbac.dto.AssignRolesRequest;
import com.dms.rbac.dto.RoleCreateRequest;
import com.dms.rbac.dto.RoleDTO;
import com.dms.rbac.service.RbacService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 角色与用户-角色接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RbacController {

    private final RbacService rbacService;

    @GetMapping("/roles")
    public ApiResponse<List<RoleDTO>> list(@RequestParam(required = false) UUID tenantId) {
        return ApiResponse.ok(rbacService.listRoles(tenantId));
    }

    @PostMapping("/roles")
    public ApiResponse<RoleDTO> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok(rbacService.createRole(request));
    }

    @PostMapping("/users/{id}/roles")
    public ApiResponse<Void> assign(@PathVariable Long id,
                                    @Valid @RequestBody AssignRolesRequest request) {
        rbacService.assignRoleToUser(id, request.getRoleIds());
        return ApiResponse.ok();
    }
}
