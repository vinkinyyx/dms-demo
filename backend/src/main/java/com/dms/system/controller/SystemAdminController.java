/*
 * 系统管理只读控制器：审计日志、登录日志、通知消息、系统健康统计
 * 供后台管理员使用（字段与实际 DB schema 对齐）
 */
package com.dms.system.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemAdminController {

    private final EntityManager em;

    @GetMapping("/audit-logs")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> auditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType) {
        UUID tenantId = TenantContext.getTenantId();
        int offset = (page - 1) * size;

        StringBuilder where = new StringBuilder(" WHERE tenant_id = :tid ");
        Map<String, Object> params = new HashMap<>();
        params.put("tid", tenantId);
        if (action != null && !action.isBlank()) { where.append(" AND action = :action "); params.put("action", action); }
        if (entityType != null && !entityType.isBlank()) { where.append(" AND resource_type = :et "); params.put("et", entityType); }

        var countQ = em.createNativeQuery("SELECT COUNT(*) FROM audit_logs " + where);
        params.forEach(countQ::setParameter);
        long total = ((Number) countQ.getSingleResult()).longValue();

        var listQ = em.createNativeQuery(
                "SELECT id, tenant_id, user_id, action, resource_type, resource_id, ip, at_time " +
                "FROM audit_logs " + where + " ORDER BY at_time DESC LIMIT :limit OFFSET :offset",
                Tuple.class);
        params.forEach(listQ::setParameter);
        listQ.setParameter("limit", size);
        listQ.setParameter("offset", offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = listQ.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("userId", r.get("user_id"));
            m.put("username", "user_" + r.get("user_id"));
            m.put("action", r.get("action"));
            m.put("entityType", r.get("resource_type"));
            m.put("entityId", r.get("resource_id"));
            m.put("ipAddress", r.get("ip"));
            m.put("atTime", String.valueOf(r.get("at_time")));
            list.add(m);
        }

        return ApiResponse.ok(pageData(total, page, size, list));
    }

    @GetMapping("/login-logs")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> loginLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username) {
        UUID tenantId = TenantContext.getTenantId();
        int offset = (page - 1) * size;

        String where = " WHERE l.tenant_id = :tid ";
        var countQ = em.createNativeQuery("SELECT COUNT(*) FROM user_login_logs l " + where);
        countQ.setParameter("tid", tenantId);
        long total = ((Number) countQ.getSingleResult()).longValue();

        var listQ = em.createNativeQuery(
                "SELECT l.id, l.tenant_id, l.user_id, u.username, l.login_type, l.ip, l.user_agent, l.success, l.fail_reason, l.at_time " +
                "FROM user_login_logs l LEFT JOIN users u ON l.user_id = u.id " +
                where + " ORDER BY l.at_time DESC LIMIT :limit OFFSET :offset",
                Tuple.class);
        listQ.setParameter("tid", tenantId);
        listQ.setParameter("limit", size);
        listQ.setParameter("offset", offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = listQ.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("userId", r.get("user_id"));
            m.put("username", r.get("username"));
            m.put("loginType", r.get("login_type"));
            m.put("ipAddress", r.get("ip"));
            m.put("userAgent", r.get("user_agent"));
            m.put("success", r.get("success"));
            m.put("failReason", r.get("fail_reason"));
            m.put("atTime", String.valueOf(r.get("at_time")));
            list.add(m);
        }

        return ApiResponse.ok(pageData(total, page, size, list));
    }

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> notifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = TenantContext.getTenantId();
        int offset = (page - 1) * size;

        var countQ = em.createNativeQuery("SELECT COUNT(*) FROM notifications WHERE tenant_id = :tid");
        countQ.setParameter("tid", tenantId);
        long total = ((Number) countQ.getSingleResult()).longValue();

        var listQ = em.createNativeQuery(
                "SELECT id, tenant_id, user_id, channel, title, body, ref_type, ref_id, is_read, created_at " +
                "FROM notifications WHERE tenant_id = :tid ORDER BY created_at DESC LIMIT :limit OFFSET :offset",
                Tuple.class);
        listQ.setParameter("tid", tenantId);
        listQ.setParameter("limit", size);
        listQ.setParameter("offset", offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = listQ.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("receiverId", r.get("user_id"));
            m.put("channel", r.get("channel"));
            m.put("category", r.get("ref_type"));
            m.put("title", r.get("title"));
            m.put("content", r.get("body"));
            m.put("isRead", r.get("is_read"));
            m.put("createdAt", String.valueOf(r.get("created_at")));
            list.add(m);
        }

        return ApiResponse.ok(pageData(total, page, size, list));
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> stats() {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("users", countOf("users", tenantId));
        stats.put("dealers", countOf("dealers", tenantId));
        stats.put("products", countOf("products", tenantId));
        stats.put("orders", countOf("orders", tenantId));
        stats.put("contracts", countOf("contracts", tenantId));
        stats.put("inventoryRecords", countOf("inventory", tenantId));
        stats.put("auditLogs", countOf("audit_logs", tenantId));
        stats.put("loginLogs", countOf("user_login_logs", tenantId));
        stats.put("notifications", countOf("notifications", tenantId));
        stats.put("promotions", countOf("promotions", tenantId));
        stats.put("roles", countOf("roles", tenantId));
        stats.put("salesOuts", countOf("sales_outs", tenantId));
        return ApiResponse.ok(stats);
    }

    @GetMapping("/settings")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> settings() {
        var q = em.createNativeQuery(
                "SELECT scope, key, value_json, description FROM system_settings ORDER BY scope, key",
                Tuple.class);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scope", r.get("scope"));
            m.put("key", r.get("key"));
            m.put("value", String.valueOf(r.get("value_json")));
            m.put("description", r.get("description"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/dicts")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> dicts() {
        var q = em.createNativeQuery(
                "SELECT dt.code AS type_code, dt.name AS type_name, di.code AS item_code, di.name AS label, di.seq " +
                " FROM dict_types dt LEFT JOIN dict_items di ON dt.id = di.type_id " +
                " ORDER BY dt.code, di.seq",
                Tuple.class);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("typeCode", r.get("type_code"));
            m.put("typeName", r.get("type_name"));
            m.put("itemCode", r.get("item_code"));
            m.put("label", r.get("label"));
            m.put("sortOrder", r.get("seq"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/tenants-brief")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> tenantsBrief() {
        var q = em.createNativeQuery(
                "SELECT id, code, name, industry, status, created_at FROM tenants ORDER BY created_at DESC LIMIT 100",
                Tuple.class);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(r.get("id")));
            m.put("code", r.get("code"));
            m.put("name", r.get("name"));
            m.put("industry", r.get("industry"));
            m.put("status", r.get("status"));
            m.put("createdAt", String.valueOf(r.get("created_at")));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    private Map<String, Object> pageData(long total, int page, int size, List<Map<String, Object>> list) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("total", total);
        d.put("page", page);
        d.put("size", size);
        d.put("list", list);
        return d;
    }

    private long countOf(String table, UUID tenantId) {
        try {
            String sql = "SELECT COUNT(*) FROM " + table;
            // 检查是否含 tenant_id 列
            String colSql = "SELECT 1 FROM information_schema.columns WHERE table_name = '" + table + "' AND column_name = 'tenant_id' LIMIT 1";
            var chk = em.createNativeQuery(colSql).getResultList();
            if (!chk.isEmpty()) {
                sql += " WHERE tenant_id = :tid";
                var q = em.createNativeQuery(sql);
                q.setParameter("tid", tenantId);
                return ((Number) q.getSingleResult()).longValue();
            } else {
                return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
            }
        } catch (Exception e) {
            return 0L;
        }
    }
}
