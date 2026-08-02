package com.dms.apilog;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 接口调用日志实体（v3.8.2）。
 * direction=IN 记录外部调用 DMS；direction=OUT 记录 DMS 调用外部系统。
 */
@Data
@Entity
@Table(name = "api_call_log")
public class ApiCallLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(length = 32)
    private String system;

    @Column(length = 32)
    private String endpoint;

    @Column(name = "http_method", length = 8)
    private String httpMethod;

    @Column(length = 1024)
    private String url;

    @Column(length = 512)
    private String path;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "biz_code")
    private Integer bizCode;

    private Boolean success;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 64)
    private String username;

    @Column(name = "app_key", length = 64)
    private String appKey;

    @Column(name = "request_headers", columnDefinition = "text")
    private String requestHeaders;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "error_msg", columnDefinition = "text")
    private String errorMsg;

    @Column(name = "spent_ms")
    private Long spentMs;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}
