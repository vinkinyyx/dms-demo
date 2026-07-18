/*
 * 忘记密码请求 DTO（V1 占位）。
 */
package com.dms.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 忘记密码请求。
 */
@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email
    private String email;
}
