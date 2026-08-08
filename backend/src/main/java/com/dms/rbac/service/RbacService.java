/*
 * RBAC 服务：负责角色 CRUD 与用户-角色分配。
 */
package com.dms.rbac.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.rbac.dto.RoleCreateRequest;
import com.dms.rbac.dto.RoleDTO;
import com.dms.rbac.dto.RolePermissionsDTO;
import com.dms.rbac.dto.RolePermissionsRequest;
import com.dms.rbac.entity.Resource;
import com.dms.rbac.entity.Role;
import com.dms.rbac.entity.RoleStrategy;
import com.dms.rbac.entity.Strategy;
import com.dms.rbac.entity.StrategyResource;
import com.dms.rbac.entity.UserRole;
import com.dms.rbac.repository.ResourceRepository;
import com.dms.rbac.repository.RoleRepository;
import com.dms.rbac.repository.RoleStrategyRepository;
import com.dms.rbac.repository.StrategyRepository;
import com.dms.rbac.repository.StrategyResourceRepository;
import com.dms.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RBAC 业务服务：管理角色以及用户-角色分配关系。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacService {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ResourceRepository resourceRepository;
    private final StrategyRepository strategyRepository;
    private final RoleStrategyRepository roleStrategyRepository;
    private final StrategyResourceRepository strategyResourceRepository;

    @Transactional(readOnly = true)
    public List<RoleDTO> listRoles(UUID tenantId) {
        UUID t = tenantId != null ? tenantId : TenantContext.getTenantId();
        if (t == null) {
            return List.of();
        }
        return roleRepository.findByTenantId(t).stream().map(this::toDTO).toList();
    }

    @Transactional
    public RoleDTO createRole(RoleCreateRequest request) {
        UUID tenantId = request.getTenantId() != null ? request.getTenantId() : TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (roleRepository.existsByTenantIdAndCode(tenantId, request.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "角色编码已存在");
        }
        Role role = Role.builder()
                .type(request.getType() == null ? "custom" : request.getType())
                .tenantId(tenantId)
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .status("active")
                .updatedAt(OffsetDateTime.now())
                .build();
        return toDTO(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public RoleDTO getRole(Long id) {
        Role r = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
        return toDTO(r);
    }

    @Transactional
    public RoleDTO updateRole(Long id, RoleCreateRequest request) {
        Role r = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
        if (request.getName() != null) r.setName(request.getName());
        if (request.getDescription() != null) r.setDescription(request.getDescription());
        if (request.getType() != null) r.setType(request.getType());
        r.setUpdatedAt(OffsetDateTime.now());
        return toDTO(roleRepository.save(r));
    }

    public void assignRoleToUser(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "角色列表不能为空");
        }
        userRoleRepository.deleteByUserId(userId);
        Long operator = TenantContext.getUserId();
        List<UserRole> newList = roleIds.stream().distinct().map(rid ->
                UserRole.builder().userId(userId).roleId(rid).grantedBy(operator).build()
        ).toList();
        userRoleRepository.saveAll(newList);
        log.info("用户 {} 已分配角色 {}", userId, roleIds);
    }


    @Transactional(readOnly = true)
    public RolePermissionsDTO getRolePermissions(Long roleId) {
        Role role = getTenantRole(roleId);
        List<Resource> resources = resourceRepository.findByTenantIdAndStatus(role.getTenantId(), "active");
        List<RoleStrategy> roleStrategies = roleStrategyRepository.findByRoleIdIn(List.of(roleId));
        List<String> selected = new ArrayList<>();
        if (!roleStrategies.isEmpty()) {
            List<Long> strategyIds = roleStrategies.stream().map(RoleStrategy::getStrategyId).toList();
            List<StrategyResource> bindings = strategyResourceRepository.findByStrategyIdIn(strategyIds);
            List<Long> resourceIds = bindings.stream().map(StrategyResource::getResourceId).toList();
            for (Resource r : resourceRepository.findByIdIn(resourceIds)) {
                selected.add(r.getCode());
            }
        }
        return RolePermissionsDTO.builder()
                .roleId(roleId)
                .selectedCodes(selected)
                .resources(resources.stream().map(r -> RolePermissionsDTO.ResourceDTO.builder()
                        .id(r.getId())
                        .code(r.getCode())
                        .name(r.getName())
                        .type(r.getType())
                        .parentId(r.getParentId())
                        .path(r.getPath())
                        .build()).toList())
                .build();
    }

    @Transactional
    public RolePermissionsDTO setRolePermissions(Long roleId, RolePermissionsRequest request) {
        Role role = getTenantRole(roleId);
        UUID tenantId = role.getTenantId();
        List<RoleStrategy> existingRoleStrategies = roleStrategyRepository.findByRoleIdIn(List.of(roleId));
        List<Long> oldStrategyIds = existingRoleStrategies.stream().map(RoleStrategy::getStrategyId).toList();
        if (!oldStrategyIds.isEmpty()) {
            for (Long sid : oldStrategyIds) {
                List<StrategyResource> oldBindings = strategyResourceRepository.findByStrategyIdIn(List.of(sid));
                if (!oldBindings.isEmpty()) strategyResourceRepository.deleteAll(oldBindings);
            }
            roleStrategyRepository.deleteAll(existingRoleStrategies);
            strategyRepository.deleteAllById(oldStrategyIds);
        }

        Strategy strategy = strategyRepository.save(Strategy.builder()
                .tenantId(tenantId)
                .name(role.getName() + "权限策略")
                .description("租户管理员为角色 " + role.getCode() + " 维护的权限策略")
                .status("active")
                .updatedAt(OffsetDateTime.now())
                .build());
        roleStrategyRepository.save(RoleStrategy.builder().roleId(roleId).strategyId(strategy.getId()).build());

        List<String> codes = request.getResourceCodes() == null ? List.of() : request.getResourceCodes().stream().distinct().toList();
        if (!codes.isEmpty()) {
            List<Resource> resources = resourceRepository.findByTenantIdAndStatus(tenantId, "active").stream()
                    .filter(r -> codes.contains(r.getCode()))
                    .toList();
            List<StrategyResource> bindings = resources.stream()
                    .map(r -> StrategyResource.builder().strategyId(strategy.getId()).resourceId(r.getId()).operations(new String[]{"view"}).build())
                    .toList();
            strategyResourceRepository.saveAll(bindings);
        }
        return getRolePermissions(roleId);
    }

    private Role getRoleEntity(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }

    private Role getTenantRole(Long id) {
        Role role = getRoleEntity(id);
        UUID currentTenantId = TenantContext.getTenantId();
        if (currentTenantId != null && !currentTenantId.equals(role.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能操作其他租户的角色");
        }
        return role;
    }

    private RoleDTO toDTO(Role r) {
        return RoleDTO.builder()
                .id(r.getId())
                .tenantId(r.getTenantId())
                .code(r.getCode())
                .name(r.getName())
                .type(r.getType())
                .description(r.getDescription())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
