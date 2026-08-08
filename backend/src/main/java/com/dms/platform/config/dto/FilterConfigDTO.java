package com.dms.platform.config.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FilterConfigDTO {
    private Long id;
    private String pageKey;
    private String tenantType;
    private String filterKey;
    private String label;
    private String componentType;
    private String dictType;
    private String defaultValue;
    private Boolean multiple;
    private Boolean visible;
    private Integer sortOrder;
    private String status;
}