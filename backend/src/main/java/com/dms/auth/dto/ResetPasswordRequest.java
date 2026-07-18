/*
 * 密码重置请求 DTO（V1 占位）。
 */
package com.dms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密码重置请求（携带 token）。
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "token 不能为空")
    private String token;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64)
    private String newPassword;
}
