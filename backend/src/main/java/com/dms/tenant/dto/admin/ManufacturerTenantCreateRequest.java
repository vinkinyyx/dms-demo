package com.dms.tenant.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ManufacturerTenantCreateRequest {

    @NotBlank(message = "租户编码不能为空")
    @Size(max = 32)
    private String code;

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 200)
    private String name;

    @Size(max = 64)
    private String contactName;

    @Size(max = 32)
    private String contactPhone;

    @Size(max = 128)
    private String contactEmail;

    @NotBlank(message = "管理员账号不能为空")
    @Size(max = 64)
    private String adminUsername;

    @NotBlank(message = "管理员初始密码不能为空")
    @Size(min = 6, max = 64)
    private String adminPassword;

    @NotBlank(message = "管理员姓名不能为空")
    @Size(max = 64)
    private String adminName;

    @Size(max = 500)
    private String remark;
}
