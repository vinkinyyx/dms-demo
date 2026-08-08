/*
 * HTTP 接口日志过滤器：记录 /api/** 元数据到 api_http_logs，原始报文脱敏后存 MinIO。
 * 二进制/上传下载不记录完整 body。顺序晚于 JwtFilter，可读取 TenantContext。
 */
package com.dms.platform.apilog.filter;

import com.dms.apilog.CachedBodyHttpServletResponse;
import com.dms.common.util.TenantContext;
import com.dms.operationlog.filter.ContentCachingFilter;
import com.dms.platform.apilog.entity.ApiHttpLog;
import com.dms.platform.apilog.service.ApiHttpLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@Order(70)
@RequiredArgsConstructor
public class ApiHttpLogFilter extends OncePerRequestFilter {

    private static final int MAX_BODY = 32 * 1024;
    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> EXCLUDE = List.of(
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api/admin/logs/api/*/request-file",
            "/api/admin/logs/api/*/response-file"
    );

    private final ApiHttpLogService apiHttpLogService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) return true;
        for (String p : EXCLUDE) if (MATCHER.match(p, uri)) return true;
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CachedBodyHttpServletResponse wrapped = new CachedBodyHttpServletResponse(response);
        long t0 = System.currentTimeMillis();
        OffsetDateTime started = OffsetDateTime.now();
        Throwable error = null;
        try {
            filterChain.doFilter(request, wrapped);
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            byte[] body = wrapped.getContentAsBytes();
            if (body.length > 0) {
                try {
                    response.setContentLength(body.length);
                    response.getOutputStream().write(body);
                    response.getOutputStream().flush();
                } catch (Exception e) {
                    log.warn("api http log flush response failed: {}", e.getMessage());
                }
            }
            try {
                record(request, wrapped, t0, started, error);
            } catch (Exception e) {
                log.warn("write api http log failed: {}", e.getMessage());
            }
        }
    }

    private void record(HttpServletRequest request, CachedBodyHttpServletResponse response,
                        long t0, OffsetDateTime started, Throwable error) {
        long spent = System.currentTimeMillis() - t0;
        String requestId = MDC.get("requestId");
        ApiHttpLog entry = ApiHttpLog.builder()
                .requestId(requestId)
                .traceId(request.getHeader("X-Trace-Id"))
                .tenantId(TenantContext.getTenantId())
                .tenantType(TenantContext.getTenantType())
                .ownerManufacturerId(TenantContext.getOwnerManufacturerId())
                .userId(TenantContext.getUserId())
                .username(TenantContext.getUsername())
                .authSource(TenantContext.getAuthSource())
                .httpMethod(request.getMethod())
                .path(truncate(request.getRequestURI(), 255))
                .queryString(request.getQueryString())
                .statusCode(response.getStatus())
                .spentMs(spent)
                .clientIp(clientIp(request))
                .userAgent(truncate(request.getHeader("User-Agent"), 512))
                .startedAt(started)
                .finishedAt(OffsetDateTime.now())
                .build();

        String respBody = response.getContentAsString();
        entry.setBizCode(extractBizCode(respBody, response));
        boolean httpOk = response.getStatus() >= 200 && response.getStatus() < 300;
        entry.setSuccess(httpOk && (entry.getBizCode() == null || entry.getBizCode() == 0));
        if (error != null) {
            entry.setErrorMessage(truncate(error.getMessage(), 2000));
        }

        String reqBody = shouldRecordBody(request.getContentType(), request.getMethod())
                ? truncate(ContentCachingFilter.getBody(request), MAX_BODY) : null;
        String respBodyToStore = shouldRecordBody(response.getContentType(), null)
                ? truncate(respBody, MAX_BODY) : null;

        apiHttpLogService.recordAsync(entry, reqBody, respBodyToStore);
    }

    private boolean shouldRecordBody(String contentType, String method) {
        if (contentType == null) return false;
        String ct = contentType.toLowerCase();
        if (ct.contains("multipart")) return false;
        if (ct.contains("octet-stream")) return false;
        return ct.contains("json") || ct.contains("xml") || ct.contains("text") || ct.contains("x-www-form-urlencoded");
    }

    private Integer extractBizCode(String body, HttpServletResponse response) {
        String ct = response.getContentType();
        if (ct == null || !ct.contains(MediaType.APPLICATION_JSON_VALUE)) return null;
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode code = node.get("code");
            return code == null || code.isNull() ? null : code.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) return ip.split(",")[0].trim();
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) return ip.trim();
        return request.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}