package com.dms.platform.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
public class PlatformAuditLogDTO {
    private Long id;
    private Long adminUserId;
    private String adminUsername;
    private String action;
    private String targetType;
    private String targetId;
    private Map<String, Object> beforeJson;
    private Map<String, Object> afterJson;
    private String ip;
    private String userAgent;
    private Boolean success;
    private String errorMessage;
    private OffsetDateTime createdAt;
}