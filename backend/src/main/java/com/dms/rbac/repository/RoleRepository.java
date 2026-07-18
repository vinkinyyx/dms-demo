/*
 * 角色仓储，按租户查询与主键操作。
 */
package com.dms.rbac.repository;

import com.dms.rbac.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 角色仓储。
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByTenantId(UUID tenantId);

    Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
