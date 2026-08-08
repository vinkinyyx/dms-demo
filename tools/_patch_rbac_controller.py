from pathlib import Path
p=Path('backend/src/main/java/com/dms/rbac/controller/RbacController.java')
s=p.read_text(encoding='utf-8')
s=s.replace('import com.dms.rbac.dto.RoleCreateRequest;\nimport com.dms.rbac.dto.RoleDTO;', 'import com.dms.rbac.dto.RoleCreateRequest;\nimport com.dms.rbac.dto.RoleDTO;\nimport com.dms.rbac.dto.RolePermissionsDTO;\nimport com.dms.rbac.dto.RolePermissionsRequest;')
s=s.replace('''    @PostMapping("/users/{id}/roles")
    public ApiResponse<Void> assign(@PathVariable Long id,
                                    @Valid @RequestBody AssignRolesRequest request) {
        rbacService.assignRoleToUser(id, request.getRoleIds());
        return ApiResponse.ok();
    }
}''', '''    @GetMapping("/roles/{id}/permissions")
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
}''')
p.write_text(s, encoding='utf-8', newline='\n')
