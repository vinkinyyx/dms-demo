/*
 * 权限查询服务，负责递归解析用户拥有的资源权限集合。
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
     * 加载用户拥有的资源 code 集合，供权限校验、菜单渲染使用。
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
            }
        }
        return result;
    }
}
