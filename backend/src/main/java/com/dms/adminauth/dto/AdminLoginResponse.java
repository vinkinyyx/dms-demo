package com.dms.adminauth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminLoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private Boolean mustChangePassword;
    private AdminUserDTO user;
}
