/*
 * 资源实体，映射 resources 表，代表菜单/接口/按钮等可授权对象。
 */
package com.dms.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 资源：菜单 / API / 按钮，作为最小权限单元。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "resources")
@SQLRestriction("deleted_at IS NULL")
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", length = 128, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "type", length = 16, nullable = false)
    private String type;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "path", length = 500)
    private String path;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
