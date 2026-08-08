/*
 * Spring Security config: CSRF disabled, stateless sessions, permit auth endpoints, docs, health.
 * 业务 token 与平台后台 token 通过两个过滤器分别解析，路径前缀 /api/admin/** 仅接受后台 token。
 */
package com.dms.security;

import com.dms.adminauth.service.AdminJwtFilter;
import com.dms.common.ApiResponse;
import com.dms.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final AdminJwtFilter adminJwtFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(
                                "/auth/**",
                                "/api/auth/**",
                                "/api/admin/auth/**",
                                "/actuator/health",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/open/**",
                                "/api/system-ops/approval-tokens/*/approve"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    ApiResponse.fail(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录")));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    ApiResponse.fail(ErrorCode.FORBIDDEN, "没有权限访问该资源")));
                        })
                )
                .httpBasic(hb -> hb.disable())
                .formLogin(fl -> fl.disable())
                .logout(lo -> lo.disable())
                .addFilterBefore(adminJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
