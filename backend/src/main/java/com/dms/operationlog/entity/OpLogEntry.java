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

    /**
     * v4.4.6：落库前按列长度安全截断，避免长 Java 方法签名 / 长 path / 长 User-Agent /
     * 长 remark 触发 PostgreSQL "value too long for type character varying(255)" 导致整条操作日志写入失败。
     */
    @PrePersist
    void enforceLengths() {
        this.requestId = clip(this.requestId, 64);
        this.traceId = clip(this.traceId, 64);
        this.username = clip(this.username, 64);
        this.layer = clip(this.layer, 16);
        this.method = clip(this.method, 255);
        this.httpMethod = clip(this.httpMethod, 8);
        this.path = clip(this.path, 255);
        this.ip = clip(this.ip, 64);
        this.userAgent = clip(this.userAgent, 255);
        this.bizType = clip(this.bizType, 32);
        this.bizId = clip(this.bizId, 64);
        this.action = clip(this.action, 16);
        this.remark = clip(this.remark, 255);
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
    }
}
