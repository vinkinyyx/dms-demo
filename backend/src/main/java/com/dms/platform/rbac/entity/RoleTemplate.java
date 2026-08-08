/*
 * 平台默认角色模板，映射 role_templates 表。租户开通时按模板复制到租户内 roles。
 */
package com.dms.platform.rbac.entity;

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
@Table(name = "role_templates")
public class RoleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "tenant_type", nullable = false, length = 16)
    private String tenantType;

    @Column(name = "data_scope", nullable = false, length = 32)
    private String dataScope;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}