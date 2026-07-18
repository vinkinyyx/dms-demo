/*
 * 登录返回 DTO。
 */
package com.dms.auth.dto;

import com.dms.user.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应：token 与用户信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpiresIn;
    private Boolean mustChangePassword;
    private UserDTO user;
}
