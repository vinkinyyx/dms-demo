/*
 * 微信登录 Mock 服务：生成扫码 scene、模拟回调、完成账号绑定。
 */
package com.dms.auth.service;

import com.dms.auth.dto.LoginResponse;
import com.dms.auth.dto.WechatCallbackResponse;
import com.dms.auth.dto.WechatQrResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.security.JwtUtil;
import com.dms.tenant.entity.Tenant;
import com.dms.tenant.repository.TenantRepository;
import com.dms.user.entity.User;
import com.dms.user.repository.UserRepository;
import com.dms.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 微信登录 Mock 服务，供本地开发环境模拟扫码登录流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMockService {

    private static final String MOCK_OPENID_PREFIX = "MOCK_OPENID_";
    private static final String SCENE_PREFIX = "dms:wechat:scene:";
    private static final String BIND_TOKEN_PREFIX = "dms:wechat:bind:";
    private static final long SCENE_TTL_SECONDS = 300L;
    private static final long BIND_TOKEN_TTL_SECONDS = 600L;

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedissonClient redissonClient;

    @Value("${dms.wechat.qr-base-url:http://localhost:9090/mocks/wechat/qr}")
    private String qrBaseUrl;

    /**
     * 生成扫码 scene 与二维码 URL。
     */
    public WechatQrResponse generateQrCode() {
        String scene = UUID.randomUUID().toString().replace("-", "");
        RBucket<String> bucket = redissonClient.getBucket(SCENE_PREFIX + scene);
        bucket.set("pending", SCENE_TTL_SECONDS, TimeUnit.SECONDS);
        return WechatQrResponse.builder()
                .scene(scene)
                .qrUrl(qrBaseUrl + "?scene=" + scene)
                .build();
    }

    /**
     * 模拟微信回调：若 code 以 MOCK_OPENID_ 开头，则视为返回该 openid。
     * - 若数据库中已存在绑定该 openid 的用户 -> 直接登录；
     * - 否则返回 needBind + 短期 bindToken。
     */
    @Transactional
    public WechatCallbackResponse handleCallback(String code, String state) {
        if (code == null || !code.startsWith(MOCK_OPENID_PREFIX)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "非法的模拟 code");
        }
        String openid = code;
        Optional<User> userOpt = userRepository.findByWechatOpenid(openid);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            LoginResponse login = LoginResponse.builder()
                    .accessToken(jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getTenantId().toString()))
                    .refreshToken(jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getTenantId().toString()))
                    .accessTokenExpiresIn(jwtUtil.getAccessTokenTtl() / 1000)
                    .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                    .user(userService.toDTO(user))
                    .build();
            return WechatCallbackResponse.builder().needBind(false).login(login).build();
        }
        // 未绑定：生成一次性 bindToken 存入 Redis，写入 openid
        String bindToken = UUID.randomUUID().toString().replace("-", "");
        RBucket<String> bucket = redissonClient.getBucket(BIND_TOKEN_PREFIX + bindToken);
        bucket.set(openid, BIND_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
        return WechatCallbackResponse.builder()
                .needBind(true)
                .bindToken(bindToken)
                .build();
    }

    /**
     * 使用账号密码 + bindToken 完成微信绑定，返回登录信息。
     */
    @Transactional
    public LoginResponse bind(String bindToken, String username, String password, String tenantCode) {
        RBucket<String> bucket = redissonClient.getBucket(BIND_TOKEN_PREFIX + bindToken);
        String openid = bucket.get();
        if (openid == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "bindToken 已过期，请重新扫码");
        }
        User user = locateUser(tenantCode, username);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        userService.bindWechat(user.getId(), openid, null);
        bucket.delete();
        // 重新加载最新用户信息
        User latest = userService.loadUser(user.getId());
        return LoginResponse.builder()
                .accessToken(jwtUtil.generateAccessToken(latest.getId(), latest.getUsername(), latest.getTenantId().toString()))
                .refreshToken(jwtUtil.generateRefreshToken(latest.getId(), latest.getUsername(), latest.getTenantId().toString()))
                .accessTokenExpiresIn(jwtUtil.getAccessTokenTtl() / 1000)
                .mustChangePassword(Boolean.TRUE.equals(latest.getMustChangePassword()))
                .user(userService.toDTO(latest))
                .build();
    }

    /**
     * 解绑当前登录用户的微信。
     */
    @Transactional
    public void unbind(Long userId) {
        userService.unbindWechat(userId);
    }

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
        return userRepository.findAll().stream()
                .filter(u -> username.equals(u.getUsername()))
                .findFirst()
                .orElse(null);
    }
}
