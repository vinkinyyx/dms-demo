package com.dms.platform.rbac.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class RoleTemplateDTO {
    private Long id;
    private String code;
    private String name;
    private String tenantType;
    private String dataScope;
    private String description;
    private String status;
    private List<String> resourceCodes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}