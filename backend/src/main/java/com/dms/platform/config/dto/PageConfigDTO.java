package com.dms.platform.config.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PageConfigDTO {
    private Long id;
    private String pageKey;
    private String tenantType;
    private String fieldKey;
    private String label;
    private Boolean visible;
    private Boolean readonly;
    private Boolean required;
    private Boolean exportable;
    private Integer sortOrder;
    private Integer width;
    private Map<String, Object> config;
    private String status;
}