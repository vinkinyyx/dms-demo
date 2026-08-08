/*
 * 平台页面字段配置，映射 platform_page_configs。
 */
package com.dms.platform.config.entity;

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
@Table(name = "platform_page_configs")
public class PlatformPageConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "page_key", nullable = false, length = 100)
    private String pageKey;
    @Column(name = "tenant_type", nullable = false, length = 16)
    private String tenantType;
    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;
    @Column(name = "label", length = 100)
    private String label;
    @Column(name = "visible", nullable = false)
    private Boolean visible;
    @Column(name = "readonly", nullable = false)
    private Boolean readonly;
    @Column(name = "required", nullable = false)
    private Boolean required;
    @Column(name = "exportable", nullable = false)
    private Boolean exportable;
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
    @Column(name = "width")
    private Integer width;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;
    @Column(name = "status", nullable = false, length = 16)
    private String status;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}