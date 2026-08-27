/*
 * 权限查询服务：负责递归解析用户拥有的资源权限集合。
 */
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
 * 权限查询服务：user -> roles -> strategies -> resources 递归解析。
 */
@Service
@RequiredArgsConstructor
public class PermissionQueryService {

    private final UserRoleRepository userRoleRepository;
    private final RoleStrategyRepository roleStrategyRepository;
    private final StrategyResourceRepository strategyResourceRepository;
    private final ResourceRepository resourceRepository;

    /**
     * 加载用户拥有的资源 code 集合，供权限校验、菜单筛选使用。
     */
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
            if (module.equals("tenant") || module.equals("contract") || module.equals("inventory") || module.equals("order") || module.equals("auth")) {
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