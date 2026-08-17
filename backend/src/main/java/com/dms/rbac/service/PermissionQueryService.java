/*
 * 鏉冮檺鏌ヨ鏈嶅姟锛岃礋璐ｉ€掑綊瑙ｆ瀽鐢ㄦ埛鎷ユ湁鐨勮祫婧愭潈闄愰泦鍚堛€? */
package com.dms.rbac.service;

import com.dms.rbac.entity.Resource;
import com.dms.rbac.entity.RoleStrategy;
import com.dms.rbac.entity.StrategyResource;
import com.dms.rbac.entity.UserRole;
import com.dms.rbac.repository.ResourceRepository;
import com.dms.rbac.repository.RoleStrategyRepository;
import com.dms.rbac.repository.StrategyResourceRepository;
import com.dms.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 鏉冮檺鏌ヨ鏈嶅姟锛歶ser -> roles -> strategies -> resources 閫掑綊瑙ｆ瀽銆? */
@Service
@RequiredArgsConstructor
public class PermissionQueryService {

    private final UserRoleRepository userRoleRepository;
    private final RoleStrategyRepository roleStrategyRepository;
    private final StrategyResourceRepository strategyResourceRepository;
    private final ResourceRepository resourceRepository;

    /**
     * 鍔犺浇鐢ㄦ埛鎷ユ湁鐨勮祫婧?code 闆嗗悎锛屼緵鏉冮檺鏍￠獙銆佽彍鍗曟覆鏌撲娇鐢ㄣ€?     */
    @Transactional(readOnly = true)
    public Set<String> loadPermissionsForUser(Long userId) {
        Set<String> result = new HashSet<>();
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (userRoles.isEmpty()) {
            return result;
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        List<RoleStrategy> roleStrategies = roleStrategyRepository.findByRoleIdIn(roleIds);
        if (roleStrategies.isEmpty()) {
            return result;
        }
        List<Long> strategyIds = roleStrategies.stream().map(RoleStrategy::getStrategyId).toList();
        List<StrategyResource> strategyResources = strategyResourceRepository.findByStrategyIdIn(strategyIds);
        if (strategyResources.isEmpty()) {
            return result;
        }
        List<Long> resourceIds = strategyResources.stream().map(StrategyResource::getResourceId).toList();
        List<Resource> resources = resourceRepository.findByIdIn(resourceIds);
        for (Resource r : resources) {
            if (r.getCode() != null) {
                result.add(r.getCode());
                addLegacyPermissions(result, r);
            }
        }
        addViewPermissionsForActions(result);
        return result;
    }


    private void addViewPermissionsForActions(Set<String> result) {
        for (String permission : List.copyOf(result)) {
            int index = permission.lastIndexOf(':');
            if (index <= 0) {
                continue;
            }
            String action = permission.substring(index + 1);
            if (!Set.of("view", "search", "create", "edit", "delete", "export", "import", "manage", "admin", "submit", "approve", "reject", "cancel", "confirm", "adjust", "publish").contains(action)) {
                continue;
            }
            result.add(permission.substring(0, index) + ":view");
        }
        if (result.contains("contract_template:create") || result.contains("contract_template:edit")) {
            result.add("contract_template:manage");
        }
        if (result.contains("approval:template:edit") || result.contains("approval:template:create")) {
            result.add("approval:manage");
        }
    }

    private void addLegacyPermissions(Set<String> result, Resource resource) {
        String code = resource.getCode();
        String path = resource.getPath();
        if (code == null) {
            return;
        }
        if (code.startsWith("api.") && path != null && path.endsWith("/**")) {
            String module = code.substring("api.".length());
            if (module.equals("tenant") || module.equals("contract") || module.equals("inventory") || module.equals("order")) {
                return;
            }
            result.add(module + ":view");
            result.add(module + ":search");
            result.add(module + ":create");
            result.add(module + ":edit");
            result.add(module + ":delete");
            result.add(module + ":export");
            result.add(module + ":import");
        }
        if ("/api/api-call-logs/**".equals(path) || "/api/admin/api-call-logs/**".equals(path)) {
            result.add("api_log:view");
            result.add("api_log:search");
            result.add("api_log:export");
        }
        if ("/api/users/**".equals(path)) {
            result.add("user:reset_password");
            result.add("user:unlock");
        }
        if ("/api/roles/**".equals(path)) {
            result.add("role:view");
            result.add("role:search");
            result.add("role:create");
            result.add("role:edit");
            result.add("role:delete");
            result.add("role:assign");
        }
        if ("/api/tenant-page-configs/**".equals(path)) {
            result.add("tenant_ui_config:view");
            result.add("tenant_ui_config:edit");
        }
    }
}

