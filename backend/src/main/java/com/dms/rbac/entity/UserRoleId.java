/*
 * 用户-角色 关联实体的复合主键类。
 */
package com.dms.rbac.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * user_id + role_id 组成的复合主键。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleId implements Serializable {
    private Long userId;
    private Long roleId;
}
