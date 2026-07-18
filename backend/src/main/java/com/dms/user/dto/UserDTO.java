/*
 * 用户对外 DTO，屏蔽 passwordHash 等敏感字段。
 */
package com.dms.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 用户返回 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private UUID tenantId;
    private String username;
    private String name;
    private String userType;
    private Boolean mustChangePassword;
    private String email;
    private String phone;
    private Long orgId;
    private Long dealerId;
    private String status;
    private Integer loginFailCount;
    private OffsetDateTime lockedUntil;
    private OffsetDateTime lastLoginAt;
    private String lastLoginIp;
    private Map<String, Object> attrs;
    private Boolean wechatBound;
    private OffsetDateTime wechatBoundAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
