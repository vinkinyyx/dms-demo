/*
 * JWT filter: parses Authorization Bearer token and populates SecurityContext/TenantContext.
 */
package com.dms.security;

import com.dms.common.ApiResponse;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.rbac.service.PermissionQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final PermissionQueryService permissionQueryService;
    private final ObjectMapper objectMapper;

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
                    log.warn("Non-access token requests resource: {}", request.getRequestURI());
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
                            new UsernamePasswordAuthenticationToken(username, null, loadAuthorities(userId));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.warn("JWT parse failed: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, "登录已过期，请重新登录");
                return;
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(ErrorCode.UNAUTHORIZED, message)));
    }

    private List<SimpleGrantedAuthority> loadAuthorities(Long userId) {
        if (userId == null) {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        }
        Set<String> permissions = permissionQueryService.loadPermissionsForUser(userId);
        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        return authorities;
    }
}