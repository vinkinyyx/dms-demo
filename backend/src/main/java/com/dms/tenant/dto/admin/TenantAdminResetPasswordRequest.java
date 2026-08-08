package com.dms.tenant.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TenantAdminResetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64)
    private String newPassword;
}
