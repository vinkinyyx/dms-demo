/*
 * 策略仓储。
 */
package com.dms.rbac.repository;

import com.dms.rbac.entity.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 策略仓储。
 */
@Repository
public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    List<Strategy> findByTenantId(UUID tenantId);
}
