package com.dms.tenant.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class AdminTenantDTO {

    private UUID id;
    private String code;
    private String name;
    private String status;
    private String tenantType;
    private String deploymentMode;
    private UUID ownerManufacturerId;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private Long boundDealerId;
    private UUID boundManufacturerTenantId;
    private String disableReason;
    private OffsetDateTime disabledAt;
    private OffsetDateTime enabledAt;
    private OffsetDateTime createdAt;
}
