package com.dms.auth.service;

import com.dms.auth.dto.LoginResponse;
import com.dms.auth.dto.MfaSetupResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TotpUtil;
import com.dms.security.JwtUtil;
import com.dms.user.entity.User;
import com.dms.user.repository.UserRepository;
import com.dms.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MFA (TOTP) service: setup, confirm, disable, and two-step login verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    static final String ATTR_SECRET = "mfaSecret";
    static final String ATTR_ENABLED = "mfaEnabled";

    private static final String MFA_PENDING_PREFIX = "dms:auth:mfa:pending:";
    private static final long MFA_PENDING_TTL_MINUTES = 5;
    private static final String ISSUER = "DMS";

    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final RedissonClient redissonClient;

    public MfaSetupResponse setup(Long userId) {
        User user = userService.loadUser(userId);
        user.ensureAttrs();
        String secret;
        boolean enabled = Boolean.TRUE.equals(user.getAttrs().get(ATTR_ENABLED));
        if (enabled) {
            secret = String.valueOf(user.getAttrs().get(ATTR_SECRET));
        } else {
            secret = TotpUtil.generateSecret();
            user.getAttrs().put(ATTR_SECRET, secret);
            user.setUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);
        }
        return MfaSetupResponse.builder()
                .secret(secret)
                .otpAuthUrl(TotpUtil.otpAuthUrl(ISSUER, user.getUsername(), secret))
                .enabled(enabled)
                .build();
    }

    @Transactional
    public void confirm(Long userId, String code) {
        User user = userService.loadUser(userId);
        user.ensureAttrs();
        String secret = String.valueOf(user.getAttrs().get(ATTR_SECRET));
        if (secret == null || "null".equals(secret) || !TotpUtil.verify(secret, code)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "验证码不正确");
        }
        user.getAttrs().put(ATTR_ENABLED, Boolean.TRUE);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void disable(Long userId, String code) {
        User user = userService.loadUser(userId);
        user.ensureAttrs();
        String secret = String.valueOf(user.getAttrs().get(ATTR_SECRET));
        if (secret == null || "null".equals(secret) || !TotpUtil.verify(secret, code)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "验证码不正确");
        }
        user.getAttrs().remove(ATTR_SECRET);
        user.getAttrs().put(ATTR_ENABLED, Boolean.FALSE);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    public boolean isEnabled(User user) {
        if (user == null || user.getAttrs() == null) {
            return false;
        }
        return Boolean.TRUE.equals(user.getAttrs().get(ATTR_ENABLED));
    }

    public LoginResponse buildPendingMfaResponse(User user) {
        String mfaToken = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> payload = new HashMap<>();
        payload.put("userId", String.valueOf(user.getId()));
        payload.put("username", user.getUsername());
        payload.put("tenantId", user.getTenantId().toString());
        RBucket<Map<String, String>> bucket =
                redissonClient.getBucket(MFA_PENDING_PREFIX + mfaToken);
        bucket.set(payload, MFA_PENDING_TTL_MINUTES, TimeUnit.MINUTES);
        return LoginResponse.builder()
                .mfaRequired(true)
                .mfaToken(mfaToken)
                .accessTokenExpiresIn(MFA_PENDING_TTL_MINUTES * 60L)
                .user(userService.toDTO(user))
                .build();
    }

    public LoginResponse verifyAndLogin(String mfaToken, String code) {
        RBucket<Map<String, String>> bucket =
                redissonClient.getBucket(MFA_PENDING_PREFIX + mfaToken);
        Map<String, String> payload = bucket.get();
        if (payload == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "MFA 会话已过期，请重新登录");
        }
        Long userId = Long.valueOf(payload.get("userId"));
        User user = userService.loadUser(userId);
        user.ensureAttrs();
        String secret = String.valueOf(user.getAttrs().get(ATTR_SECRET));
        if (!isEnabled(user) || !TotpUtil.verify(secret, code)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "验证码不正确或已失效");
        }
        bucket.delete();
        String access = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getTenantId().toString());
        String refresh = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getTenantId().toString());
        return LoginResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .accessTokenExpiresIn(jwtUtil.getAccessTokenTtl() / 1000)
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .mfaRequired(false)
                .user(userService.toDTO(user))
                .build();
    }
}
