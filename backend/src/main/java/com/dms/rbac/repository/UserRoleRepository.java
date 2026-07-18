/*
 * 用户-角色关联仓储。
 */
package com.dms.rbac.repository;

import com.dms.rbac.entity.UserRole;
import com.dms.rbac.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户-角色仓储：查询用户拥有的角色列表。
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
