/*
 * 微信账号绑定请求 DTO。
 */
package com.dms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信绑定请求：使用短期 bindToken + 账号密码完成绑定。
 */
@Data
public class WechatBindRequest {

    @NotBlank(message = "bindToken 不能为空")
    private String bindToken;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 多租户场景下可传入 tenantCode 定位租户 */
    private String tenantCode;
}
