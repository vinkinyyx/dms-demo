/*
 * 平台后台认证服务：独立登录态、Redis 登出黑名单、登录失败锁定。
 */
package com.dms.adminauth.service;

import com.dms.adminauth.dto.AdminChangePasswordRequest;
import com.dms.adminauth.dto.AdminLoginRequest;
import com.dms.adminauth.dto.AdminLoginResponse;
import com.dms.adminauth.dto.AdminUserDTO;
import com.dms.adminauth.entity.PlatformAdminUser;
import com.dms.adminauth.repository.PlatformAdminUserRepository;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final int MAX_FAIL_COUNT = 9;
    private static final long LOCK_MINUTES = 30L;
    private static final String BLACKLIST_PREFIX = "dms:admin:auth:blacklist:";

    private final PlatformAdminUserRepository adminUserRepository;
    private final AdminJwtService adminJwtService;
    private final PasswordEncoder passwordEncoder;
    private final RedissonClient redissonClient;
    private final EntityManager em;

    @org.springframework.transaction.annotation.Transactional
    public AdminLoginResponse login(AdminLoginRequest request, String clientIp) {
        PlatformAdminUser admin = adminUserRepository.findByUsername(request.getUsername()).orElse(null);
        if (admin == null) {
            writePlatformLoginLog(null, clientIp, false, "用户名不存在");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(OffsetDateTime.now())) {
            writePlatformLoginLog(admin, clientIp, false, "账号已锁定");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被锁定，请稍后再试");
        }
        if (!"active".equalsIgnoreCase(admin.getStatus())) {
            writePlatformLoginLog(admin, clientIp, false, "账号已停用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被停用");
        }
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            incrementFailCount(admin);
            writePlatformLoginLog(admin, clientIp, false, "密码错误");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        OffsetDateTime now = OffsetDateTime.now();
        adminUserRepository.resetLoginState(admin.getId(), clientIp, now);

        writePlatformLoginLog(admin, clientIp, true, null);
        String access = adminJwtService.generateAccessToken(admin.getId(), admin.getUsername());
        String refresh = adminJwtService.generateRefreshToken(admin.getId(), admin.getUsername());
        log.info("平台后台管理员登录成功: username={}, ip={}", admin.getUsername(), clientIp);

        return AdminLoginResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(adminJwtService.getAccessTokenTtl() / 1000)
                .mustChangePassword(Boolean.TRUE.equals(admin.getMustChangePassword()))
                .user(toDTO(admin))
                .build();
    }


    private void writePlatformLoginLog(PlatformAdminUser admin, String ip, boolean success, String reason) {
        try {
            var q = em.createNativeQuery("INSERT INTO user_login_logs (tenant_id, user_id, login_type, ip, user_agent, success, fail_reason, at_time) VALUES (NULL, ?1, 'PLATFORM', ?2, NULL, ?3, ?4, now())");
            q.setParameter(1, admin == null ? null : admin.getId());
            q.setParameter(2, ip);
            q.setParameter(3, success);
            q.setParameter(4, reason);
            q.executeUpdate();
        } catch (Exception ex) {
            log.warn("平台登录日志写入失败: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AdminUserDTO me() {
        Long adminId = TenantContext.getUserId();
        if (adminId == null) {
            throw new BusinessException(ErrorCode.PLATFORM_AUTH_REQUIRED);
        }
        PlatformAdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return toDTO(admin);
    }

    public AdminLoginResponse refresh(String refreshToken) {
        Claims claims = parseRefreshToken(refreshToken);
        Long adminId = Long.valueOf(claims.get(AdminJwtService.CLAIM_ADMIN_ID).toString());
        PlatformAdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!"active".equalsIgnoreCase(admin.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被停用");
        }
        String access = adminJwtService.generateAccessToken(admin.getId(), admin.getUsername());
        String newRefresh = adminJwtService.generateRefreshToken(admin.getId(), admin.getUsername());
        return AdminLoginResponse.builder()
                .accessToken(access)
                .refreshToken(newRefresh)
                .expiresIn(adminJwtService.getAccessTokenTtl() / 1000)
                .mustChangePassword(Boolean.TRUE.equals(admin.getMustChangePassword()))
                .user(toDTO(admin))
                .build();
    }

    @Transactional
    public void changePassword(AdminChangePasswordRequest request) {
        Long adminId = TenantContext.getUserId();
        if (adminId == null) {
            throw new BusinessException(ErrorCode.PLATFORM_AUTH_REQUIRED);
        }
        PlatformAdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!passwordEncoder.matches(request.getOldPassword(), admin.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "原密码不正确");
        }
        admin.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        admin.setMustChangePassword(false);
        admin.setPasswordUpdatedAt(OffsetDateTime.now());
        admin.setUpdatedAt(OffsetDateTime.now());
        adminUserRepository.save(admin);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            Claims claims = adminJwtService.parse(refreshToken);
            long ttlMillis = Math.max(claims.getExpiration().getTime() - System.currentTimeMillis(), 1000L);
            RBucket<String> bucket = redissonClient.getBucket(BLACKLIST_PREFIX + refreshToken);
            bucket.set("1", ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            log.warn("平台后台登出黑名单写入失败: {}", ex.getMessage());
        }
    }

    private Claims parseRefreshToken(String refreshToken) {
        Claims claims;
        try {
            claims = adminJwtService.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "refreshToken 非法");
        }
        if (!adminJwtService.isRefreshToken(claims)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "非 refresh 类型令牌");
        }
        RBucket<String> bucket = redissonClient.getBucket(BLACKLIST_PREFIX + refreshToken);
        if (bucket.isExists()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "该令牌已失效");
        }
        return claims;
    }

    private void incrementFailCount(PlatformAdminUser admin) {
        OffsetDateTime now = OffsetDateTime.now();
        int updated = adminUserRepository.incrementLoginFailCount(
                admin.getId(), MAX_FAIL_COUNT, now.plusMinutes(LOCK_MINUTES), now);
        if (updated > 0) {
            adminUserRepository.findById(admin.getId()).ifPresent(refreshed -> {
                Integer failCount = refreshed.getLoginFailCount();
                if (failCount != null && failCount >= MAX_FAIL_COUNT) {
                    log.warn("Platform admin {} failed login {} times, locked until {}", refreshed.getUsername(), failCount, refreshed.getLockedUntil());
                }
            });
        }
    }

    private AdminUserDTO toDTO(PlatformAdminUser admin) {
        return AdminUserDTO.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .name(admin.getName())
                .mustChangePassword(Boolean.TRUE.equals(admin.getMustChangePassword()))
                .build();
    }
}

