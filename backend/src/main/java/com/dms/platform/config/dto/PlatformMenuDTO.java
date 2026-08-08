package com.dms.platform.config.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformMenuDTO {
    private Long id;
    private String menuKey;
    private String parentKey;
    private String label;
    private String icon;
    private String route;
    private String permissionCode;
    private String tenantType;
    private Boolean visible;
    private Integer sortOrder;
    private String status;
}