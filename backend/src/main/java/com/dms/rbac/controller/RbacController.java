/*
 * RBAC 鎺у埗鍣細鏆撮湶瑙掕壊绠＄悊涓庣敤鎴疯鑹插垎閰嶆帴鍙ｃ€? */
package com.dms.rbac.controller;

import com.dms.common.ApiResponse;
import com.dms.rbac.dto.AssignRolesRequest;
import com.dms.rbac.dto.RoleCreateRequest;
import com.dms.rbac.dto.RoleDTO;
import com.dms.rbac.dto.RolePermissionsDTO;
import com.dms.rbac.dto.RolePermissionsRequest;
import com.dms.rbac.service.RbacService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 瑙掕壊涓庣敤鎴?瑙掕壊鎺ュ彛銆? */
@RestController
@RequestMapping("/api")
@PreAuthorize("@perm.canManageRoles()")
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

    @GetMapping("/roles/{id}")
    public ApiResponse<RoleDTO> get(@PathVariable Long id) {
        return ApiResponse.ok(rbacService.getRole(id));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<RoleDTO> update(@PathVariable Long id, @Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok(rbacService.updateRole(id, request));
    }

    @GetMapping("/roles/{id}/permissions")
    public ApiResponse<RolePermissionsDTO> getPermissions(@PathVariable Long id) {
        return ApiResponse.ok(rbacService.getRolePermissions(id));
    }

    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<RolePermissionsDTO> setPermissions(@PathVariable Long id,
                                                          @Valid @RequestBody RolePermissionsRequest request) {
        return ApiResponse.ok(rbacService.setRolePermissions(id, request));
    }

    @PostMapping("/users/{id}/roles")
    public ApiResponse<Void> assign(@PathVariable Long id,
                                    @Valid @RequestBody AssignRolesRequest request) {
        rbacService.assignRoleToUser(id, request.getRoleIds());
        return ApiResponse.ok();
    }
}
