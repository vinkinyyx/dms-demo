/*
 * 系统维护接口：邮件审批 Token、超时提醒、缓存监视、批量导入
 * 覆盖 US-A-11, US-A-12, US-D-11, US-D-05, US-B-27, US-D-07 (数据权限)
 */
package com.dms.system.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/system-ops")
@RequiredArgsConstructor
public class SystemOpsController {

    private final StringRedisTemplate redis;
    private final RedisConnectionFactory redisConn;
    private final EntityManager em;

    // ============ US-A-11 邮件审批 Token ============

    /**
     * 生成合同审批邮件 Token（有效期 72h）
     * V1 Mock：Token 存 Redis + 打日志
     */
    @PostMapping("/approval-tokens/generate")
    public ApiResponse<Map<String, Object>> genApprovalToken(@RequestBody Map<String, Object> body) {
        String resType = String.valueOf(body.getOrDefault("resourceType", "contract-application"));
        String resId = String.valueOf(body.get("resourceId"));
        String approver = String.valueOf(body.getOrDefault("approverEmail", "approver@example.com"));

        String token = UUID.randomUUID().toString().replace("-", "");
        String key = "dms:approval-token:" + token;
        Map<String, String> data = new LinkedHashMap<>();
        data.put("resourceType", resType);
        data.put("resourceId", resId);
        data.put("approverEmail", approver);
        data.put("createdAt", LocalDateTime.now().toString());
        data.put("used", "false");
        redis.opsForHash().putAll(key, data);
        redis.expire(key, 72, TimeUnit.HOURS);

        String url = "http://<YOUR_SERVER_IP>/api/system-ops/approval-tokens/" + token + "/approve";
        log.warn("[MOCK-EMAIL] 发送审批邮件给 {}: 主题=[DMS] 请审批合同申请 {}, 链接={}", approver, resId, url);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("token", token);
        res.put("approvalUrl", url);
        res.put("approverEmail", approver);
        res.put("expiresInHours", 72);
        return ApiResponse.ok(res);
    }

    /** 用 Token 快捷审批 */
    @GetMapping("/approval-tokens/{token}/approve")
    @Transactional
    public ApiResponse<Map<String, Object>> approveByToken(@PathVariable String token) {
        String key = "dms:approval-token:" + token;
        var raw = redis.opsForHash().entries(key);
        if (raw.isEmpty()) return ApiResponse.fail(40007, "Token 无效或已过期");
        if ("true".equals(raw.get("used"))) return ApiResponse.fail(40008, "该 Token 已使用过");
        String resType = String.valueOf(raw.get("resourceType"));
        String resId = String.valueOf(raw.get("resourceId"));

        // 更新对应实体的状态为 APPROVED
        if ("contract-application".equals(resType)) {
            em.createNativeQuery("UPDATE contract_applications SET status = 'APPROVED', updated_at = now() WHERE id = ?1")
              .setParameter(1, Long.valueOf(resId)).executeUpdate();
        } else if ("order".equals(resType)) {
            em.createNativeQuery("UPDATE orders SET status = 'APPROVED', updated_at = now() WHERE id = ?1")
              .setParameter(1, Long.valueOf(resId)).executeUpdate();
        }
        redis.opsForHash().put(key, "used", "true");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("resourceType", resType);
        res.put("resourceId", resId);
        res.put("approvedAt", LocalDateTime.now().toString());
        res.put("approvedBy", raw.get("approverEmail"));
        return ApiResponse.ok(res);
    }

    // ============ US-A-12 超时提醒 ============

