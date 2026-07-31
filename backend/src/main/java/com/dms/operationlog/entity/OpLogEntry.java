package com.dms.operationlog.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 全链路操作日志条目（v3.6.2）。
 * 由 {@link com.dms.operationlog.service.OperationLogRecordService} 写入。
 */
@Data
@Entity
@Table(name = "op_log")
public class OpLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 64)
    private String username;

    @Column(nullable = false, length = 16)
    private String layer;

    @Column(length = 255)
    private String method;

    @Column(name = "http_method", length = 8)
    private String httpMethod;

    @Column(length = 255)
    private String path;

    private Integer status;

    @Column(name = "spent_ms")
    private Long spentMs;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(columnDefinition = "text")
    private String response;

    @Column(columnDefinition = "text")
    private String stack;

    @Column(name = "biz_type", length = 32)
    private String bizType;

    @Column(name = "biz_id", length = 64)
    private String bizId;

    @Column(length = 16)
    private String action;

    @Column(length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
