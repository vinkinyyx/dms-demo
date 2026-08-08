/*
 * 平台后台 JWT 服务：签发与解析后台独立 token，与业务 token 通过 aud=admin 区分，不可互换。
 */
package com.dms.adminauth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
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
public class AdminJwtService {

    public static final String CLAIM_ADMIN_ID = "adminId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String CLAIM_AUTH_SOURCE = "authSource";
    public static final String AUTH_SOURCE_ADMIN = "PLATFORM";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    @Value("${dms.jwt.secret}")
    private String secret;

    @Getter
    @Value("${dms.jwt.access-token-ttl:28800000}")
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

    public String generateAccessToken(Long adminId, String username) {
        return build(adminId, username, TOKEN_TYPE_ACCESS, accessTokenTtl);
    }

    public String generateRefreshToken(Long adminId, String username) {
        return build(adminId, username, TOKEN_TYPE_REFRESH, refreshTokenTtl);
    }

    private String build(Long adminId, String username, String type, long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_ADMIN_ID, adminId);
        claims.put(CLAIM_USERNAME, username);
        claims.put(CLAIM_TOKEN_TYPE, type);
        claims.put(CLAIM_AUTH_SOURCE, AUTH_SOURCE_ADMIN);
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
        Claims payload = jws.getPayload();
        if (!AUTH_SOURCE_ADMIN.equals(String.valueOf(payload.get(CLAIM_AUTH_SOURCE)))) {
            throw new JwtException("非法的后台令牌");
        }
        return payload;
    }

    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(String.valueOf(claims.get(CLAIM_TOKEN_TYPE)));
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(String.valueOf(claims.get(CLAIM_TOKEN_TYPE)));
    }
}

