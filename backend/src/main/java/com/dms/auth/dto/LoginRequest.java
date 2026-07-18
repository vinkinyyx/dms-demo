/*
 * 登录请求 DTO。
 */
package com.dms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户名密码登录请求。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String captcha;

    private Boolean rememberMe;

    /** 可选：多租户场景下可传入租户编码用于定位 */
    private String tenantCode;
}
