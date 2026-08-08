package com.dms.apilog;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 接口调用日志查询（v3.8.2）。仅 admin 可访问。
 *
 * <p>GET /api/admin/api-call-logs        分页列表（含过滤）
 * <p>GET /api/admin/api-call-logs/{id}   详情（含请求/响应体）
 */
@RestController
@RequestMapping({"/api/api-call-logs", "/api/admin/api-call-logs"})
@RequiredArgsConstructor
public class ApiCallLogController {

    private final EntityManager em;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String system,
            @RequestParam(required = false) String method,
            @RequestParam(name = "status", required = false) Integer statusCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        if (!canViewLogs()) return ApiResponse.fail(40300, "无权限");
        if (size > 200) size = 200;

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        int idx = 1;
        UUID tid = TenantContext.getTenantId();
        if (tid != null) { where.append(" AND tenant_id = ?").append(idx++); params.add(tid); }
        if (direction != null && !direction.isBlank()) { where.append(" AND direction = ?").append(idx++); params.add(direction.toUpperCase()); }
        if (system != null && !system.isBlank()) { where.append(" AND system = ?").append(idx++); params.add(system); }
        if (method != null && !method.isBlank()) { where.append(" AND http_method = ?").append(idx++); params.add(method.toUpperCase()); }
        if (statusCode != null) { where.append(" AND status_code = ?").append(idx++); params.add(statusCode); }
        if (startTime != null && !startTime.isBlank()) { where.append(" AND started_at >= ?").append(idx++); params.add(startTime.replace(" ","T")); }
        if (endTime != null && !endTime.isBlank()) { where.append(" AND started_at <= ?").append(idx++); params.add(endTime.replace(" ","T")); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (path ILIKE ?").append(idx++)
                 .append(" OR url ILIKE ?").append(idx++)
                 .append(" OR username ILIKE ?").append(idx++)
                 .append(" OR app_key ILIKE ?").append(idx++).append(")");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw); params.add(kw); params.add(kw); params.add(kw);
        }

        var cnt = em.createNativeQuery("SELECT COUNT(*) FROM api_call_log" + where);
        for (int i = 0; i < params.size(); i++) cnt.setParameter(i + 1, params.get(i));
        long total = ((Number) cnt.getSingleResult()).longValue();

        int offset = (page - 1) * size;
        String limitParam = "?" + (idx++), offsetParam = "?" + (idx++);
        var q = em.createNativeQuery(
                "SELECT id, direction, system, endpoint, http_method, path, url, status_code, biz_code, success, " +
                "client_ip, username, app_key, spent_ms, started_at " +
                "FROM api_call_log" + where +
                " ORDER BY id DESC LIMIT " + limitParam + " OFFSET " + offsetParam, Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(params.size() + 1, size);
        q.setParameter(params.size() + 2, offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toBrief(t));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total); data.put("page", page); data.put("size", size); data.put("list", list);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        if (!canViewLogs()) return ApiResponse.fail(40300, "无权限");
        var q = em.createNativeQuery("SELECT * FROM api_call_log WHERE id = ?1", Tuple.class);
        q.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        if (rows.isEmpty()) return ApiResponse.fail(40404, "日志不存在");
        return ApiResponse.ok(toFull(rows.get(0)));
    }

    private boolean canViewLogs() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            boolean platformAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ROLE_PLATFORM_ADMIN"::equals);
            boolean hasPermission = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("api_log:view"::equals);
            if (platformAdmin || hasPermission) return true;
        }
        String u = TenantContext.getUsername();
        return u != null && "admin".equalsIgnoreCase(u);
    }

    private Map<String, Object> toBrief(Tuple t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id"));
        m.put("direction", t.get("direction"));
        m.put("system", t.get("system"));
        m.put("endpoint", t.get("endpoint"));
        m.put("httpMethod", t.get("http_method"));
        m.put("path", t.get("path"));
        m.put("statusCode", t.get("status_code"));
        m.put("bizCode", t.get("biz_code"));
        m.put("success", t.get("success"));
        m.put("clientIp", t.get("client_ip"));
        m.put("username", t.get("username"));
        m.put("appKey", t.get("app_key"));
        m.put("spentMs", t.get("spent_ms"));
        m.put("startedAt", String.valueOf(t.get("started_at")));
        return m;
    }

    private Map<String, Object> toFull(Tuple t) {
        Map<String, Object> m = toBrief(t);
        m.put("url", t.get("url"));
        m.put("requestId", t.get("request_id"));
        m.put("traceId", t.get("trace_id"));
        m.put("requestHeaders", t.get("request_headers"));
        m.put("requestBody", t.get("request_body"));
        m.put("responseBody", t.get("response_body"));
        m.put("errorMsg", t.get("error_msg"));
        m.put("finishedAt", String.valueOf(t.get("finished_at")));
        m.put("bizKey", t.get("biz_key"));
        m.put("bizAction", t.get("biz_action"));
        return m;
    }
}
