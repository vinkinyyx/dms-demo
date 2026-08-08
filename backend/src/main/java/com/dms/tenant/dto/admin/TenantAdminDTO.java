package com.dms.tenant.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TenantAdminDTO {

    private Long id;
    private UUID tenantId;
    private String tenantCode;
    private String tenantName;
    private String username;
    private String name;
    private String status;
    private Boolean mustChangePassword;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
}
