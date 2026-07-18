/*
 * 租户基本信息更新请求 DTO，仅允许调整名称、行业、联系人等基础字段。
 */
package com.dms.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/**
 * 更新租户基础信息请求 DTO。
 */
@Data
public class TenantUpdateRequest {

    @Size(max = 200)
    private String name;

    @Size(max = 32)
    private String industry;

    @Size(max = 64)
    private String timezone;

    private String logoUrl;

    private String status;

    private Map<String, Object> modulesEnabled;

    private Map<String, Object> quota;

    private Map<String, Object> attrs;

    @Size(max = 64)
    private String contactName;

    @Email
    @Size(max = 128)
    private String contactEmail;

    @Size(max = 32)
    private String contactPhone;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
