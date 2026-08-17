package com.dms.inventory.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.PagingUtil;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * 批次效期预警（NEW-13）：查询近效期 / 已过期库存批次。
 *
 * <p>GET /api/inventory/expiry-alerts?withinDays=90  指定天数内到期
 * <p>GET /api/inventory/expiry-summary             首页提醒统计
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class ExpiryAlertController {

    private final EntityManager em;

    @GetMapping("/expiry-alerts")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> alerts(
            @RequestParam(defaultValue = "90") int withinDays,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String keyword) {
        UUID tid = TenantContext.getTenantId();
        if (withinDays < 0) withinDays = 0;
        if (withinDays > 3650) withinDays = 3650;
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(withinDays);

        StringBuilder where = new StringBuilder(" FROM inventory i "
                + "LEFT JOIN products p ON p.id = i.product_id "
                + "LEFT JOIN warehouses w ON w.id = i.warehouse_id "
                + "WHERE i.tenant_id = ?1 AND i.qty > 0 AND i.exp_date IS NOT NULL "
                + "AND i.exp_date <= ?2 ");
        List<Object> params = new ArrayList<>();
        params.add(tid); params.add(deadline);
        int idx = 3;
        if (warehouseId != null) { where.append(" AND i.warehouse_id = ?").append(idx++); params.add(warehouseId); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.name_cn ILIKE ?").append(idx++)
                 .append(" OR p.code ILIKE ?").append(idx++).append(" OR i.batch_no ILIKE ?").append(idx++).append(")");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw); params.add(kw); params.add(kw);
        }

        var cnt = em.createNativeQuery("SELECT COUNT(*) " + where);
        for (int i = 0; i < params.size(); i++) cnt.setParameter(i + 1, params.get(i));
        long total = ((Number) cnt.getSingleResult()).longValue();

        int safePage = PagingUtil.normalizePage(page);
        int safeSize = PagingUtil.normalizeSize(size);
        int offset = (safePage - 1) * safeSize;
        String limitParam = "?" + (idx++), offsetParam = "?" + (idx++);

        var q = em.createNativeQuery(
                "SELECT i.id, i.product_id, p.code AS product_code, p.name_cn AS product_name, p.spec AS spec, p.unit AS unit, "
                + "i.batch_no, i.serial_no, i.qty, i.exp_date, i.warehouse_id, w.name AS warehouse_name, "
                + "(i.exp_date - DATE '" + today + "') AS days_to_expiry "
                + where
                + " ORDER BY i.exp_date ASC, i.id DESC LIMIT " + limitParam + " OFFSET " + offsetParam, Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(params.size() + 1, safeSize);
        q.setParameter(params.size() + 2, offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("productId", t.get("product_id"));
            m.put("productCode", t.get("product_code"));
            m.put("productName", t.get("product_name"));
            m.put("spec", t.get("spec"));
            m.put("unit", t.get("unit"));
            m.put("batchNo", t.get("batch_no"));
            m.put("serialNo", t.get("serial_no"));
            m.put("qty", t.get("qty"));
            Object exp = t.get("exp_date");
            m.put("expDate", exp == null ? null : String.valueOf(exp));
            m.put("warehouseId", t.get("warehouse_id"));
            m.put("warehouseName", t.get("warehouse_name"));
            Object dte = t.get("days_to_expiry");
            long days = dte == null ? 0 : ((Number) dte).longValue();
            m.put("daysToExpiry", days);
            m.put("expired", days < 0);
            m.put("level", days < 0 ? "expired" : (days <= 30 ? "danger" : (days <= 90 ? "warning" : "info")));
            list.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total); data.put("page", safePage); data.put("size", safeSize); data.put("list", list);
        data.put("withinDays", withinDays);
        return ApiResponse.ok(data);
    }

    @GetMapping("/expiry-summary")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> summary() {
        UUID tid = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expired", countBy(tid, today, true));
        data.put("within30", countWithin(tid, today, 30));
        data.put("within90", countWithin(tid, today, 90));
        data.put("within180", countWithin(tid, today, 180));
        return ApiResponse.ok(data);
    }

    private long countBy(UUID tid, LocalDate today, boolean expired) {
        String sql = expired
                ? "SELECT COUNT(*) FROM inventory WHERE tenant_id=?1 AND qty>0 AND exp_date IS NOT NULL AND exp_date < ?2"
                : "SELECT COUNT(*) FROM inventory WHERE tenant_id=?1 AND qty>0 AND exp_date IS NOT NULL AND exp_date >= ?2";
        var q = em.createNativeQuery(sql);
        q.setParameter(1, tid); q.setParameter(2, today);
        return ((Number) q.getSingleResult()).longValue();
    }

    private long countWithin(UUID tid, LocalDate today, int days) {
        var q = em.createNativeQuery(
                "SELECT COUNT(*) FROM inventory WHERE tenant_id=?1 AND qty>0 AND exp_date IS NOT NULL "
                + "AND exp_date >= ?2 AND exp_date <= ?3");
        q.setParameter(1, tid); q.setParameter(2, today); q.setParameter(3, today.plusDays(days));
        return ((Number) q.getSingleResult()).longValue();
    }
}