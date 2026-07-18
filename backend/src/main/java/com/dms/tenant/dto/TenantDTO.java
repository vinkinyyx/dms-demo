/*
 * 租户返回 DTO，向外部暴露的租户视图。
 */
package com.dms.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 租户对外 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDTO {

    private UUID id;
    private String code;
    private String name;
    private String industry;
    private String timezone;
    private String logoUrl;
    private String status;
    private Map<String, Object> modulesEnabled;
    private Map<String, Object> quota;
    private Map<String, Object> attrs;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
