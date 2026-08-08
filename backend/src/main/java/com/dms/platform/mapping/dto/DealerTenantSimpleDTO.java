package com.dms.platform.mapping.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DealerTenantSimpleDTO {
    private UUID tenantId;
    private String code;
    private String name;
    private Long dealerId;
    private String dealerName;
    private String status;
}