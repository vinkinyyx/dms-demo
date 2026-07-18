/*
 * 重置密码请求 DTO（管理员触发的场景）。
 */
package com.dms.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户密码重置请求：管理员为指定用户设置新密码。
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度 8-64 位")
    private String newPassword;
}