    /**
     * 手动触发超时检查（生产上应用定时任务）
     * 24h / 48h / 72h 未审批的记录发送提醒
     */
    @PostMapping("/check-timeouts")
    public ApiResponse<Map<String, Object>> checkTimeouts() {
        int reminded = doTimeoutCheck();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reminded", reminded);
        res.put("checkedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(res);
    }

    /** 定时任务：每小时检查一次超时 */
    @Scheduled(fixedDelay = 3600_000, initialDelay = 60_000)
    public void scheduledTimeoutCheck() {
        try {
            int n = doTimeoutCheck();
            if (n > 0) log.info("[定时任务] 超时提醒已发出：{} 条", n);
        } catch (Exception e) {
            log.warn("超时检查失败", e);
        }
    }

    private int doTimeoutCheck() {
        int total = 0;
        for (int hours : new int[]{24, 48, 72}) {
            try {
                var q = em.createNativeQuery(
                        "SELECT id, code FROM contract_applications " +
                        "WHERE status = 'SUBMITTED' AND created_at < now() - (?1 || ' hours')::interval " +
                        "LIMIT 20", Tuple.class);
                q.setParameter(1, String.valueOf(hours));
                @SuppressWarnings("unchecked")
                List<Tuple> rows = q.getResultList();
                for (Tuple t : rows) {
                    log.warn("[MOCK-REMIND] 合同申请 {} #{} 已提交超过 {} 小时未审批，发送提醒", t.get("code"), t.get("id"), hours);
                    total++;
                }
            } catch (Exception ignored) {}
        }
        return total;
    }

    // ============ US-D-11 缓存监视 ============

    @GetMapping("/cache/status")
    public ApiResponse<Map<String, Object>> cacheStatus() {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            String info = redisConn.getConnection().serverCommands().info().toString();
            res.put("connected", true);
            // 提取关键指标
            Map<String, String> parsed = new LinkedHashMap<>();
            for (String line : info.split("\n")) {
                if (line.contains(":") && !line.startsWith("#")) {
                    String[] kv = line.split(":", 2);
                    if (kv.length == 2 &&
                        (kv[0].contains("memory") || kv[0].contains("connected") ||
                         kv[0].contains("total_") || kv[0].contains("uptime"))) {
                        parsed.put(kv[0].trim(), kv[1].trim());
                    }
                }
            }
            res.put("info", parsed);

            // 键统计
            Long dbSize = redisConn.getConnection().serverCommands().dbSize();
            res.put("keyCount", dbSize);

            // 采样部分 key
            Set<String> keys = redis.keys("dms:*");
            List<String> sample = keys == null ? new ArrayList<>() :
                    keys.stream().limit(20).toList();
            res.put("sampleKeys", sample);
        } catch (Exception e) {
            res.put("connected", false);
            res.put("error", e.getMessage());
        }
        return ApiResponse.ok(res);
    }

    @PostMapping("/cache/flush")
    public ApiResponse<Map<String, Object>> flushCache(@RequestBody(required = false) Map<String, Object> body) {
        String pattern = body == null ? "dms:cache:*" : String.valueOf(body.getOrDefault("pattern", "dms:cache:*"));
        Set<String> keys = redis.keys(pattern);
        int count = 0;
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
            count = keys.size();
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pattern", pattern);
        res.put("cleared", count);
        return ApiResponse.ok(res);
    }

    // ============ US-D-05 批量用户导入 ============

    @PostMapping("/users/batch-import")
    @Transactional
    public ApiResponse<Map<String, Object>> importUsers(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) body.get("users");
        if (users == null || users.isEmpty()) return ApiResponse.fail(40001, "users 不能为空");
        int success = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        UUID tid = TenantContext.getTenantId();
        for (Map<String, Object> u : users) {
            try {
                em.createNativeQuery(
                        "INSERT INTO users (tenant_id, username, password_hash, name, user_type, email, phone, status, " +
                        "must_change_password, created_at, updated_at) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 'active', true, now(), now())")
                    .setParameter(1, tid)
                    .setParameter(2, u.get("username"))
                    .setParameter(3, "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy") // "password" 默认密码
                    .setParameter(4, u.getOrDefault("name", u.get("username")))
                    .setParameter(5, u.getOrDefault("userType", "vendor"))
                    .setParameter(6, u.get("email"))
                    .setParameter(7, u.get("phone"))
                    .executeUpdate();
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(u.get("username") + ": " + e.getMessage());
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", users.size());
        res.put("success", success);
        res.put("failed", failed);
        res.put("errors", errors);
        return ApiResponse.ok(res);
    }

    // ============ US-B-27 批量发票导入 ============

    @PostMapping("/invoices/batch-import")
    @Transactional
    public ApiResponse<Map<String, Object>> importInvoices(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoices = (List<Map<String, Object>>) body.get("invoices");
        if (invoices == null || invoices.isEmpty()) return ApiResponse.fail(40001, "invoices 不能为空");
        int success = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        UUID tid = TenantContext.getTenantId();
        for (Map<String, Object> inv : invoices) {
            try {
                em.createNativeQuery(
                        "INSERT INTO purchase_invoices (tenant_id, invoice_no, ref_order_id, amount, tax_amount, tax_rate, " +
                        "invoice_date, created_at, updated_at) " +
                        "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, now(), now())")
                    .setParameter(1, tid)
                    .setParameter(2, inv.get("invoiceNo"))
                    .setParameter(3, inv.get("refOrderId"))
                    .setParameter(4, inv.get("amount"))
                    .setParameter(5, inv.getOrDefault("taxAmount", 0))
                    .setParameter(6, inv.getOrDefault("taxRate", 0.13))
                    .setParameter(7, inv.get("issueDate"))
                    .executeUpdate();
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(inv.get("invoiceNo") + ": " + e.getMessage());
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", invoices.size());
        res.put("success", success);
        res.put("failed", failed);
        res.put("errors", errors);
        return ApiResponse.ok(res);
    }

    // ============ US-D-07 数据权限查询接口 ============

    /**
     * 查询当前用户的数据权限范围
     * 前端可根据此决定哪些数据可见
     */
    @GetMapping("/my-data-scope")
    public ApiResponse<Map<String, Object>> myDataScope() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("tenantId", String.valueOf(TenantContext.getTenantId()));
        res.put("userId", TenantContext.getUserId());
        res.put("scopeType", "TENANT");  // V1 只做租户级
        res.put("description", "当前仅按 tenant_id 隔离；行级 dealer/org scope 见 data_scopes 表");
        try {
            var q = em.createNativeQuery(
                    "SELECT scope_type, dealer_ids, org_ids FROM data_scopes WHERE user_id = ?1 LIMIT 20", Tuple.class);
            q.setParameter(1, TenantContext.getUserId());
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            List<Map<String, Object>> scopes = new ArrayList<>();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("scopeType", t.get("scope_type"));
                m.put("dealerIds", t.get("dealer_ids"));
                m.put("orgIds", t.get("org_ids"));
                scopes.add(m);
            }
            res.put("configuredScopes", scopes);
        } catch (Exception ignored) {
            res.put("configuredScopes", Collections.emptyList());
        }
        return ApiResponse.ok(res);
    }

    // ============ US-D-06 RBAC 权限矩阵查询 ============

    @GetMapping("/rbac/matrix")
    public ApiResponse<Map<String, Object>> rbacMatrix() {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            var qRoles = em.createNativeQuery(
                    "SELECT id, code, name, type, description FROM roles ORDER BY id", Tuple.class);
            @SuppressWarnings("unchecked")
            List<Tuple> roles = qRoles.getResultList();
            List<Map<String, Object>> roleList = new ArrayList<>();
            for (Tuple t : roles) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", t.get("id"));
                r.put("code", t.get("code"));
                r.put("name", t.get("name"));
                r.put("type", t.get("type"));
                r.put("description", t.get("description"));
                roleList.add(r);
            }
            res.put("roles", roleList);
        } catch (Exception e) { res.put("roles", Collections.emptyList()); }

