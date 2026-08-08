/*
 * 平台菜单模板，映射 platform_menus 表，按 tenant_type 控制厂家/经销商可见菜单。
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "platform_menus")
public class PlatformMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_key", nullable = false, unique = true, length = 64)
    private String menuKey;

    @Column(name = "parent_key", length = 64)
    private String parentKey;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "route", length = 200)
    private String route;

    @Column(name = "permission_code", length = 128)
    private String permissionCode;

    @Column(name = "tenant_type", nullable = false, length = 16)
    private String tenantType;

    @Column(name = "visible", nullable = false)
    private Boolean visible;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}