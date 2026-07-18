/*
 * 角色-策略关联实体，映射 role_strategies 表。
 */
package com.dms.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 角色 -> 策略 的多对多关联。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "role_strategies")
@IdClass(RoleStrategyId.class)
public class RoleStrategy {

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "strategy_id")
    private Long strategyId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
