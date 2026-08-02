package com.dms.openapi;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对外接口鉴权过滤器（v3.8.3）。
 *
 * <p>仅拦截 /open/api/**，采用 HMAC-SHA256 签名鉴权：
 * <pre>
 *   signString = HTTP_METHOD + "\n" + PATH + "\n" + X-Timestamp + "\n" + X-Nonce + "\n" + sha256Hex(body)
 *   signature  = HMAC-SHA256(appSecret, signString) 转小写 hex
 * </pre>
 * 请求头：X-App-Key、X-Timestamp（毫秒）、X-Nonce（随机串）、X-Signature。
 * 时间戳允许 ±5 分钟偏差；校验通过后将租户/应用信息写入 TenantContext。
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class OpenApiAuthFilter extends OncePerRequestFilter {

    private static final long SKEW_MS = 5 * 60 * 1000L;
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MATCHER.match("/open/api/**", request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        CachedBodyRequest wrapped = new CachedBodyRequest(request);
        String appKey = wrapped.getHeader("X-App-Key");
        String ts = wrapped.getHeader("X-Timestamp");
        String nonce = wrapped.getHeader("X-Nonce");
        String sig = wrapped.getHeader("X-Signature");

        if (isBlank(appKey) || isBlank(ts) || isBlank(nonce) || isBlank(sig)) {
            reject(response, 401, "缺少鉴权头(X-App-Key/X-Timestamp/X-Nonce/X-Signature)");
            return;
        }
        long timestamp;
        try { timestamp = Long.parseLong(ts); } catch (Exception e) { reject(response, 401, "X-Timestamp 非法"); return; }
        long now = Instant.now().toEpochMilli();
        if (Math.abs(now - timestamp) > SKEW_MS) { reject(response, 401, "请求时间戳超出允许偏差(±5分钟)"); return; }

        Map<String, Object> app;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, tenant_id, app_secret, app_name, system, status, allowed_ips FROM open_app WHERE app_key = ?",
                    appKey);
            if (rows.isEmpty()) { reject(response, 401, "无效的 appKey"); return; }
            app = rows.get(0);
        } catch (Exception e) {
            log.warn("open_app query failed: {}", e.getMessage());
            reject(response, 500, "鉴权服务异常"); return;
        }
        if (!"active".equals(String.valueOf(app.get("status")))) { reject(response, 403, "应用已被禁用"); return; }

        Object allowed = app.get("allowed_ips");
        if (allowed != null && !String.valueOf(allowed).isBlank()) {
            String ip = clientIp(wrapped);
            boolean ok = false;
            for (String a : String.valueOf(allowed).split(",")) {
                if (a.trim().equals(ip)) { ok = true; break; }
            }
            if (!ok) { reject(response, 403, "来源 IP 不在白名单: " + ip); return; }
        }

        String secret = String.valueOf(app.get("app_secret"));
        String bodyHash = sha256Hex(wrapped.getCachedBody());
        String signString = wrapped.getMethod().toUpperCase() + "\n" + wrapped.getRequestURI() + "\n"
                + ts + "\n" + nonce + "\n" + bodyHash;
        String expected = hmacSha256Hex(secret, signString);
        if (!constantTimeEq(expected, sig.toLowerCase())) { reject(response, 401, "签名校验失败"); return; }

        try {
            UUID tenantId = app.get("tenant_id") == null ? null : UUID.fromString(String.valueOf(app.get("tenant_id")));
            TenantContext.setTenantId(tenantId);
            TenantContext.set("appKey", appKey);
            TenantContext.set("appName", app.get("app_name"));
            TenantContext.set("appSystem", app.get("system"));
            wrapped.setAttribute("openAppId", app.get("id"));
            chain.doFilter(wrapped, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void reject(HttpServletResponse response, int httpStatus, String msg) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<?> body = ApiResponse.fail(40100 + httpStatus, msg);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String clientIp(HttpServletRequest r) {
        String ip = r.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) return ip.split(",")[0].trim();
        ip = r.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip.trim();
        return r.getRemoteAddr();
    }

    static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data == null ? new byte[0] : data));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static boolean constantTimeEq(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0; for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i); return r == 0;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
