/*
 * 刷新 token 请求 DTO。
 */
package com.dms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 使用 refreshToken 刷新 accessToken。
 */
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
