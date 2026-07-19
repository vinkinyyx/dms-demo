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
                "SELECT sp.id, sp.code, sp.name, sp.level, sp.parent_id, sp.region, sp.status, " +
                "  (SELECT username FROM users u WHERE u.sales_position_id = sp.id LIMIT 1) AS bound_user, " +
                "  (SELECT id FROM users u WHERE u.sales_position_id = sp.id LIMIT 1) AS bound_user_id, " +
                "  (SELECT COUNT(*) FROM dealers d WHERE d.sales_position_id = sp.id) AS dealer_count, " +
                "  sp.created_at, sp.updated_at " +
                "FROM sales_positions sp WHERE sp.tenant_id = ?1 AND sp.deleted_at IS NULL " +
                "ORDER BY sp.level, sp.id LIMIT ?2 OFFSET ?3", Tuple.class);
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
            m.put("boundUser", t.get("bound_user") == null ? "" : t.get("bound_user"));
            m.put("boundUserId", t.get("bound_user_id") == null ? "" : t.get("bound_user_id"));
            m.put("dealerCount", t.get("dealer_count"));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", total); res.put("page", page); res.put("size", size); res.put("list", list);
        return ApiResponse.ok(res);
    }

    /**
     * v3.4.8: 仅返回销售角色的用户，供岗位绑定时使用
     * 返回字段：id, username, name, boundPositionId, boundPositionName
     */
    @GetMapping("/candidate-users")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> candidateUsers() {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT u.id, u.username, u.name, u.sales_position_id, sp.name AS position_name " +
                "FROM users u LEFT JOIN sales_positions sp ON sp.id = u.sales_position_id " +
                "WHERE u.tenant_id = ?1 AND u.role = 'sales' AND (u.deleted_at IS NULL) " +
                "ORDER BY u.username", Tuple.class);
        q.setParameter(1, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("username", t.get("username"));
            m.put("name", t.get("name") == null ? "" : t.get("name"));
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
                "SELECT sp.id, sp.code, sp.name, sp.level, sp.parent_id, sp.region, " +
                "  (SELECT username FROM users u WHERE u.sales_position_id = sp.id LIMIT 1) AS bound_user, " +
                "  (SELECT COUNT(*) FROM dealers d WHERE d.sales_position_id = sp.id) AS dealer_count " +
                "FROM sales_positions sp WHERE sp.tenant_id = ?1 AND sp.deleted_at IS NULL ORDER BY sp.level, sp.id",
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
            node.put("boundUser", t.get("bound_user"));
            node.put("dealerCount", t.get("dealer_count"));
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

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String code = strReq(body, "code");
        String name = strReq(body, "name");
        Integer level = toInt(body.get("level"));
        Long parentId = toLong(body.get("parentId"));
        String region = str(body.get("region"));

        if (level == null || level < 1 || level > 6) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "level 必须 1-6");
        }

        var ins = em.createNativeQuery(
                "INSERT INTO sales_positions (tenant_id, code, name, level, parent_id, region, status, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'active', now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, name)
                .setParameter(4, level).setParameter(5, parentId).setParameter(6, region);
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

        int aff = em.createNativeQuery(
                "UPDATE sales_positions SET name = ?1, level = ?2, region = ?3, status = ?4, updated_at = now() " +
                "WHERE id = ?5 AND tenant_id = ?6")
                .setParameter(1, name).setParameter(2, level).setParameter(3, region)
                .setParameter(4, status == null ? "active" : status)
                .setParameter(5, id).setParameter(6, tid).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "岗位不存在");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("name", name);
        return ApiResponse.ok(res);
    }

    @PutMapping("/{id}/bind-user")
    @Transactional
    public ApiResponse<Map<String, Object>> bindUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        // v3.4.7: userId=null 表示解绑
        if (userId == null) {
            em.createNativeQuery("UPDATE users SET sales_position_id = NULL WHERE sales_position_id = ?1")
                    .setParameter(1, id).executeUpdate();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("positionId", id); r.put("userId", null); r.put("message", "已解绑");
            return ApiResponse.ok(r);
        }
        // 该用户已绑定其他岗位？（业务规则：一人一岗）
        var chk2 = em.createNativeQuery("SELECT sales_position_id FROM users WHERE id = ?1");
        chk2.setParameter(1, userId);
        Object cur = chk2.getSingleResult();
        if (cur != null && !((Number) cur).equals(id)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "该用户已绑定其他岗位，请先解除后再绑");
        }
        // v3.4.7: 先清空该岗位当前绑定的其他用户（一位一人）
        em.createNativeQuery("UPDATE users SET sales_position_id = NULL WHERE sales_position_id = ?1 AND id != ?2")
                .setParameter(1, id).setParameter(2, userId).executeUpdate();
        // 再绑定目标用户
        em.createNativeQuery("UPDATE users SET sales_position_id = ?1 WHERE id = ?2")
                .setParameter(1, id).setParameter(2, userId).executeUpdate();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("positionId", id); res.put("userId", userId); res.put("message", "绑定成功");
        return ApiResponse.ok(res);
    }

    @PutMapping("/{id}/bind-dealers")
    @Transactional
    public ApiResponse<Map<String, Object>> bindDealers(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> dealerIds = (List<Object>) body.getOrDefault("dealerIds", Collections.emptyList());

        // v3.4.7: 全量替换语义 —— 先解挂当前岗位所有经销商，再逐个挂新的
        em.createNativeQuery("UPDATE dealers SET sales_position_id = NULL WHERE sales_position_id = ?1")
                .setParameter(1, id).executeUpdate();

        int count = 0;
        for (Object o : dealerIds) {
            Long did = toLong(o);
            if (did == null) continue;
            // 校验：一经销商只能挂一个岗位 —— 若目标经销商已属其他岗位，跳过
            var chk = em.createNativeQuery("SELECT sales_position_id FROM dealers WHERE id = ?1");
            chk.setParameter(1, did);
            Object cur = null;
            try { cur = chk.getSingleResult(); } catch (Exception ignored) {}
            if (cur != null && !((Number) cur).equals(id)) continue;
            em.createNativeQuery("UPDATE dealers SET sales_position_id = ?1 WHERE id = ?2")
                    .setParameter(1, id).setParameter(2, did).executeUpdate();
            count++;
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("positionId", id); res.put("boundCount", count);
        return ApiResponse.ok(res);
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
        List<?> ls = q.getResultList();
        if (ls.isEmpty()) { res.put("scope", "NONE"); return ApiResponse.ok(res); }

        Tuple t = (Tuple) ls.get(0);
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
        // sales：走岗位树
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
            var dq = em.createNativeQuery(
                    "SELECT id FROM dealers WHERE tenant_id = ?1 AND sales_position_id = ANY(?2)");
            dq.setParameter(1, tid);
            dq.setParameter(2, allPos.toArray(new Long[0]));
            @SuppressWarnings("unchecked")
            List<Object> ids = dq.getResultList();
            res.put("scope", "POSITION_TREE");
            res.put("positionId", positionId);
            res.put("subordinatePositionCount", allPos.size());
            res.put("dealerIds", ids);
        }
        return ApiResponse.ok(res);
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
    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
