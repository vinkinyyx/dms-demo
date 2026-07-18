/*
 * 认证控制器，聚合登录、登出、令牌刷新、密码重置、微信登录相关接口。
 */
package com.dms.auth.controller;

import com.dms.auth.dto.ChangePasswordRequest;
import com.dms.auth.dto.ForgotPasswordRequest;
import com.dms.auth.dto.LoginRequest;
import com.dms.auth.dto.LoginResponse;
import com.dms.auth.dto.RefreshTokenRequest;
import com.dms.auth.dto.ResetPasswordRequest;
import com.dms.auth.dto.WechatBindRequest;
import com.dms.auth.dto.WechatCallbackRequest;
import com.dms.auth.dto.WechatCallbackResponse;
import com.dms.auth.dto.WechatQrRequest;
import com.dms.auth.dto.WechatQrResponse;
import com.dms.auth.service.AuthService;
import com.dms.auth.service.WechatMockService;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.user.dto.UserDTO;
import com.dms.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：/auth/**。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final WechatMockService wechatMockService;
    private final UserService userService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, resolveIp(httpRequest)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null) {
            authService.logout(request.getRefreshToken());
        }
        return ApiResponse.ok();
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserDTO> me() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(userService.get(userId));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.ok();
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok();
    }

    @PostMapping("/wechat/qrcode")
    public ApiResponse<WechatQrResponse> wechatQrcode(@RequestBody(required = false) WechatQrRequest request) {
        return ApiResponse.ok(wechatMockService.generateQrCode());
    }

    @PostMapping("/wechat/callback")
    public ApiResponse<WechatCallbackResponse> wechatCallback(@Valid @RequestBody WechatCallbackRequest request) {
        return ApiResponse.ok(wechatMockService.handleCallback(request.getCode(), request.getState()));
    }

    @PostMapping("/wechat/bind")
    public ApiResponse<LoginResponse> wechatBind(@Valid @RequestBody WechatBindRequest request) {
        return ApiResponse.ok(wechatMockService.bind(
                request.getBindToken(),
                request.getUsername(),
                request.getPassword(),
                request.getTenantCode()
        ));
    }

    @PostMapping("/wechat/unbind")
    public ApiResponse<Void> wechatUnbind() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        wechatMockService.unbind(userId);
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
