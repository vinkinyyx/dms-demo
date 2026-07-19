/*
 * 销售岗位递归解析工具
 */
package com.dms.org.controller;

import jakarta.persistence.EntityManager;

import java.util.*;

public final class PositionResolver {
    private PositionResolver() {}

    /**
     * 递归查询某个岗位的所有下级岗位（含自己）
     */
    public static Set<Long> recursivePositions(EntityManager em, UUID tid, Long rootId) {
        Set<Long> result = new LinkedHashSet<>();
        if (rootId == null) return result;
        result.add(rootId);
        Deque<Long> queue = new ArrayDeque<>();
        queue.push(rootId);
        int guard = 10000;
        while (!queue.isEmpty() && guard-- > 0) {
            Long cur = queue.pop();
            var q = em.createNativeQuery(
                    "SELECT id FROM sales_positions WHERE tenant_id = ?1 AND parent_id = ?2 AND deleted_at IS NULL");
            q.setParameter(1, tid).setParameter(2, cur);
            @SuppressWarnings("unchecked")
            List<Object> childIds = q.getResultList();
            for (Object o : childIds) {
                Long id = ((Number) o).longValue();
                if (result.add(id)) queue.push(id);
            }
        }
        return result;
    }

    /**
     * 根据用户身份计算可访问经销商 ID 集合
     * 返回 null → admin 全部
     */
    public static Set<Long> resolveAccessibleDealers(EntityManager em, UUID tid, Long userId) {
        if (userId == null) return null;
        var q = em.createNativeQuery(
                "SELECT role, sales_position_id, dealer_id FROM users WHERE id = ?1",
                jakarta.persistence.Tuple.class);
        q.setParameter(1, userId);
        List<?> ls = q.getResultList();
        if (ls.isEmpty()) return new HashSet<>();
        jakarta.persistence.Tuple t = (jakarta.persistence.Tuple) ls.get(0);
        String role = String.valueOf(t.get("role"));
        if ("admin".equals(role)) return null;

        Set<Long> dealerIds = new HashSet<>();
        if ("dealer".equals(role)) {
            Object d = t.get("dealer_id");
            if (d != null) dealerIds.add(((Number) d).longValue());
            return dealerIds;
        }
        // sales
        Object pos = t.get("sales_position_id");
        if (pos == null) return dealerIds;
        Long positionId = ((Number) pos).longValue();
        Set<Long> allPos = recursivePositions(em, tid, positionId);
        if (allPos.isEmpty()) return dealerIds;

        var dq = em.createNativeQuery(
                "SELECT id FROM dealers WHERE tenant_id = ?1 AND sales_position_id = ANY(?2)");
        dq.setParameter(1, tid);
        dq.setParameter(2, allPos.toArray(new Long[0]));
        @SuppressWarnings("unchecked")
        List<Object> ids = dq.getResultList();
        for (Object o : ids) if (o != null) dealerIds.add(((Number) o).longValue());
        return dealerIds;
    }
}
