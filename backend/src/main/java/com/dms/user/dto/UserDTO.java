/*
 * 用户对外 DTO，屏蔽 passwordHash 等敏感字段。
 */
package com.dms.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** 主角色 ID（一个账号一个角色） */
    private Long roleId;
    /** 主角色名称 */
    private String roleName;
    /** 用户当前拥有的角色 ID 列表（兼容，取主角色） */
    private List<Long> roleIds;
    /** 用户当前拥有的角色名称列表（展示用） */
    private List<String> roleNames;
    private String status;
    private Integer loginFailCount;
    private OffsetDateTime lockedUntil;
    private OffsetDateTime lastLoginAt;
    private String lastLoginIp;
    private Map<String, Object> attrs;
    /** 当前用户拥有的资源权限码（resource.code），由 PermissionQueryService 填充；前端 v-has 指令使用 */
    private Set<String> permissions;
    private Boolean wechatBound;
    private OffsetDateTime wechatBoundAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