        try {
            var qRes = em.createNativeQuery(
                    "SELECT DISTINCT code, name FROM resources ORDER BY code LIMIT 100", Tuple.class);
            @SuppressWarnings("unchecked")
            List<Tuple> rs = qRes.getResultList();
            List<Map<String, Object>> resList = new ArrayList<>();
            for (Tuple t : rs) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("code", t.get("code"));
                r.put("name", t.get("name"));
                resList.add(r);
            }
            res.put("resources", resList);
        } catch (Exception e) { res.put("resources", Collections.emptyList()); }

        try {
            var qRp = em.createNativeQuery(
                    "SELECT role_id, permission_code FROM role_permissions LIMIT 500", Tuple.class);
            @SuppressWarnings("unchecked")
            List<Tuple> rps = qRp.getResultList();
            List<Map<String, Object>> rpList = new ArrayList<>();
            for (Tuple t : rps) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("roleId", t.get("role_id"));
                r.put("permission", t.get("permission_code"));
                rpList.add(r);
            }
            res.put("rolePermissions", rpList);
        } catch (Exception e) { res.put("rolePermissions", Collections.emptyList()); }

        return ApiResponse.ok(res);
    }

    // ============ US-D-08 流程节点查询 ============

    @GetMapping("/workflows")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> workflows() {
        try {
            var q = em.createNativeQuery(
                    "SELECT w.id, w.code, w.name, w.status, COUNT(n.id) AS node_count " +
                    "FROM workflows w LEFT JOIN workflow_nodes n ON n.workflow_id = w.id " +
                    "GROUP BY w.id, w.code, w.name, w.status ORDER BY w.id", Tuple.class);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.get("id"));
                m.put("code", t.get("code"));
                m.put("name", t.get("name"));
                m.put("status", t.get("status"));
                m.put("nodeCount", t.get("node_count"));
                list.add(m);
            }
            return ApiResponse.ok(list);
        } catch (Exception e) {
            return ApiResponse.ok(Collections.emptyList());
        }
    }

    @GetMapping("/workflows/{id}/nodes")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> workflowNodes(@PathVariable Long id) {
        try {
            var q = em.createNativeQuery(
                    "SELECT id, seq, code, name, node_type, assignee_strategy, timeout_hours " +
                    "FROM workflow_nodes WHERE workflow_id = ?1 ORDER BY seq", Tuple.class);
            q.setParameter(1, id);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Tuple t : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.get("id"));
                m.put("seq", t.get("seq"));
                m.put("code", t.get("code"));
                m.put("name", t.get("name"));
                m.put("type", t.get("node_type"));
                m.put("assigneeType", t.get("assignee_strategy"));
                m.put("slaHours", t.get("timeout_hours"));
                list.add(m);
            }
            return ApiResponse.ok(list);
        } catch (Exception e) {
            return ApiResponse.ok(Collections.emptyList());
        }
    }
}

