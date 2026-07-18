/*
 * 角色创建请求 DTO。
 */
package com.dms.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 创建角色请求。
 */
@Data
public class RoleCreateRequest {

    private UUID tenantId;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64)
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 200)
    private String name;

    private String description;
}
