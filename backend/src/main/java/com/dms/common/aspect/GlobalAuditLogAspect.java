/*
 * 全局请求审计日志 AOP 切面
 * 自动拦截所有 Controller 请求，记录用户操作信息到数据库
 * 注意：审计日志写入委托给独立的 AuditLogWriter Bean，避免同类自调用绕过 Spring 事务代理
 */
package com.dms.common.aspect;

import com.dms.common.util.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

@Slf4j
@Aspect
@Component
public class GlobalAuditLogAspect {

    private final AuditLogWriter auditLogWriter;

    public GlobalAuditLogAspect(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
    }

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long startTime = System.currentTimeMillis();

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        UUID tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        String username = TenantContext.getUsername();

        Object result;
        int status = 200;
        try {
            result = point.proceed();
        } catch (Exception e) {
            status = 500;
            throw e;
        }

        long durationMs = System.currentTimeMillis() - startTime;

        if (request != null) {
            try {
                auditLogWriter.writeAuditLog(request, status, durationMs, tenantId, userId, username);
            } catch (Exception e) {
                log.warn("写入全局审计日志失败: {}", e.getMessage());
            }
        }

        return result;
    }
}