package com.dms.operationlog.interceptor;

import com.dms.common.util.TenantContext;
import com.dms.operationlog.entity.OpLogEntry;
import com.dms.operationlog.filter.ContentCachingFilter;
import com.dms.operationlog.service.OperationLogRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * HTTP 请求日志拦截器（v3.6.2 R1）。
 *
 * - preHandle：生成 requestId + MDC，写 HTTP-IN
 * - afterCompletion：写 HTTP-OUT
 * - 排除 /actuator/** 等监控路径
 */
@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String ATTR_START = "__oplog_start";
    private static final String ATTR_REQ_ID = "__oplog_request_id";

    @Autowired
    private OperationLogRecordService recordService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long start = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(ATTR_START, start);
        request.setAttribute(ATTR_REQ_ID, requestId);
        MDC.put("requestId", requestId);

        OpLogEntry entry = new OpLogEntry();
        entry.setRequestId(requestId);
        entry.setLayer("HTTP-IN");
        entry.setHttpMethod(request.getMethod());
        entry.setPath(request.getRequestURI());
        entry.setIp(getClientIp(request));
        entry.setUserAgent(request.getHeader("User-Agent"));
        entry.setUserId(TenantContext.getUserId());
        entry.setUsername(TenantContext.getUsername());
        entry.setTenantId(TenantContext.getTenantId());
        entry.setCreatedAt(OffsetDateTime.now());
        String body = ContentCachingFilter.getBody(request);
        if (body != null) {
            entry.setRequestBody(body);
        }
        try {
            recordService.record(entry);
        } catch (Exception e) {
            log.warn("HTTP-IN log failed: {}", e.getMessage());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object startAttr = request.getAttribute(ATTR_START);
        long spentMs = startAttr instanceof Long ? System.currentTimeMillis() - (Long) startAttr : 0;
        String requestId = (String) request.getAttribute(ATTR_REQ_ID);

        OpLogEntry entry = new OpLogEntry();
        entry.setRequestId(requestId);
        entry.setLayer("HTTP-OUT");
        entry.setHttpMethod(request.getMethod());
        entry.setPath(request.getRequestURI());
        entry.setStatus(response.getStatus());
        entry.setSpentMs(spentMs);
        entry.setIp(getClientIp(request));
        entry.setUserAgent(request.getHeader("User-Agent"));
        entry.setUserId(TenantContext.getUserId());
        entry.setUsername(TenantContext.getUsername());
        entry.setTenantId(TenantContext.getTenantId());
        entry.setCreatedAt(OffsetDateTime.now());
        String body = extractRequestBody(request);
        if (body != null) {
            entry.setRequestBody(body);
        }
        try {
            recordService.record(entry);
        } catch (Exception e) {
            log.warn("HTTP-OUT log failed: {}", e.getMessage());
        }
        MDC.remove("requestId");
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

    private String extractRequestBody(HttpServletRequest request) {
        return ContentCachingFilter.getBody(request);
    }
}
