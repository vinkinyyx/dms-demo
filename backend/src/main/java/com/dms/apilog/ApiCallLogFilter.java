package com.dms.apilog;

import com.dms.common.util.TenantContext;
import com.dms.operationlog.filter.ContentCachingFilter;
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

/**
 * 入站接口调用日志过滤器（v3.8.2）。
 *
 * <p>记录所有外部调用 DMS 的 /api/** 请求（方法、路径、状态码、耗时、请求/响应体摘要、调用方）。
 * 与业务操作日志 op_log 解耦，独立写入 api_call_log。
 *
 * <p>顺序：在 JwtFilter 之后执行（通过 order 值控制），以便读取 TenantContext 中的用户信息。
 */
@Slf4j
@Component
@Order(60)
@RequiredArgsConstructor
public class ApiCallLogFilter extends OncePerRequestFilter {

    private static final int MAX_BODY = 32 * 1024;
    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> EXCLUDE = List.of(
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api/admin/api-call-logs/**"
    );

    private final ApiCallLogService logService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/open/api/")) return false;
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
        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            try {
                byte[] body = wrapped.getContentAsBytes();
                if (body.length > 0) {
                    response.setContentLength(body.length);
                    response.getOutputStream().write(body);
                    response.getOutputStream().flush();
                }
            } catch (Exception e) {
                log.warn("api call log flush response failed: {}", e.getMessage());
            }
            try {
                record(request, wrapped, t0, started, null);
            } catch (Exception e) {
                log.warn("api call log write failed: {}", e.getMessage());
            }
        }
    }

    private void record(HttpServletRequest request, CachedBodyHttpServletResponse response,
                        long t0, OffsetDateTime started, String error) {
        ApiCallLog entry = new ApiCallLog();
        entry.setDirection("IN");
        entry.setTenantId(TenantContext.getTenantId());
        entry.setUserId(TenantContext.getUserId());
        entry.setUsername(TenantContext.getUsername());
        Object appKey = TenantContext.get("appKey");
        Object appSystem = TenantContext.get("appSystem");
        if (appKey != null) entry.setAppKey(String.valueOf(appKey));
        if (appSystem != null) entry.setSystem(String.valueOf(appSystem));
        entry.setRequestId(MDC.get("requestId"));
        entry.setTraceId(request.getHeader("X-Trace-Id"));
        entry.setAppKey(request.getHeader("X-App-Key"));
        entry.setHttpMethod(request.getMethod());
        entry.setPath(request.getRequestURI());
        entry.setUrl(request.getRequestURL().toString());
        entry.setClientIp(clientIp(request));
        entry.setStatusCode(response.getStatus());
        entry.setSpentMs(System.currentTimeMillis() - t0);
        entry.setStartedAt(started);
        entry.setFinishedAt(OffsetDateTime.now());

        String reqBody = ContentCachingFilter.getBody(request);
        entry.setRequestBody(sanitize(truncate(reqBody)));
        // 导出类接口返回的是 xlsx 二进制，不能当文本入库（含 0x00 会被 PostgreSQL 拒绝）
        String respBody = isTextResponse(response) ? response.getContentAsString() : null;
        entry.setResponseBody(respBody != null
                ? sanitize(truncate(respBody))
                : "<binary " + response.getContentAsBytes().length + " bytes, content-type=" + response.getContentType() + ">");
        entry.setBizCode(extractBizCode(respBody, response));
        // 传输接口专用:按 URI 推断 biz_action，并从响应体中提取订单号作为 biz_key。
        deriveBizTags(entry, request, respBody);
        boolean httpOk = response.getStatus() >= 200 && response.getStatus() < 300;
        entry.setSuccess(httpOk && (entry.getBizCode() == null || entry.getBizCode() == 0));
        if (error != null) entry.setErrorMsg(truncate(error));

        logService.recordInbound(entry);
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

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > MAX_BODY ? s.substring(0, MAX_BODY) + "...(truncated)" : s;
    }

    /** 仅对文本类 Content-Type 记录响应体，避免 Excel/PDF 等二进制写入 text 列。 */
    private boolean isTextResponse(HttpServletResponse response) {
        String ct = response.getContentType();
        if (ct == null) return true;
        String lower = ct.toLowerCase();
        return lower.contains("json") || lower.contains("xml") || lower.startsWith("text/")
                || lower.contains("javascript") || lower.contains("x-www-form-urlencoded");
    }


    /**
     * 传输接口专用:根据 URI 和响应体推断 biz_action / biz_key。
     *
     * <p>规则:
     * <ul>
     *   <li>POST /api/orders/transfer         -> biz_action=order.transfer.sales，biz_key=SO-*</li>
     *   <li>POST /api/purchase-orders/transfer-> biz_action=order.transfer.purchase，biz_key=PO-*</li>
     *   <li>GET  /api/inventory              -> biz_action=inventory.query，biz_key=warehouseId-productId 形式</li>
     * </ul>
     */
    private void deriveBizTags(ApiCallLog entry, HttpServletRequest request, String respBody) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) && "/api/orders/transfer".equals(uri)) {
            entry.setBizAction("order.transfer.sales");
            String code = extractBizKeyFromResponse(respBody, "code");
            if (code != null) entry.setBizKey(code);
        } else if ("POST".equalsIgnoreCase(method) && "/api/purchase-orders/transfer".equals(uri)) {
            entry.setBizAction("order.transfer.purchase");
            String code = extractBizKeyFromResponse(respBody, "code");
            if (code != null) entry.setBizKey(code);
        } else if ("GET".equalsIgnoreCase(method) && uri.startsWith("/api/inventory")) {
            entry.setBizAction("inventory.query");
            // 优先使用 query 参数合成 biz_key（warehouseId-productId），避免依赖响应体结构。
            String w = request.getParameter("warehouseId");
            String pid = request.getParameter("productId");
            String key = null;
            if (w != null || pid != null) {
                key = (w == null ? "*" : w) + "-" + (pid == null ? "*" : pid);
            }
            if (key == null) key = extractBizKeyFromResponse(respBody, "id");
            if (key != null) entry.setBizKey(key);
        }
    }

    /**
     * 从响应体中提取 data.{field} 作作为业务单号;提取失败时退而取 message 前 32 字。
     */
    private String extractBizKeyFromResponse(String body, String field) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null) return null;
            JsonNode data = root.get("data");
            if (data != null && !data.isNull() && data.isObject()) {
                JsonNode v = data.get(field);
                if (v != null && !v.isNull() && (v.isTextual() || v.isNumber())) {
                    return v.asText();
                }
            }
            JsonNode msg = root.get("message");
            if (msg != null && msg.isTextual()) {
                String m = msg.asText();
                if (m.length() > 32) m = m.substring(0, 32);
                return m;
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    /** PostgreSQL 的 text 列不允许 0x00，否则整条日志写入失败。 */
    private String sanitize(String s) {
        if (s == null) return null;
        return s.indexOf('\u0000') >= 0 ? s.replace("\u0000", "") : s;
    }
}
