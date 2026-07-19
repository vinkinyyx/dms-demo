/*
 * 销售组织架构工具类：递归下级查询 + 权限数据过滤
 */
package com.dms.org.controller;

import jakarta.persistence.EntityManager;

import java.util.*;

public final class SalesOrgResolver {

    private SalesOrgResolver() {}

    /**
     * 递归查询某个 sales_user 的所有下级（含自己）
     */
    public static Set<Long> recursiveSubordinates(EntityManager em, UUID tid, Long rootUserId) {
        Set<Long> result = new LinkedHashSet<>();
        if (rootUserId == null) return result;
        result.add(rootUserId);
        Deque<Long> queue = new ArrayDeque<>();
        queue.push(rootUserId);
        int guard = 10000;
        while (!queue.isEmpty() && guard-- > 0) {
            Long cur = queue.pop();
            var q = em.createNativeQuery(
                    "SELECT id FROM sales_users WHERE tenant_id = ?1 AND parent_id = ?2 AND deleted_at IS NULL");
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
     * 根据当前登录用户的 role/sales_user_id/dealer_id，计算他可访问的 dealer_id 集合
     * 返回 null 表示"全部可访问"（admin）
     */
    public static Set<Long> resolveAccessibleDealerIds(EntityManager em, UUID tid,
                                                        String role, Long salesUserId, Long dealerId) {
        if ("admin".equals(role)) return null; // 全部
        Set<Long> dealerIds = new HashSet<>();
        if ("dealer".equals(role) && dealerId != null) {
            dealerIds.add(dealerId);
            return dealerIds;
        }
        if ("sales".equals(role) && salesUserId != null) {
            Set<Long> allSales = recursiveSubordinates(em, tid, salesUserId);
            if (allSales.isEmpty()) return dealerIds;
            var q = em.createNativeQuery(
                    "SELECT dealer_id FROM sales_dealer_mapping " +
                    "WHERE tenant_id = ?1 AND sales_user_id = ANY(?2)");
            q.setParameter(1, tid);
            q.setParameter(2, allSales.toArray(new Long[0]));
            @SuppressWarnings("unchecked")
            List<Object> rows = q.getResultList();
            for (Object r : rows) if (r != null) dealerIds.add(((Number) r).longValue());
        }
        return dealerIds;
    }
}
