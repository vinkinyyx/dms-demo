package com.dms.platform.mapping.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ProductMappingCreateRequest {
    @NotNull
    private UUID dealerTenantId;
    @NotNull
    private Long manufacturerProductId;
    @NotNull
    private Long dealerProductId;
    private String packageUnit;
    private BigDecimal conversionRate;
    private String remark;
}