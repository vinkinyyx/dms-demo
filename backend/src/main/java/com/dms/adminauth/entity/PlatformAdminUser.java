/*
 * 平台后台管理员实体，映射 platform_admin_users 表，与业务 users 表隔离。
 */
package com.dms.adminauth.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "platform_admin_users")
public class PlatformAdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "must_change_password")
    private Boolean mustChangePassword;

    @Column(name = "password_updated_at")
    private OffsetDateTime passwordUpdatedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @Column(name = "login_fail_count")
    private Integer loginFailCount;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "attrs", columnDefinition = "jsonb")
    private Map<String, Object> attrs;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    public void ensureAttrs() {
        if (attrs == null) {
            attrs = new HashMap<>();
        }
    }
}
