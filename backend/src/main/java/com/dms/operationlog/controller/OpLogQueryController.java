package com.dms.operationlog.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.PagingUtil;
import com.dms.common.util.TenantContext;
import com.dms.operationlog.sanitize.OpLogSanitizer;
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
 * 业务端操作日志查询（统一日志中心 - 操作日志 Tab）。
 *
 * <p>GET /api/operation-logs/fullchain        分页列表（含筛选）
 * <p>GET /api/operation-logs/fullchain/{id}   详情（请求/响应经脱敏）
 * <p>GET /api/operation-logs/fullchain/by-biz 按业务对象查询历史时间线
 */
@RestController
@RequestMapping("/api/operation-logs/fullchain")
@RequiredArgsConstructor
public class OpLogQueryController {

    private final EntityManager em;
    private final OpLogSanitizer sanitizer;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String layer,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        if (!canViewOpLogs()) return ApiResponse.fail(40300, "无权限");
        if (size > 200) size = 200;

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        int idx = 1;
        UUID tid = TenantContext.getTenantId();
        if (tid != null) { where.append(" AND tenant_id = ?").append(idx++); params.add(tid); }
        if (layer != null && !layer.isBlank()) { where.append(" AND layer = ?").append(idx++); params.add(layer.trim()); }
        if (username != null && !username.isBlank()) { where.append(" AND username ILIKE ?").append(idx++); params.add("%" + username.trim() + "%"); }
        if (path != null && !path.isBlank()) { where.append(" AND path ILIKE ?").append(idx++); params.add("%" + path.trim() + "%"); }
        if (bizType != null && !bizType.isBlank()) { where.append(" AND biz_type = ?").append(idx++); params.add(bizType.trim()); }
        if (bizId != null && !bizId.isBlank()) { where.append(" AND biz_id = ?").append(idx++); params.add(bizId.trim()); }
        if (action != null && !action.isBlank()) { where.append(" AND action = ?").append(idx++); params.add(action.trim().toUpperCase()); }
        if (status != null) { where.append(" AND status = ?").append(idx++); params.add(status); }
        if (startTime != null && !startTime.isBlank()) { where.append(" AND created_at >= ?").append(idx++); params.add(startTime.replace(" ", "T")); }
        if (endTime != null && !endTime.isBlank()) { where.append(" AND created_at <= ?").append(idx++); params.add(endTime.replace(" ", "T")); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (path ILIKE ?").append(idx++)
                 .append(" OR username ILIKE ?").append(idx++)
                 .append(" OR remark ILIKE ?").append(idx++)
                 .append(" OR method ILIKE ?").append(idx++).append(")");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw); params.add(kw); params.add(kw); params.add(kw);
        }

        var cnt = em.createNativeQuery("SELECT COUNT(*) FROM op_log" + where);
        for (int i = 0; i < params.size(); i++) cnt.setParameter(i + 1, params.get(i));
        long total = ((Number) cnt.getSingleResult()).longValue();

        int safePage = PagingUtil.normalizePage(page);
        int safeSize = PagingUtil.normalizeSize(size);
        int offset = (safePage - 1) * safeSize;
        String limitParam = "?" + (idx++), offsetParam = "?" + (idx++);
        var q = em.createNativeQuery(
                "SELECT id, request_id, trace_id, username, layer, method, http_method, path, status, " +
                "spent_ms, ip, biz_type, biz_id, action, remark, created_at " +
                "FROM op_log" + where +
                " ORDER BY id DESC LIMIT " + limitParam + " OFFSET " + offsetParam, Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(params.size() + 1, safeSize);
        q.setParameter(params.size() + 2, offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toBrief(t));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total); data.put("page", safePage); data.put("size", safeSize); data.put("list", list);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        if (!canViewOpLogs()) return ApiResponse.fail(40300, "无权限");
        var q = em.createNativeQuery("SELECT * FROM op_log WHERE id = ?1", Tuple.class);
        q.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        if (rows.isEmpty()) return ApiResponse.fail(40404, "日志不存在");
        Tuple t = rows.get(0);
        Map<String, Object> m = toBrief(t);
        m.put("requestId", t.get("request_id"));
        m.put("traceId", t.get("trace_id"));
        m.put("userId", t.get("user_id"));
        m.put("userAgent", t.get("user_agent"));
        m.put("requestBody", sanitizer.sanitize(str(t.get("request_body"))));
        m.put("response", sanitizer.sanitize(str(t.get("response"))));
        m.put("stack", str(t.get("stack")));
        m.put("method", str(t.get("method")));
        return ApiResponse.ok(m);
    }

    @GetMapping("/by-biz")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> byBiz(
            @RequestParam String bizType,
            @RequestParam String bizId) {
        if (!canViewOpLogs()) return ApiResponse.fail(40300, "无权限");
        StringBuilder sql = new StringBuilder(
                "SELECT id, username, layer, http_method, path, status, spent_ms, ip, action, remark, created_at " +
                "FROM op_log WHERE biz_type = ?1 AND biz_id = ?2");
        List<Object> params = new ArrayList<>();
        params.add(bizType.trim()); params.add(bizId.trim());
        UUID tid = TenantContext.getTenantId();
        if (tid != null) { sql.append(" AND tenant_id = ?3"); params.add(tid); }
        sql.append(" ORDER BY id ASC LIMIT 500");
        var q = em.createNativeQuery(sql.toString(), Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toBrief(t));
        return ApiResponse.ok(list);
    }

    private boolean canViewOpLogs() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            boolean hasPermission = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> "op_log:view".equals(a) || "api_log:view".equals(a) || "ROLE_PLATFORM_ADMIN".equals(a));
            if (hasPermission) return true;
        }
        String u = TenantContext.getUsername();
        return u != null && "admin".equalsIgnoreCase(u);
    }

    private Map<String, Object> toBrief(Tuple t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id"));
        m.put("username", str(t.get("username")));
        m.put("layer", str(t.get("layer")));
        m.put("httpMethod", str(t.get("http_method")));
        m.put("path", str(t.get("path")));
        m.put("status", t.get("status"));
        m.put("spentMs", t.get("spent_ms"));
        m.put("ip", str(t.get("ip")));
        m.put("bizType", str(t.get("biz_type")));
        m.put("bizId", str(t.get("biz_id")));
        m.put("action", str(t.get("action")));
        m.put("remark", str(t.get("remark")));
        Object at = t.get("created_at");
        m.put("createdAt", at == null ? null : String.valueOf(at));
        return m;
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
}
