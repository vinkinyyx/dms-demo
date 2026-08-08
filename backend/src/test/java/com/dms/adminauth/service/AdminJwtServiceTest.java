package com.dms.adminauth.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminJwtServiceTest {

    private AdminJwtService jwt;

    @BeforeEach
    void setUp() {
        jwt = new AdminJwtService();
        ReflectionTestUtils.setField(jwt, "secret",
                "admin-unit-test-secret-key-abcdefghijklmnopqrstuvwxyz-1234567890");
        ReflectionTestUtils.setField(jwt, "accessTokenTtl", 60_000L);
        ReflectionTestUtils.setField(jwt, "refreshTokenTtl", 300_000L);
        jwt.init();
    }

    @Test
    @DisplayName("admin access token is parseable and carries platform auth source")
    void should_issueAndParseAdminAccessToken() {
        String token = jwt.generateAccessToken(7L, "admin");
        Claims claims = jwt.parse(token);
        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get(AdminJwtService.CLAIM_ADMIN_ID)).isEqualTo(7);
        assertThat(claims.get(AdminJwtService.CLAIM_AUTH_SOURCE)).isEqualTo(AdminJwtService.AUTH_SOURCE_ADMIN);
        assertThat(jwt.isAccessToken(claims)).isTrue();
    }

    @Test
    @DisplayName("token without platform auth source is rejected")
    void should_rejectTokenWithoutAdminAudience() {
        SecretKeySpec key = new SecretKeySpec(
                "admin-unit-test-secret-key-abcdefghijklmnopqrstuvwxyz-1234567890".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        String businessToken = io.jsonwebtoken.Jwts.builder()
                .claim("userId", 1L).subject("u").signWith(key).compact();
        assertThatThrownBy(() -> jwt.parse(businessToken)).isInstanceOf(Exception.class);
    }
}
