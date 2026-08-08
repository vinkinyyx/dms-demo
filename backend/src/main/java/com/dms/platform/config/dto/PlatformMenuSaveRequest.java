package com.dms.platform.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlatformMenuSaveRequest {
    @NotBlank
    @Size(max = 64)
    private String menuKey;
    @Size(max = 64)
    private String parentKey;
    @NotBlank
    @Size(max = 100)
    private String label;
    @Size(max = 64)
    private String icon;
    @Size(max = 200)
    private String route;
    @Size(max = 128)
    private String permissionCode;
    @NotBlank
    @Size(max = 16)
    private String tenantType;
    private Boolean visible;
    private Integer sortOrder;
}