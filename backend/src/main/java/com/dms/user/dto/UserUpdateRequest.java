/*
 * 用户资料更新请求 DTO。
 */
package com.dms.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 用户资料更新请求。
 */
@Data
public class UserUpdateRequest {

    @Size(max = 64)
    private String name;

    @Email
    @Size(max = 128)
    private String email;

    @Size(max = 32)
    private String phone;

    private Long orgId;

    private Long dealerId;

    private String status;

    private Map<String, Object> attrs;
}
