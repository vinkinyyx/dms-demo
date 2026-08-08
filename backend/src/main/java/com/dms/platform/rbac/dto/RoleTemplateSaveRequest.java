package com.dms.platform.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleTemplateSaveRequest {

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Size(max = 16)
    private String tenantType;

    @NotBlank
    @Size(max = 32)
    private String dataScope;

    private String description;
}