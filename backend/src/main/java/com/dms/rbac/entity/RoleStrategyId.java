/*
 * 角色-策略 关联实体的复合主键。
 */
package com.dms.rbac.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * role_id + strategy_id 复合主键。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleStrategyId implements Serializable {
    private Long roleId;
    private Long strategyId;
}
