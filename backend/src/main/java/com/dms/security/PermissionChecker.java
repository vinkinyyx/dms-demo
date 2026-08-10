package com.dms.security;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("perm")
public class PermissionChecker {

    public boolean hasAny(String... permissions) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (String permission : permissions) {
            if (hasAuthority(authentication, permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean canManageUsers() {
        return hasAny("user:search", "user:view", "user:create", "user:edit");
    }

    public boolean canManageRoles() {
        return hasAny("role:search", "role:view", "role:create", "role:edit", "role:assign");
    }

    public boolean canViewProductMappings() {
        return hasAny("product_mapping:search", "product_mapping:view", "product_mapping:import", "product_mapping:export");
    }

    public boolean canAdminApprovals() {
        return hasAny("approval:admin", "approval:manage", "approval:template:edit", "tenant_ui_config:view");
    }

    public boolean canManageTenantUi() {
        return hasAny("tenant_ui_config:view", "tenant_ui_config:edit");
    }

    public void requireAny(String... permissions) {
        if (!hasAny(permissions)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有权限访问该资源");
        }
    }

    private boolean hasAuthority(Authentication authentication, String permission) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (permission.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
