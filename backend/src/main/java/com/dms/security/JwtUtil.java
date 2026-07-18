/*
 * JWT 工具类，负责签发与解析 Access Token / Refresh Token，采用 HS256 签名算法。
 */
package com.dms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    @Value("${dms.jwt.secret}")
    private String secret;

    @Getter
    @Value("${dms.jwt.access-token-ttl:3600000}")
    private long accessTokenTtl;

    @Getter
    @Value("${dms.jwt.refresh-token-ttl:604800000}")
    private long refreshTokenTtl;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String username, String tenantId) {
        return build(userId, username, tenantId, TOKEN_TYPE_ACCESS, accessTokenTtl);
    }

    public String generateRefreshToken(Long userId, String username, String tenantId) {
        return build(userId, username, tenantId, TOKEN_TYPE_REFRESH, refreshTokenTtl);
    }

    private String build(Long userId, String username, String tenantId, String type, long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_USERNAME, username);
        claims.put(CLAIM_TENANT_ID, tenantId);
        claims.put(CLAIM_TOKEN_TYPE, type);
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMillis);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(String.valueOf(claims.get(CLAIM_TOKEN_TYPE)));
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(String.valueOf(claims.get(CLAIM_TOKEN_TYPE)));
    }
}
