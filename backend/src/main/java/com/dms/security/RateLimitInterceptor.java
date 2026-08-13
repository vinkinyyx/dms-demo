package com.dms.security;

import com.dms.common.ApiResponse;
import com.dms.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API rate limiting based on Redisson RRateLimiter.
 * Applies per-IP limits to sensitive endpoints (login, MFA verify, forgot password).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String ADMIN_LOGIN_PATH = "/api/admin/auth/login";
    private static final String MFA_VERIFY_PATH = "/api/auth/mfa/verify";
    private static final String FORGOT_PATH = "/api/auth/forgot-password";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Value("${dms.ratelimit.login-per-minute:60}")
    private long loginPerMinute;

    @Value("${dms.ratelimit.forgot-per-minute:10}")
    private long forgotPerMinute;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String ip = resolveIp(request);

        if (LOGIN_PATH.equals(path) || ADMIN_LOGIN_PATH.equals(path)) {
            return acquire(response, "dms:rl:v2:login:" + ip, loginPerMinute, 60, "登录过于频繁，请稍后再试");
        }
        if (MFA_VERIFY_PATH.equals(path)) {
            return acquire(response, "dms:rl:v2:mfa:" + ip, loginPerMinute, 60, "验证过于频繁，请稍后再试");
        }
        if (FORGOT_PATH.equals(path)) {
            return acquire(response, "dms:rl:v2:forgot:" + ip, forgotPerMinute, 60, "请求过于频繁，请稍后再试");
        }
        return true;
    }

    private boolean acquire(HttpServletResponse response, String key, long permits, long intervalSeconds, String message) throws Exception {
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.trySetRate(RateType.OVERALL, permits, intervalSeconds, RateIntervalUnit.SECONDS);
        if (limiter.tryAcquire(1)) {
            return true;
        }
        log.warn("Rate limit triggered: key={}", key);
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(ErrorCode.RATE_LIMITED, message)));
        return false;
    }

    private String resolveIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(header)) {
            return header.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
