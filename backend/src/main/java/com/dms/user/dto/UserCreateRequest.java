package com.dms.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128)
    private String email;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Size(max = 32)
    private String phone;

    private Long orgId;
    private Long dealerId;

    /** 分配的角色 ID（一个账号一个角色，必填） */
    @jakarta.validation.constraints.NotNull(message = "角色不能为空")
    private Long roleId;
    private Map<String, Object> attrs;
}