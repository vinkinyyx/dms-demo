package com.dms.tenant.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class TenantAdminCreateRequest {

    @NotNull(message = "租户不能为空")
    private UUID tenantId;

    @NotBlank(message = "管理员账号不能为空")
    @Size(max = 64)
    private String username;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 64)
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64)
    private String name;
}
