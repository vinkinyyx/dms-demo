package com.dms.platform.apilog.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ApiHttpLogDTO {
    private Long id;
    private String requestId;
    private String traceId;
    private UUID tenantId;
    private String tenantType;
    private UUID ownerManufacturerId;
    private Long userId;
    private String username;
    private String authSource;
    private String httpMethod;
    private String path;
    private String queryString;
    private Integer statusCode;
    private Integer bizCode;
    private Boolean success;
    private Boolean slow;
    private Long spentMs;
    private String clientIp;
    private String errorMessage;
    private Boolean hasRequestFile;
    private Boolean hasResponseFile;
    private Long requestSize;
    private Long responseSize;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}