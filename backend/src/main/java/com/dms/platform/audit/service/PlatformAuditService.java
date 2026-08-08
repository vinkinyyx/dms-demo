/*
 * 平台后台审计日志服务：所有平台写操作必须记录审计日志。
 */
package com.dms.platform.audit.service;

import com.dms.common.util.TenantContext;
import com.dms.platform.audit.entity.PlatformAuditLog;
import com.dms.platform.audit.repository.PlatformAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAuditService {

    private final PlatformAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String targetType, String targetId,
                    Map<String, Object> before, Map<String, Object> after,
                    Boolean success, String errorMessage) {
        try {
            String ip = null;
            String userAgent = null;
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                var request = attrs.getRequest();
                ip = resolveIp(request);
                userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.length() > 512) {
                    userAgent = userAgent.substring(0, 512);
                }
            }
            PlatformAuditLog entity = PlatformAuditLog.builder()
                    .adminUserId(TenantContext.getUserId())
                    .adminUsername(TenantContext.getUsername())
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .beforeJson(before)
                    .afterJson(after)
                    .ip(ip)
                    .userAgent(userAgent)
                    .success(success)
                    .errorMessage(errorMessage)
                    .build();
            repository.save(entity);
        } catch (Exception ex) {
            log.warn("写入平台审计日志失败 action={}: {}", action, ex.getMessage());
        }
    }

    public void log(String action, String targetType, String targetId,
                    Map<String, Object> after) {
        log(action, targetType, targetId, null, after, true, null);
    }

    private String resolveIp(jakarta.servlet.http.HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank() && !"unknown".equalsIgnoreCase(header)) {
            return header.split(",")[0].trim();
        }
        header = request.getHeader("X-Real-IP");
        if (header != null && !header.isBlank() && !"unknown".equalsIgnoreCase(header)) {
            return header.trim();
        }
        return request.getRemoteAddr();
    }
}
