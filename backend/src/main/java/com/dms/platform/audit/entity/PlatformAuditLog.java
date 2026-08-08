/*
 * 平台后台审计日志实体，映射 platform_audit_logs 表。
 */
package com.dms.platform.audit.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "platform_audit_logs")
public class PlatformAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "admin_username", length = 64)
    private String adminUsername;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private Map<String, Object> beforeJson;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "after_json", columnDefinition = "jsonb")
    private Map<String, Object> afterJson;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}