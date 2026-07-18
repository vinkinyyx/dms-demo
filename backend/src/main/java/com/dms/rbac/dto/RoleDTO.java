/*
 * 角色 DTO。
 */
package com.dms.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 角色返回 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
    private Long id;
    private UUID tenantId;
    private String code;
    private String name;
    private String description;
    private String status;
    private OffsetDateTime createdAt;
}
