/*
 * 租户创建请求 DTO，包含必填的租户编码、名称、行业等。
 */
package com.dms.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/**
 * 创建租户请求：仅保留 V1 必要字段。
 */
@Data
public class TenantCreateRequest {

    @NotBlank(message = "租户编码不能为空")
    @Size(max = 32)
    private String code;

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "所属行业不能为空")
    @Size(max = 32)
    private String industry;

    @Size(max = 64)
    private String timezone;

    private String logoUrl;

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
