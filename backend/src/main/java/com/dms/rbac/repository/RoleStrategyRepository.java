/*
 * 角色-策略关联仓储。
 */
package com.dms.rbac.repository;

import com.dms.rbac.entity.RoleStrategy;
import com.dms.rbac.entity.RoleStrategyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 角色-策略仓储：按角色 ID 集合查询关联的策略。
 */
@Repository
public interface RoleStrategyRepository extends JpaRepository<RoleStrategy, RoleStrategyId> {

    List<RoleStrategy> findByRoleIdIn(Collection<Long> roleIds);
}
