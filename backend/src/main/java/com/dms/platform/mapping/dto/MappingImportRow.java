package com.dms.platform.mapping.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MappingImportRow {
    private int rowNumber;
    private String dealerCode;
    private String manufacturerProductCode;
    private String dealerProductCode;
    private String packageUnit;
    private String conversionRate;
}