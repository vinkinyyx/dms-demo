/*
 * 策略-资源关联实体，映射 strategy_resources 表。
 * 注意：operations 数组字段涉及 PostgreSQL text[]，V1 简化实现暂不映射到 Entity。
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
 * 策略 -> 资源 的关联，operations 字段暂不通过 JPA 直接映射。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "strategy_resources")
@IdClass(StrategyResourceId.class)
public class StrategyResource {

    @Id
    @Column(name = "strategy_id")
    private Long strategyId;

    @Id
    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
