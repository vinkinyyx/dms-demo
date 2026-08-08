/*
 * HTTP 接口日志元数据，映射 api_http_logs。原始请求/响应报文存 MinIO。
 */
package com.dms.platform.apilog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_http_logs")
public class ApiHttpLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", length = 64)
    private String requestId;
    @Column(name = "trace_id", length = 64)
    private String traceId;
    @Column(name = "tenant_id")
    private UUID tenantId;
    @Column(name = "tenant_type", length = 16)
    private String tenantType;
    @Column(name = "owner_manufacturer_id")
    private UUID ownerManufacturerId;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "username", length = 64)
    private String username;
    @Column(name = "auth_source", length = 16)
    private String authSource;
    @Column(name = "http_method", length = 8)
    private String httpMethod;
    @Column(name = "path", length = 255)
    private String path;
    @Column(name = "query_string")
    private String queryString;
    @Column(name = "status_code")
    private Integer statusCode;
    @Column(name = "biz_code")
    private Integer bizCode;
    @Column(name = "success")
    private Boolean success;
    @Column(name = "slow", nullable = false)
    private Boolean slow;
    @Column(name = "spent_ms")
    private Long spentMs;
    @Column(name = "client_ip", length = 64)
    private String clientIp;
    @Column(name = "user_agent", length = 512)
    private String userAgent;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "request_object_key")
    private String requestObjectKey;
    @Column(name = "response_object_key")
    private String responseObjectKey;
    @Column(name = "request_size")
    private Long requestSize;
    @Column(name = "response_size")
    private Long responseSize;
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;
    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}