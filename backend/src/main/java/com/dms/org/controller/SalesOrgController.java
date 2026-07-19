/*
 * 销售组织架构 Controller (v3.3)
 *   /api/sales-users        - 销售人员管理
 *   /api/sales-org/tree     - 销售组织树
 *   /api/sales-org/subordinates/{userId} - 递归查询下级
 *   /api/sales-org/my-dealers - 我负责的经销商 (含下级)
 */
package com.dms.org.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sales-org")
@RequiredArgsConstructor
public class SalesOrgController {

    private final EntityManager em;

    @GetMapping("/tree")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> tree() {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT id, name, code, level, parent_id, region FROM sales_users " +
                "WHERE tenant_id = ?1 AND (deleted_at IS NULL) ORDER BY level, id", Tuple.class);
        q.setParameter(1, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> node = new LinkedHashMap<>();
            Long id = ((Number) t.get("id")).longValue();
            node.put("id", id);
            node.put("name", t.get("name"));
            node.put("code", t.get("code"));
            node.put("level", t.get("level"));
            node.put("region", t.get("region"));
            Object p = t.get("parent_id");
            node.put("parentId", p);
            node.put("children", new ArrayList<>());
            map.put(id, node);
        }
        for (Map<String, Object> n : map.values()) {
            Object pid = n.get("parentId");
            if (pid == null) roots.add(n);
            else {
                Map<String, Object> parent = map.get(((Number) pid).longValue());
                if (parent != null) ((List<Object>) parent.get("children")).add(n);
                else roots.add(n);
            }
        }
        return ApiResponse.ok(roots);
    }

    /**
     * 递归查询某个销售的所有下级 ID（含自己）
     */
    @GetMapping("/subordinates/{userId}")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> subordinates(@PathVariable Long userId) {
        UUID tid = TenantContext.getTenantId();
        Set<Long> ids = SalesOrgResolver.recursiveSubordinates(em, tid, userId);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("root", userId);
        res.put("subordinateIds", ids);
        res.put("count", ids.size());
        return ApiResponse.ok(res);
    }

    /**
     * 查询当前登录用户负责的经销商 (含所有下级负责的)
     */
    @GetMapping("/my-dealers")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> myDealers() {
        UUID tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        Map<String, Object> res = new LinkedHashMap<>();

        // 1. 找当前用户的 sales_user_id 和 role
        var infoQ = em.createNativeQuery(
                "SELECT role, sales_user_id, dealer_id FROM users WHERE id = ?1", Tuple.class);
        infoQ.setParameter(1, uid);
        List<?> lst = infoQ.getResultList();
        if (lst.isEmpty()) {
            res.put("role", "unknown"); res.put("dealerIds", Collections.emptyList());
            return ApiResponse.ok(res);
        }
        Tuple info = (Tuple) lst.get(0);
        String role = String.valueOf(info.get("role"));
        Object salesUidObj = info.get("sales_user_id");
        Object dealerIdObj = info.get("dealer_id");

        res.put("role", role);

        if ("admin".equals(role)) {
            var allQ = em.createNativeQuery("SELECT id FROM dealers WHERE tenant_id = ?1");
            allQ.setParameter(1, tid);
            res.put("dealerIds", allQ.getResultList());
            res.put("scope", "ALL");
        } else if ("dealer".equals(role)) {
            List<Object> single = new ArrayList<>();
            if (dealerIdObj != null) single.add(dealerIdObj);
            res.put("dealerIds", single);
            res.put("scope", "SELF");
        } else if ("sales".equals(role) && salesUidObj != null) {
            Long salesUid = ((Number) salesUidObj).longValue();
            Set<Long> allSales = SalesOrgResolver.recursiveSubordinates(em, tid, salesUid);
            if (allSales.isEmpty()) {
                res.put("dealerIds", Collections.emptyList());
            } else {
                var q = em.createNativeQuery(
                        "SELECT dealer_id FROM sales_dealer_mapping " +
                        "WHERE tenant_id = ?1 AND sales_user_id = ANY(?2)");
                q.setParameter(1, tid);
                q.setParameter(2, allSales.toArray(new Long[0]));
                res.put("dealerIds", q.getResultList());
            }
            res.put("scope", "SALES_TREE");
            res.put("salesUserId", salesUid);
            res.put("subordinateCount", allSales.size());
        } else {
            res.put("dealerIds", Collections.emptyList());
            res.put("scope", "NONE");
        }
        return ApiResponse.ok(res);
    }
}
