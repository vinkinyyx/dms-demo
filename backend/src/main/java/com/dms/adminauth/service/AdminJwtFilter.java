/*
 * 平台后台 JWT 过滤器：解析后台 token，设置 PLATFORM 来源的上下文，不设置业务租户。
 */
package com.dms.adminauth.service;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminJwtFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String ADMIN_API_PREFIX = "/api/admin/";

    private final AdminJwtService adminJwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        boolean adminPath = requestUri.startsWith(ADMIN_API_PREFIX);

        String header = request.getHeader(HEADER);
        if (adminPath && StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                Claims claims = adminJwtService.parse(token);
                if (!adminJwtService.isAccessToken(claims)) {
                    log.warn("Non-access token requests admin resource: {}", requestUri);
                    writeUnauthorized(response, "登录已过期，请重新登录");
                    return;
                }
                String username = claims.getSubject();
                Long adminId = Long.valueOf(claims.get(AdminJwtService.CLAIM_ADMIN_ID).toString());

                TenantContext.setAuthSource(TenantContext.AUTH_SOURCE_PLATFORM);
                TenantContext.setUserId(adminId);
                TenantContext.setUsername(username);
                // 平台后台不设置业务 tenantId / tenantType

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                log.warn("Admin JWT parse failed: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, "登录已过期，请重新登录");
                return;
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (adminPath) {
                TenantContext.clear();
            }
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":40104,\"message\":\"" + message + "\",\"data\":null,\"requestId\":\"\"}");
    }
}
