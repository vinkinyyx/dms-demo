/*
 * 租户实体类，映射 tenants 表，承载多租户核心元数据与配置 JSONB 字段。
 */
package com.dms.tenant.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 租户实体：承载租户级基础信息、模块启用状态、配额与扩展属性。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenants")
@SQLRestriction("deleted_at IS NULL")
public class Tenant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", length = 32, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "industry", length = 32, nullable = false)
    private String industry;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "status", length = 16)
    private String status;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "modules_enabled", columnDefinition = "jsonb")
    private Map<String, Object> modulesEnabled;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "quota", columnDefinition = "jsonb")
    private Map<String, Object> quota;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "attrs", columnDefinition = "jsonb")
    private Map<String, Object> attrs;

    @Column(name = "contact_name", length = 64)
    private String contactName;

    @Column(name = "contact_email", length = 128)
    private String contactEmail;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /**
     * 保证 JSONB 字段非 null，避免序列化时出现异常。
     */
    public void ensureJsonFields() {
        if (modulesEnabled == null) modulesEnabled = new HashMap<>();
        if (quota == null) quota = new HashMap<>();
        if (attrs == null) attrs = new HashMap<>();
    }
}
