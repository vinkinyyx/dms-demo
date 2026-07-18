/*
 * 用户实体类，映射 users 表，承载租户内用户核心字段与登录相关状态。
 */
package com.dms.user.entity;

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
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 用户实体：包含租户内用户主档、登录状态与微信绑定字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "user_type", length = 16, nullable = false)
    private String userType;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "must_change_password")
    private Boolean mustChangePassword;

    @Column(name = "password_updated_at")
    private OffsetDateTime passwordUpdatedAt;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "login_fail_count")
    private Integer loginFailCount;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "attrs", columnDefinition = "jsonb")
    private Map<String, Object> attrs;

    @Column(name = "wechat_openid", length = 64)
    private String wechatOpenid;

    @Column(name = "wechat_unionid", length = 64)
    private String wechatUnionid;

    @Column(name = "wechat_bound_at")
    private OffsetDateTime wechatBoundAt;

    @Column(name = "sso_service_id", length = 64)
    private String ssoServiceId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureAttrs() {
        if (attrs == null) attrs = new HashMap<>();
    }
}
