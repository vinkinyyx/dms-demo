/*
 * 租户拦截器，从 JWT Claims 已注入的 TenantContext 中兜底，或从超管 header X-Tenant-Id 读取。
 */
package com.dms.security;

import com.dms.common.util.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (TenantContext.getTenantId() == null) {
            String headerVal = request.getHeader(HEADER_TENANT_ID);
            if (StringUtils.hasText(headerVal)) {
                try {
                    TenantContext.setTenantId(UUID.fromString(headerVal));
                } catch (IllegalArgumentException ex) {
                    log.warn("X-Tenant-Id 非法 UUID: {}", headerVal);
                }
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        // TenantContext 由 JwtFilter 的 finally 清理，此处不重复处理
    }
}
