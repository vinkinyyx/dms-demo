/*
 * 认证业务服务：用户名密码登录、修改密码、刷新令牌、登出黑名单等。
 */
package com.dms.auth.service;

import com.dms.auth.dto.ChangePasswordRequest;
import com.dms.auth.dto.ForgotPasswordRequest;
import com.dms.auth.dto.LoginRequest;
import com.dms.auth.dto.LoginResponse;
import com.dms.auth.dto.ResetPasswordRequest;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.security.JwtUtil;
import com.dms.tenant.entity.Tenant;
import com.dms.tenant.repository.TenantRepository;
import com.dms.user.entity.User;
import com.dms.user.repository.UserRepository;
import com.dms.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务：登录、密码变更、Token 刷新、登出黑名单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BLACKLIST_PREFIX = "dms:auth:blacklist:";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedissonClient redissonClient;

    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        User user = locateUser(request.getTenantCode(), request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被锁定，请稍后再试");
        }
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被停用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            userService.incrementFailCount(user.getId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        userService.resetFailCount(user.getId(), clientIp);

        String access = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getTenantId().toString());
        String refresh = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getTenantId().toString());

        return LoginResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .accessTokenExpiresIn(jwtUtil.getAccessTokenTtl() / 1000)
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .user(userService.toDTO(user))
                .build();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        User user = userService.loadUser(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setPasswordUpdatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    /**
     * V1 占位：忘记密码通过管理员通道重置，此处直接返回成功。
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("收到忘记密码请求 email={}, V1 由管理员统一重置", request.getEmail());
    }

    /**
     * V1 占位：重置密码接口保留供后续启用邮件通道。
     */
    public void resetPassword(ResetPasswordRequest request) {
        log.info("收到重置密码请求 token={}, V1 暂未启用邮件通道", request.getToken());
    }

    public LoginResponse refreshToken(String refreshToken) {
        Claims claims;
        try {
            claims = jwtUtil.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "refreshToken 非法");
        }
        if (!jwtUtil.isRefreshToken(claims)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "非 refresh 类型令牌");
        }
        if (isBlacklisted(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "该令牌已失效");
        }
        Long userId = Long.valueOf(claims.get(JwtUtil.CLAIM_USER_ID).toString());
        User user = userService.loadUser(userId);
        String access = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getTenantId().toString());
        String newRefresh = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getTenantId().toString());
        return LoginResponse.builder()
                .accessToken(access)
                .refreshToken(newRefresh)
                .accessTokenExpiresIn(jwtUtil.getAccessTokenTtl() / 1000)
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .user(userService.toDTO(user))
                .build();
    }

    /**
     * 登出：将 refreshToken 加入 Redis 黑名单，简化实现。
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            Claims claims = jwtUtil.parse(refreshToken);
            long ttlMillis = Math.max(claims.getExpiration().getTime() - System.currentTimeMillis(), 1000L);
            RBucket<String> bucket = redissonClient.getBucket(BLACKLIST_PREFIX + refreshToken);
            bucket.set("1", ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            log.warn("登出黑名单写入失败: {}", ex.getMessage());
        }
    }

    private boolean isBlacklisted(String token) {
        RBucket<String> bucket = redissonClient.getBucket(BLACKLIST_PREFIX + token);
        return bucket.isExists();
    }

    /**
     * 根据可选 tenantCode + username 定位用户。
     */
    private User locateUser(String tenantCode, String username) {
        UUID tenantId = null;
        if (tenantCode != null && !tenantCode.isBlank()) {
            Optional<Tenant> tenant = tenantRepository.findByCode(tenantCode);
            if (tenant.isEmpty()) {
                return null;
            }
            tenantId = tenant.get().getId();
        }
        if (tenantId != null) {
            return userRepository.findByTenantIdAndUsername(tenantId, username).orElse(null);
        }
        // 未指定 tenantCode：V1 简化，在全局按 username 唯一匹配（超管场景）
        return userRepository.findAll().stream()
                .filter(u -> username.equals(u.getUsername()))
                .findFirst()
                .orElse(null);
    }
}
