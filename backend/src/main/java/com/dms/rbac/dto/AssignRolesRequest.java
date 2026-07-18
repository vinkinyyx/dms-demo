/*
 * 用户分配角色请求 DTO。
 */
package com.dms.rbac.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 为用户分配角色的请求体。
 */
@Data
public class AssignRolesRequest {

    @NotEmpty(message = "角色 ID 列表不能为空")
    private List<Long> roleIds;
}
