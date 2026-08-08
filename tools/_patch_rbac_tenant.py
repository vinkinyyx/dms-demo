from pathlib import Path
p=Path('backend/src/main/java/com/dms/rbac/service/RbacService.java')
s=p.read_text(encoding='utf-8')
s=s.replace('''        Role role = getRoleEntity(roleId);
        List<Resource> resources = resourceRepository.findByTenantId(role.getTenantId());''','''        Role role = getTenantRole(roleId);
        List<Resource> resources = resourceRepository.findByTenantId(role.getTenantId());''')
s=s.replace('''        Role role = getRoleEntity(roleId);
        UUID tenantId = role.getTenantId();''','''        Role role = getTenantRole(roleId);
        UUID tenantId = role.getTenantId();''')
s=s.replace('''    private Role getRoleEntity(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }
''','''    private Role getRoleEntity(Long id) {
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
''')
p.write_text(s, encoding='utf-8', newline='\n')
