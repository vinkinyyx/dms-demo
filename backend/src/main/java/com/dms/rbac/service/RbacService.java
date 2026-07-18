/*
 * RBAC 服务：负责角色 CRUD 与用户-角色分配。
 */
package com.dms.rbac.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.rbac.dto.RoleCreateRequest;
import com.dms.rbac.dto.RoleDTO;
import com.dms.rbac.entity.Role;
import com.dms.rbac.entity.UserRole;
import com.dms.rbac.repository.RoleRepository;
import com.dms.rbac.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
                .tenantId(tenantId)
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .status("active")
                .updatedAt(OffsetDateTime.now())
                .build();
        return toDTO(roleRepository.save(role));
    }

    @Transactional
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

    private RoleDTO toDTO(Role r) {
        return RoleDTO.builder()
                .id(r.getId())
                .tenantId(r.getTenantId())
                .code(r.getCode())
                .name(r.getName())
                .description(r.getDescription())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
