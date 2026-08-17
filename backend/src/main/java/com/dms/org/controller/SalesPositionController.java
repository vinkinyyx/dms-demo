/*
 * 销售岗位管理 Controller (v3.4)
 *   /api/sales-positions       CRUD
 *   /api/sales-positions/tree  层级树
 *   /api/sales-positions/{id}/bind-user     绑定用户
 *   /api/sales-positions/{id}/bind-dealers  归属经销商
 *   /api/sales-positions/my-scope           当前登录用户可访问的经销商集合
 */
package com.dms.org.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sales-positions")
@RequiredArgsConstructor
public class SalesPositionController {

    private final EntityManager em;

    @GetMapping
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort) {
        UUID tid = TenantContext.getTenantId();
        int offset = (page - 1) * size;

        long total = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM sales_positions WHERE tenant_id = ?1 AND (deleted_at IS NULL)")
                .setParameter(1, tid).getSingleResult()).longValue();

        var q = em.createNativeQuery(
                "SELECT sp.id, sp.code, sp.name, sp.level, sp.parent_id, sp.region, sp.status, sp.sort_order, " +
                "  (SELECT username FROM users u WHERE u.sales_position_id = sp.id LIMIT 1) AS bound_user, " +
                "  (SELECT id FROM users u WHERE u.sales_position_id = sp.id LIMIT 1) AS bound_user_id, " +
                "  (SELECT COUNT(*) FROM dealers d WHERE d.sales_position_id = sp.id) AS dealer_count, " +
                "  (SELECT COUNT(*) FROM position_users pu WHERE pu.position_id = sp.id) AS position_user_count, " +
                "  (SELECT COUNT(*) FROM position_dealers pd WHERE pd.position_id = sp.id) AS position_dealer_count, " +
                "  sp.created_at, sp.updated_at " +
                "FROM sales_positions sp WHERE sp.tenant_id = ?1 AND sp.deleted_at IS NULL " +
                "ORDER BY sp.sort_order, sp.level, sp.id LIMIT ?2 OFFSET ?3", Tuple.class);
        q.setParameter(1, tid).setParameter(2, size).setParameter(3, offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("name", t.get("name"));
            m.put("level", t.get("level"));
            m.put("parentId", t.get("parent_id") == null ? "" : t.get("parent_id"));
            m.put("region", t.get("region") == null ? "" : t.get("region"));
            m.put("status", t.get("status"));
            m.put("sortOrder", t.get("sort_order"));
            m.put("boundUser", t.get("bound_user") == null ? "" : t.get("bound_user"));
            m.put("boundUserId", t.get("bound_user_id") == null ? "" : t.get("bound_user_id"));
            m.put("dealerCount", t.get("dealer_count"));
            m.put("positionUserCount", t.get("position_user_count"));
            m.put("positionDealerCount", t.get("position_dealer_count"));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", total); res.put("page", page); res.put("size", size); res.put("list", list);
        return ApiResponse.ok(res);
    }

    /**
     * v3.7.3: 返回销售/经销商角色的用户，供岗位绑定时使用
     * 返回字段：id, username, name, userType, boundPositionId, boundPositionName
     * v3.4.8: 仅返回销售角色的用户
     */
    @GetMapping("/candidate-users")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> candidateUsers(
            @RequestParam(required = false) String role) {
        UUID tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "SELECT u.id, u.username, u.name, u.role, u.sales_position_id, sp.name AS position_name " +
                "FROM users u LEFT JOIN sales_positions sp ON sp.id = u.sales_position_id " +
                "WHERE u.tenant_id = ?1 AND u.deleted_at IS NULL ");
        if ("sales".equals(role)) {
            sql.append("AND u.role = 'sales' ");
        } else if ("dealer".equals(role)) {
            sql.append("AND u.role = 'dealer' ");
        } else {
            sql.append("AND u.role IN ('sales', 'dealer') ");
        }
        sql.append("ORDER BY u.role, u.username");
        var q = em.createNativeQuery(sql.toString(), Tuple.class);
        q.setParameter(1, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("username", t.get("username"));
            m.put("name", t.get("name") == null ? "" : t.get("name"));
            m.put("userType", t.get("role"));
            m.put("boundPositionId", t.get("sales_position_id") == null ? "" : t.get("sales_position_id"));
            m.put("boundPositionName", t.get("position_name") == null ? "" : t.get("position_name"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/tree")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> tree() {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT sp.id, sp.code, sp.name, sp.level, sp.parent_id, sp.region, sp.status, sp.sort_order, " +
                "  (SELECT username FROM users u WHERE u.sales_position_id = sp.id LIMIT 1) AS bound_user, " +
                "  (SELECT COUNT(*) FROM dealers d WHERE d.sales_position_id = sp.id) AS dealer_count, " +
                "  (SELECT COUNT(*) FROM position_users pu WHERE pu.position_id = sp.id) AS user_count, " +
                "  (SELECT COUNT(*) FROM position_dealers pd WHERE pd.position_id = sp.id) AS bind_dealer_count " +
                "FROM sales_positions sp WHERE sp.tenant_id = ?1 AND sp.deleted_at IS NULL ORDER BY sp.sort_order, sp.level, sp.id",
                Tuple.class);
        q.setParameter(1, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Tuple t : rows) {
            Long id = ((Number) t.get("id")).longValue();
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", id);
            node.put("code", t.get("code"));
            node.put("name", t.get("name"));
            node.put("level", t.get("level"));
            node.put("region", t.get("region"));
            node.put("status", t.get("status"));
            node.put("sortOrder", t.get("sort_order"));
            node.put("boundUser", t.get("bound_user"));
            node.put("dealerCount", t.get("dealer_count"));
            node.put("userCount", t.get("user_count"));
            node.put("bindDealerCount", t.get("bind_dealer_count"));
            node.put("children", new ArrayList<>());
            map.put(id, node);
        }
        for (Tuple t : rows) {
            Long id = ((Number) t.get("id")).longValue();
            Object pid = t.get("parent_id");
            if (pid == null) roots.add(map.get(id));
            else {
                Map<String, Object> parent = map.get(((Number) pid).longValue());
                if (parent != null) ((List<Object>) parent.get("children")).add(map.get(id));
                else roots.add(map.get(id));
            }
        }
        return ApiResponse.ok(roots);
    }

    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getOne(@PathVariable Long id) {
        var q = em.createNativeQuery(
                "SELECT sp.id, sp.code, sp.name, sp.level, sp.parent_id, sp.region, sp.status, sp.sort_order, "
              + "sp.created_at, sp.updated_at FROM sales_positions sp WHERE sp.id = ?1 AND sp.tenant_id = ?2 AND sp.deleted_at IS NULL",
                jakarta.persistence.Tuple.class);
        q.setParameter(1, id).setParameter(2, TenantContext.getTenantId());
        var rows = q.getResultList();
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "岗位不存在");
        jakarta.persistence.Tuple t = (jakarta.persistence.Tuple) rows.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id")); m.put("code", t.get("code")); m.put("name", t.get("name"));
        m.put("level", t.get("level")); m.put("parentId", t.get("parent_id"));
        m.put("region", t.get("region")); m.put("status", t.get("status")); m.put("sortOrder", t.get("sort_order"));
        m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
        m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
        return ApiResponse.ok(m);
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String code = strReq(body, "code");
        String name = strReq(body, "name");
        Integer level = toInt(body.get("level"));
        Long parentId = toLong(body.get("parentId"));
        Integer sortOrder = toInt(body.get("sortOrder"));
        String region = str(body.get("region"));

        if (level == null || level < 1 || level > 6) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "level 必须 1-6");
        }

        var ins = em.createNativeQuery(
                "INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region, status, sort_order, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'active', ?7, now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, name)
                .setParameter(4, level).setParameter(5, parentId).setParameter(6, region)
                .setParameter(7, sortOrder == null ? 0 : sortOrder);
        Long id = ((Number) ins.getSingleResult()).longValue();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("code", code); res.put("name", name);
        return ApiResponse.ok(res);
    }

    /** v3.4.6：编辑岗位 */
    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String name = str(body.get("name"));
        Integer level = toInt(body.get("level"));
        String region = str(body.get("region"));
        String status = str(body.get("status"));
        if (name == null || name.isBlank()) throw new BusinessException(ErrorCode.PARAM_INVALID, "name 必填");
        if (level == null || level < 1 || level > 6) throw new BusinessException(ErrorCode.PARAM_INVALID, "level 必须 1-6");

        Long updParentId = toLong(body.get("parentId"));
        Integer updSortOrder = toInt(body.get("sortOrder"));
        int aff = em.createNativeQuery(
                "UPDATE sales_positions SET name = ?1, level = ?2, region = ?3, status = ?4, " +
                "parent_id = COALESCE(?5, parent_id), sort_order = COALESCE(?6, sort_order), updated_at = now() " +
                "WHERE id = ?7 AND tenant_id = ?8")
                .setParameter(1, name).setParameter(2, level).setParameter(3, region)
                .setParameter(4, status == null ? "active" : status)
                .setParameter(5, updParentId).setParameter(6, updSortOrder)
                .setParameter(7, id).setParameter(8, tid).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "岗位不存在");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("name", name);
        return ApiResponse.ok(res);
    }

    /**
     * v3.8.6: 绑定销售账号（全量替换，带业绩占比）。
     * body: { "users": [ {"id":1,"shareRatio":0.6}, ... ] }
     * 一个销售只能归属一个岗位；同一岗位占比总和必须 <= 1。
     */
    @PutMapping("/{id}/bind-users")
    @Transactional
    public ApiResponse<Map<String, Object>> bindUsers(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) body.getOrDefault("users", Collections.emptyList());

        List<Long> ids = new ArrayList<>();
        java.util.Map<Long, java.math.BigDecimal> ratios = new HashMap<>();
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (Object o : items) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> it = (Map<String, Object>) o;
            Long uid = toLong(it.get("id"));
            if (uid == null) continue;
            java.math.BigDecimal r = toBd(it.get("shareRatio"));
            if (r.signum() < 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "业绩占比不能为负");
            ids.add(uid);
            ratios.put(uid, r);
            sum = sum.add(r);
        }
        if (sum.compareTo(java.math.BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "该岗位销售账号业绩占比总和为 " + sum + "，不能超过 1");
        }

        for (Long uid : ids) {
            @SuppressWarnings("unchecked")
            List<Tuple> chk = em.createNativeQuery(
                    "SELECT u.role, pu.position_id FROM users u " +
                    "LEFT JOIN position_users pu ON pu.user_id = u.id AND pu.tenant_id = u.tenant_id " +
                    "WHERE u.id = ?1 AND u.tenant_id = ?2 AND u.deleted_at IS NULL", Tuple.class)
                    .setParameter(1, uid).setParameter(2, tid).getResultList();
            if (chk.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在: " + uid);
            Tuple t = chk.get(0);
            if (!"sales".equals(String.valueOf(t.get("role")))) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只能绑定销售角色账号: " + uid);
            }
            Object pos = t.get("position_id");
            if (pos != null && ((Number) pos).longValue() != id) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "销售账号已被其他岗位占用: " + uid);
            }
        }

        em.createNativeQuery("DELETE FROM position_users WHERE tenant_id = ?1 AND position_id = ?2")
                .setParameter(1, tid).setParameter(2, id).executeUpdate();
        for (Long uid : ids) {
            em.createNativeQuery(
                    "INSERT INTO position_users (tenant_id, position_id, user_id, role_type, share_ratio, created_at) " +
                    "VALUES (?1, ?2, ?3, 'sales', ?4, now())")
                    .setParameter(1, tid).setParameter(2, id).setParameter(3, uid)
                    .setParameter(4, ratios.get(uid)).executeUpdate();
        }
        // 冗余同步 users.sales_position_id：先清空该岗位旧绑定，再写入新绑定
        em.createNativeQuery("UPDATE users SET sales_position_id = NULL WHERE sales_position_id = ?1 AND id <> ALL(?2)")
                .setParameter(1, id)
                .setParameter(2, ids.isEmpty() ? new Long[]{-1L} : ids.toArray(new Long[0]))
                .executeUpdate();
        if (!ids.isEmpty()) {
            em.createNativeQuery("UPDATE users SET sales_position_id = ?1 WHERE id = ANY(?2)")
                    .setParameter(1, id).setParameter(2, ids.toArray(new Long[0])).executeUpdate();
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("positionId", id);
        res.put("boundCount", ids.size());
        res.put("shareSum", sum);
        return ApiResponse.ok(res);
    }

    /**
     * v3.8.6: 分配经销商（全量替换）。一个经销商只能归属一个岗位。
     * body: { "dealerIds": [1,2,3] }
     */
    @PutMapping("/{id}/bind-dealers")
    @Transactional
    public ApiResponse<Map<String, Object>> bindDealers(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.getOrDefault("dealerIds", Collections.emptyList());
        List<Long> ids = new ArrayList<>();
        for (Object o : raw) {
            Long did = toLong(o);
            if (did != null) ids.add(did);
        }
        for (Long did : ids) {
            @SuppressWarnings("unchecked")
            List<Tuple> chk = em.createNativeQuery(
                    "SELECT pd.position_id FROM dealers d " +
                    "LEFT JOIN position_dealers pd ON pd.dealer_id = d.id AND pd.tenant_id = d.tenant_id " +
                    "WHERE d.id = ?1 AND d.tenant_id = ?2 AND d.deleted_at IS NULL", Tuple.class)
                    .setParameter(1, did).setParameter(2, tid).getResultList();
            if (chk.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "经销商不存在: " + did);
            Object pos = chk.get(0).get("position_id");
            if (pos != null && ((Number) pos).longValue() != id) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "经销商已归属其他岗位: " + did);
            }
        }
        em.createNativeQuery("DELETE FROM position_dealers WHERE tenant_id = ?1 AND position_id = ?2")
                .setParameter(1, tid).setParameter(2, id).executeUpdate();
        for (Long did : ids) {
            em.createNativeQuery(
                    "INSERT INTO position_dealers (tenant_id, position_id, dealer_id, created_at) VALUES (?1, ?2, ?3, now())")
                    .setParameter(1, tid).setParameter(2, id).setParameter(3, did).executeUpdate();
        }
        em.createNativeQuery("UPDATE dealers SET sales_position_id = NULL WHERE sales_position_id = ?1 AND id <> ALL(?2)")
                .setParameter(1, id)
                .setParameter(2, ids.isEmpty() ? new Long[]{-1L} : ids.toArray(new Long[0]))
                .executeUpdate();
        if (!ids.isEmpty()) {
            em.createNativeQuery("UPDATE dealers SET sales_position_id = ?1 WHERE id = ANY(?2)")
                    .setParameter(1, id).setParameter(2, ids.toArray(new Long[0])).executeUpdate();
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("positionId", id);
        res.put("boundCount", ids.size());
        return ApiResponse.ok(res);
    }

    /**
     * 候选销售账号：列出所有 sales，标注是否已被某岗位占用及当前占比。
     */
    @GetMapping("/{id}/candidates/users")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> candidateUsersForPosition(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(
                "SELECT u.id, u.username, u.name, pu.position_id, sp.name AS position_name, pu.share_ratio " +
                "FROM users u " +
                "LEFT JOIN position_users pu ON pu.user_id = u.id AND pu.tenant_id = u.tenant_id " +
                "LEFT JOIN sales_positions sp ON sp.id = pu.position_id " +
                "WHERE u.tenant_id = ?1 AND u.role = 'sales' AND u.deleted_at IS NULL " +
                "ORDER BY u.username", Tuple.class)
                .setParameter(1, tid).getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("username", t.get("username"));
            m.put("name", t.get("name") == null ? "" : t.get("name"));
            Object pid = t.get("position_id");
            m.put("boundPositionId", pid == null ? null : ((Number) pid).longValue());
            m.put("boundPositionName", t.get("position_name"));
            m.put("currentShareRatio", t.get("share_ratio"));
            m.put("occupiedByOther", pid != null && ((Number) pid).longValue() != id);
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    /**
     * 候选经销商：列出所有经销商，标注归属岗位。
     */
    @GetMapping("/{id}/candidates/dealers")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> candidateDealersForPosition(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(
                "SELECT d.id, d.code, d.name, pd.position_id, sp.name AS position_name " +
                "FROM dealers d " +
                "LEFT JOIN position_dealers pd ON pd.dealer_id = d.id AND pd.tenant_id = d.tenant_id " +
                "LEFT JOIN sales_positions sp ON sp.id = pd.position_id " +
                "WHERE d.tenant_id = ?1 AND d.deleted_at IS NULL " +
                "ORDER BY d.code", Tuple.class)
                .setParameter(1, tid).getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("name", t.get("name"));
            Object pid = t.get("position_id");
            m.put("boundPositionId", pid == null ? null : ((Number) pid).longValue());
            m.put("boundPositionName", t.get("position_name"));
            m.put("occupiedByOther", pid != null && ((Number) pid).longValue() != id);
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    /**
     * 岗位下绑定的销售账号（含业绩占比）。
     */
    @GetMapping("/{id}/users")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> getPositionUsers(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(
                "SELECT u.id, u.username, u.name, pu.share_ratio " +
                "FROM position_users pu JOIN users u ON u.id = pu.user_id " +
                "WHERE pu.tenant_id = ?1 AND pu.position_id = ?2 AND u.deleted_at IS NULL " +
                "ORDER BY u.username", Tuple.class)
                .setParameter(1, tid).setParameter(2, id).getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("username", t.get("username"));
            m.put("name", t.get("name") == null ? "" : t.get("name"));
            m.put("shareRatio", t.get("share_ratio"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    /**
     * 岗位下归属的经销商。
     */
    @GetMapping("/{id}/dealers")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> getPositionDealers(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(
                "SELECT d.id, d.code, d.name " +
                "FROM position_dealers pd JOIN dealers d ON d.id = pd.dealer_id " +
                "WHERE pd.tenant_id = ?1 AND pd.position_id = ?2 AND d.deleted_at IS NULL " +
                "ORDER BY d.code", Tuple.class)
                .setParameter(1, tid).setParameter(2, id).getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("name", t.get("name"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    /**
     * 当前用户可访问的经销商范围（基于其绑定岗位递归所有下级岗位）
     */
    @GetMapping("/my-scope")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> myScope() {
        UUID tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        Map<String, Object> res = new LinkedHashMap<>();
        if (uid == null) { res.put("scope", "NONE"); return ApiResponse.ok(res); }

        var q = em.createNativeQuery(
                "SELECT role, sales_position_id, dealer_id FROM users WHERE id = ?1", Tuple.class);
        q.setParameter(1, uid);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = q.getResultList();
        if (ls.isEmpty()) { res.put("scope", "NONE"); return ApiResponse.ok(res); }

        Tuple t = ls.get(0);
        String role = String.valueOf(t.get("role"));
        Object posObj = t.get("sales_position_id");
        Object dealerObj = t.get("dealer_id");
        res.put("role", role);

        if ("admin".equals(role)) {
            res.put("scope", "ALL");
            return ApiResponse.ok(res);
        }
        if ("dealer".equals(role)) {
            res.put("scope", "SELF");
            res.put("dealerIds", dealerObj == null ? Collections.emptyList() : List.of(dealerObj));
            return ApiResponse.ok(res);
        }
        if (posObj == null) {
            res.put("scope", "NONE");
            res.put("dealerIds", Collections.emptyList());
            return ApiResponse.ok(res);
        }
        Long positionId = ((Number) posObj).longValue();
        Set<Long> allPos = PositionResolver.recursivePositions(em, tid, positionId);
        if (allPos.isEmpty()) {
            res.put("scope", "NONE");
            res.put("dealerIds", Collections.emptyList());
        } else {
            @SuppressWarnings("unchecked")
            List<Object> ids = em.createNativeQuery(
                    "SELECT dealer_id FROM position_dealers WHERE tenant_id = ?1 AND position_id = ANY(?2)")
                    .setParameter(1, tid).setParameter(2, allPos.toArray(new Long[0])).getResultList();
            res.put("scope", "POSITION_TREE");
            res.put("positionId", positionId);
            res.put("subordinatePositionCount", allPos.size());
            res.put("dealerIds", ids);
        }
        return ApiResponse.ok(res);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        em.createNativeQuery("UPDATE sales_positions SET deleted_at = now(), updated_at = now() WHERE id = ?1 AND tenant_id = ?2")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        return ApiResponse.ok(null);
    }

    // helpers
    private String strReq(Map<String, Object> body, String key) {
        Object o = body.get(key);
        if (o == null) throw new BusinessException(ErrorCode.PARAM_MISSING, key + " 必填");
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, key + " 必填");
        return s;
    }
    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private java.math.BigDecimal toBd(Object o) {
        if (o == null) return java.math.BigDecimal.ZERO;
        try { return new java.math.BigDecimal(String.valueOf(o)); }
        catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }
        private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
