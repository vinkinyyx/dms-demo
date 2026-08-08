package com.dms.platform.mapping.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductMappingUpdateRequest {
    private String packageUnit;
    private BigDecimal conversionRate;
    private String remark;
}