/*
 * 平台按钮配置，映射 platform_button_configs。
 * 设计：D13 列表页布局统一规范。
 *   - tenant_id IS NULL  => 平台默认（admin-vue 预置）
 *   - tenant_id = 租户ID  => 租户级覆盖（tenant_admin 调整）
 *   - scope: toolbar（顶部工具栏）/ row（行内操作）
 *   - permission_code 与 role_template_resources.actions 关联，v-has 指令判定
 */
package com.dms.platform.config.entity;

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
@Table(name = "platform_button_configs")
public class PlatformButtonConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "page_key", nullable = false, length = 100)
    private String pageKey;

    @Column(name = "tenant_type", nullable = false, length = 16)
    private String tenantType;

    @Column(name = "scope", nullable = false, length = 16)
    private String scope;

    @Column(name = "button_key", nullable = false, length = 100)
    private String buttonKey;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    @Column(name = "button_type", nullable = false, length = 16)
    private String buttonType;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "permission_code", length = 128)
    private String permissionCode;

    @Column(name = "visible", nullable = false)
    private Boolean visible;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "row_button_position", nullable = false, length = 16)
    private String rowButtonPosition;

    @Column(name = "confirm_required", nullable = false)
    private Boolean confirmRequired;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}