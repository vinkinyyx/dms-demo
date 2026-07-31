package com.dms.common.aspect;

import com.dms.common.util.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 独立的审计日志写入服务，用于 GlobalAuditLogAspect 调用
 * 必须是独立 Bean 才能让 @Transactional 通过 Spring AOP 代理生效
 */
@Service
public class AuditLogWriter {

    private static final org.slf4j.Logger AUDIT_LOGGER = org.slf4j.LoggerFactory.getLogger("AUDIT_LOGGER");

    private final JdbcTemplate jdbcTemplate;

    public AuditLogWriter(@Qualifier("jdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAuditLog(HttpServletRequest request, int status, long durationMs,
                              UUID tenantId, Long userId, String username) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        String resourceType = path;
        if (path != null && path.length() > 64) {
            resourceType = path.substring(0, 64);
        }

        String detail = String.format(
                "{\"method\":\"%s\",\"path\":\"%s\",\"query\":\"%s\",\"user\":\"%s\",\"status\":%d,\"duration\":%d}",
                method != null ? method : "",
                path != null ? path : "",
                queryString != null ? queryString : "",
                username != null ? username : "",
                status,
                durationMs
        );

        String sql = "INSERT INTO audit_logs (tenant_id, user_id, action, resource_type, after, ip, user_agent, at_time) " +
                "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, now())";
        jdbcTemplate.update(sql,
                tenantId,
                userId,
                method != null ? method : "UNKNOWN",
                resourceType,
                detail,
                clientIp,
                userAgent
        );

        AUDIT_LOGGER.info("请求审计: tenantId={}, userId={}, method={}, path={}, status={}, duration={}ms, ip={}",
                tenantId, userId, method, path, status, durationMs, clientIp);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}