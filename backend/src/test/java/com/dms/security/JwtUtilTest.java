/*
 * 测试目标：验证 JwtUtil 令牌签发、解析、类型判断、过期与非法情形。
 * 覆盖用户故事：US-2.1（登录返回 JWT）、US-2.5（Token 校验与刷新）。
 */
package com.dms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtil 单元测试，直接实例化并注入配置字段（不依赖 Spring 容器）。
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "unit-test-secret-key-abcdefghijklmnopqrstuvwxyz-1234567890");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenTtl", 60_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenTtl", 300_000L);
        jwtUtil.init();
    }

    @Test
    @DisplayName("正常流程：签发 access token 并解析出 claims")
    void should_generateAndParseAccessToken_when_validClaims() {
        String token = jwtUtil.generateAccessToken(101L, "alice",
                "11111111-1111-1111-1111-111111111111");
        Claims claims = jwtUtil.parse(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get(JwtUtil.CLAIM_USER_ID).toString()).isEqualTo("101");
        assertThat(claims.get(JwtUtil.CLAIM_USERNAME)).isEqualTo("alice");
        assertThat(claims.get(JwtUtil.CLAIM_TENANT_ID))
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(jwtUtil.isAccessToken(claims)).isTrue();
        assertThat(jwtUtil.isRefreshToken(claims)).isFalse();
    }

    @Test
    @DisplayName("正常流程：签发 refresh token，类型识别正确")
    void should_recognizeRefreshType_when_generateRefreshToken() {
        String token = jwtUtil.generateRefreshToken(2L, "bob", "tid");
        Claims claims = jwtUtil.parse(token);

        assertThat(jwtUtil.isRefreshToken(claims)).isTrue();
        assertThat(jwtUtil.isAccessToken(claims)).isFalse();
    }

    @Test
    @DisplayName("异常分支：非法 token 抛出 JwtException")
    void should_throwJwtException_when_tokenIsMalformed() {
        assertThatThrownBy(() -> jwtUtil.parse("not-a-valid-token"))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("异常分支：过期 token 抛出解析异常")
    void should_throwException_when_tokenExpired() throws InterruptedException {
        // 将 accessTokenTtl 设置为 1 毫秒，签发后立即过期
        ReflectionTestUtils.setField(jwtUtil, "accessTokenTtl", 1L);
        String token = jwtUtil.generateAccessToken(1L, "u", "t");
        Thread.sleep(50);

        assertThatThrownBy(() -> jwtUtil.parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("边界：不同密钥签发的 token 无法互相解析")
    void should_failToParse_when_signedByDifferentKey() {
        String token = jwtUtil.generateAccessToken(1L, "u", "t");

        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret",
                "another-secret-key-with-enough-length-1234567890abcd");
        ReflectionTestUtils.setField(other, "accessTokenTtl", 60_000L);
        ReflectionTestUtils.setField(other, "refreshTokenTtl", 60_000L);
        other.init();

        assertThatThrownBy(() -> other.parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("正常流程：TTL 配置值可通过 getter 获取")
    void should_returnConfiguredTtl_when_queryTtl() {
        assertThat(jwtUtil.getAccessTokenTtl()).isEqualTo(60_000L);
        assertThat(jwtUtil.getRefreshTokenTtl()).isEqualTo(300_000L);
    }
}
