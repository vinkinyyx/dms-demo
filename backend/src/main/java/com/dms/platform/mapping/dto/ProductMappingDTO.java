package com.dms.platform.mapping.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ProductMappingDTO {
    private Long id;
    private UUID manufacturerTenantId;
    private UUID dealerTenantId;
    private String dealerTenantName;
    private Long manufacturerProductId;
    private Long dealerProductId;
    private String manufacturerProductCode;
    private String dealerProductCode;
    private String manufacturerProductName;
    private String dealerProductName;
    private String packageUnit;
    private BigDecimal conversionRate;
    private String status;
    private String remark;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}