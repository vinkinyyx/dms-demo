/*
 * 首页工作台服务：聚合 KPI + 6 月趋势 + 状态分布 + 待办 + 公告 + 未读消息数。
 */
package com.dms.home.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 主入口：返回工作台聚合数据。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(UUID tenantId, Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("kpi", kpi(tenantId));
        result.put("salesTrend", salesTrend6Month(tenantId));
        result.put("orderStatusDist", orderStatusDist(tenantId));
        result.put("todoCount", todoCount(userId));
        result.put("notices", notices(tenantId));
        result.put("unreadCount", unreadCount(userId));
        return result;
    }

    private Map<String, Object> kpi(UUID tenantId) {
        Map<String, Object> m = new HashMap<>();
        m.put("orderCount", scalar("SELECT COUNT(*) FROM orders WHERE deleted_at IS NULL AND tenant_id=:t", tenantId));
        m.put("salesAmount", scalar("SELECT COALESCE(SUM(amount),0) FROM sales_out_facts WHERE tenant_id=:t", tenantId));
        m.put("inventoryQty", scalar("SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id=:t", tenantId));
        m.put("dealerCount", scalar("SELECT COUNT(*) FROM dealers WHERE deleted_at IS NULL AND tenant_id=:t", tenantId));
        return m;
    }

    /**
     * 6 个月销售趋势：以当前月往前推 5 个月，一共 6 个数据点。
     */
    private List<Map<String, Object>> salesTrend6Month(UUID tenantId) {
        LocalDate now = LocalDate.now().withDayOfMonth(1);
        List<String> periods = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            periods.add(now.minusMonths(i).format(DateTimeFormatter.ofPattern("yyyy-MM")));
        }
        String sql = "SELECT to_char(sales_date,'YYYY-MM') AS period, " +
                "        COALESCE(SUM(qty),0) AS qty, COALESCE(SUM(amount),0) AS amount " +
                " FROM sales_out_facts " +
                " WHERE tenant_id=:t AND sales_date >= :start " +
                " GROUP BY period ORDER BY period";
        Query q = entityManager.createNativeQuery(sql, Tuple.class);
        q.setParameter("t", tenantId);
        q.setParameter("start", now.minusMonths(5));
        @SuppressWarnings("unchecked")
        List<Tuple> tuples = q.getResultList();
        Map<String, Tuple> byPeriod = new HashMap<>();
        for (Tuple t : tuples) byPeriod.put((String) t.get("period"), t);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String p : periods) {
            Map<String, Object> row = new HashMap<>();
            row.put("period", p);
            Tuple t = byPeriod.get(p);
            row.put("qty", t == null ? 0 : t.get("qty"));
            row.put("amount", t == null ? 0 : t.get("amount"));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> orderStatusDist(UUID tenantId) {
        return list("SELECT status, COUNT(*) AS cnt FROM orders " +
                "  WHERE deleted_at IS NULL AND tenant_id=:t GROUP BY status", tenantId);
    }

    private Object todoCount(Long userId) {
        if (userId == null) return 0;
        Query q = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM approval_tasks WHERE assignee_id=:u AND status='PENDING'");
        q.setParameter("u", userId);
        return q.getSingleResult();
    }

    private List<Map<String, Object>> notices(UUID tenantId) {
        return list("SELECT id, title, created_at FROM notifications " +
                "  WHERE tenant_id=:t ORDER BY created_at DESC LIMIT 5", tenantId);
    }

    private Object unreadCount(Long userId) {
        if (userId == null) return 0;
        Query q = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM notifications WHERE user_id=:u AND is_read=false");
        q.setParameter("u", userId);
        return q.getSingleResult();
    }

    private Object scalar(String sql, UUID tenantId) {
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("t", tenantId);
        return q.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(String sql, UUID tenantId) {
        Query q = entityManager.createNativeQuery(sql, Tuple.class);
        q.setParameter("t", tenantId);
        List<Tuple> tuples = q.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            Map<String, Object> row = new HashMap<>();
            t.getElements().forEach(e -> row.put(e.getAlias(), t.get(e.getAlias())));
            rows.add(row);
        }
        return rows;
    }
}
