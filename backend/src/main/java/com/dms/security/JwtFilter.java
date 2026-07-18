/*
 * JWT 过滤器，从 Authorization: Bearer 头解析令牌并写入 SecurityContext 与 TenantContext。
 */
package com.dms.security;

import com.dms.common.util.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                Claims claims = jwtUtil.parse(token);
                if (!jwtUtil.isAccessToken(claims)) {
                    log.warn("非 access 类型令牌请求资源: {}", request.getRequestURI());
                } else {
                    String username = claims.getSubject();
                    Object userIdObj = claims.get(JwtUtil.CLAIM_USER_ID);
                    Object tenantObj = claims.get(JwtUtil.CLAIM_TENANT_ID);

                    Long userId = userIdObj == null ? null : Long.valueOf(userIdObj.toString());
                    UUID tenantId = tenantObj == null ? null : UUID.fromString(tenantObj.toString());

                    TenantContext.setUsername(username);
                    TenantContext.setUserId(userId);
                    TenantContext.setTenantId(tenantId);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null,
                                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.warn("JWT 解析失败: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
