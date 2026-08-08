from pathlib import Path
p=Path('backend/src/main/java/com/dms/rbac/service/RbacService.java')
s=p.read_text(encoding='utf-8')
s=s.replace('import com.dms.rbac.dto.RoleCreateRequest;\nimport com.dms.rbac.dto.RoleDTO;', 'import com.dms.rbac.dto.RoleCreateRequest;\nimport com.dms.rbac.dto.RoleDTO;\nimport com.dms.rbac.dto.RolePermissionsDTO;\nimport com.dms.rbac.dto.RolePermissionsRequest;')
s=s.replace('import com.dms.rbac.entity.Role;\nimport com.dms.rbac.entity.UserRole;', 'import com.dms.rbac.entity.Resource;\nimport com.dms.rbac.entity.Role;\nimport com.dms.rbac.entity.RoleStrategy;\nimport com.dms.rbac.entity.Strategy;\nimport com.dms.rbac.entity.StrategyResource;\nimport com.dms.rbac.entity.UserRole;')
s=s.replace('import com.dms.rbac.repository.RoleRepository;\nimport com.dms.rbac.repository.UserRoleRepository;', 'import com.dms.rbac.repository.ResourceRepository;\nimport com.dms.rbac.repository.RoleRepository;\nimport com.dms.rbac.repository.RoleStrategyRepository;\nimport com.dms.rbac.repository.StrategyRepository;\nimport com.dms.rbac.repository.StrategyResourceRepository;\nimport com.dms.rbac.repository.UserRoleRepository;')
s=s.replace('import java.util.List;\nimport java.util.UUID;', 'import java.util.ArrayList;\nimport java.util.List;\nimport java.util.UUID;')
s=s.replace('private final UserRoleRepository userRoleRepository;', 'private final UserRoleRepository userRoleRepository;\n    private final ResourceRepository resourceRepository;\n    private final StrategyRepository strategyRepository;\n    private final RoleStrategyRepository roleStrategyRepository;\n    private final StrategyResourceRepository strategyResourceRepository;')
insert_after='''    public void assignRoleToUser(Long userId, List<Long> roleIds) {
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
'''
addition='''

    @Transactional(readOnly = true)
    public RolePermissionsDTO getRolePermissions(Long roleId) {
        Role role = getRoleEntity(roleId);
        List<Resource> resources = resourceRepository.findByTenantId(role.getTenantId());
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
        Role role = getRoleEntity(roleId);
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
            List<Resource> resources = resourceRepository.findByTenantId(tenantId).stream()
                    .filter(r -> codes.contains(r.getCode()))
                    .toList();
            List<StrategyResource> bindings = resources.stream()
                    .map(r -> StrategyResource.builder().strategyId(strategy.getId()).resourceId(r.getId()).build())
                    .toList();
            strategyResourceRepository.saveAll(bindings);
        }
        return getRolePermissions(roleId);
    }

    private Role getRoleEntity(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }
'''
if insert_after not in s:
    raise SystemExit('assign block not found')
s=s.replace(insert_after, insert_after+addition)
p.write_text(s, encoding='utf-8', newline='\n')
