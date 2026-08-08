/*
 * 角色模板与资源权限点（菜单/功能编码）关联，映射 role_template_resources。
 */
package com.dms.platform.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "role_template_resources")
@IdClass(RoleTemplateResource.RoleTemplateResourceId.class)
public class RoleTemplateResource {

    @Id
    @Column(name = "template_id")
    private Long templateId;

    @Id
    @Column(name = "resource_code", length = 128)
    private String resourceCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleTemplateResourceId implements Serializable {
        private Long templateId;
        private String resourceCode;
    }
}