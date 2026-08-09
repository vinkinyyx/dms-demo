package com.dms.approval.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class TemplateSummaryDTO {
    private Long id;
    private UUID tenantId;
    private String businessType;
    private String code;
    private String name;
    private Integer versionNo;
    private String templateType;
    private String status;
    private Integer priority;
    private String rejectPolicy;
    private Map<String, Object> conditionConfig;
    private Integer timeoutHours;
    private Integer remindIntervalHours;
    private Integer maxRemindCount;
    private String description;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
