/*
 * 平台后台认证控制器：登录、当前用户、退出、刷新、修改密码。
 */
package com.dms.adminauth.controller;

import com.dms.adminauth.dto.AdminChangePasswordRequest;
import com.dms.adminauth.dto.AdminLoginRequest;
import com.dms.adminauth.dto.AdminLoginResponse;
import com.dms.adminauth.dto.AdminUserDTO;
import com.dms.adminauth.dto.RefreshTokenRequest;
import com.dms.adminauth.service.AdminAuthService;
import com.dms.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                                 HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminAuthService.login(request, resolveIp(httpRequest)));
    }

    @GetMapping("/me")
    public ApiResponse<AdminUserDTO> me() {
        return ApiResponse.ok(adminAuthService.me());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null) {
            adminAuthService.logout(request.getRefreshToken());
        }
        return ApiResponse.ok();
    }

    @PostMapping("/refresh")
    public ApiResponse<AdminLoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(adminAuthService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AdminChangePasswordRequest request) {
        adminAuthService.changePassword(request);
        return ApiResponse.ok();
    }

    private String resolveIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(header)) {
            return header.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
