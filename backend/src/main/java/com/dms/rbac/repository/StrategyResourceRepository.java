/*
 * 策略-资源关联仓储。
 */
package com.dms.rbac.repository;

import com.dms.rbac.entity.StrategyResource;
import com.dms.rbac.entity.StrategyResourceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 策略-资源仓储：按策略 ID 集合查询资源。
 */
@Repository
public interface StrategyResourceRepository extends JpaRepository<StrategyResource, StrategyResourceId> {

    List<StrategyResource> findByStrategyIdIn(Collection<Long> strategyIds);
}
