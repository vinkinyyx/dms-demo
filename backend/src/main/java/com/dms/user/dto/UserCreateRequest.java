/*
 * 用户创建请求 DTO。
 */
package com.dms.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * 用户创建请求。
 */
@Data
public class UserCreateRequest {

    private UUID tenantId;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64)
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64)
    private String name;

    @NotBlank(message = "用户类型不能为空")
    @Size(max = 16)
    private String userType;

    @NotBlank(message = "初始密码不能为空")
    private String password;

    @Email
    @Size(max = 128)
    private String email;

    @Size(max = 32)
    private String phone;

    private Long orgId;

    private Long dealerId;

    private Map<String, Object> attrs;
}
