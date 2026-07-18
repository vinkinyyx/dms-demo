/*
 * 策略-资源 关联实体的复合主键。
 */
package com.dms.rbac.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * strategy_id + resource_id 复合主键。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StrategyResourceId implements Serializable {
    private Long strategyId;
    private Long resourceId;
}
