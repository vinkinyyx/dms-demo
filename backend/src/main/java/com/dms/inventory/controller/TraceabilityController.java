/*
 * UDI 追溯时间轴接口 - US-B-22
 */
package com.dms.inventory.controller;

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
@RequestMapping("/api/traceability")
@RequiredArgsConstructor
public class TraceabilityController {

    private final EntityManager em;

    @GetMapping("/by-serial")
    public ApiResponse<Map<String, Object>> bySerial(@RequestParam String serialNo) {
        UUID tenantId = TenantContext.getTenantId();
        List<Map<String, Object>> events = new ArrayList<>();

        addSafely(events, () -> queryReceipts(serialNo, tenantId));
        addSafely(events, () -> querySalesOut(serialNo, tenantId));
        addSafely(events, () -> queryInvTxn(serialNo, tenantId));

        events.sort(Comparator.comparing(m -> String.valueOf(m.getOrDefault("at", ""))));

        Map<String, Object> current = queryCurrent(serialNo, tenantId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serialNo", serialNo);
        data.put("currentStock", current);
        data.put("events", events);
        data.put("eventsCount", events.size());
        return ApiResponse.ok(data);
    }

    @GetMapping("/by-batch")
    public ApiResponse<Map<String, Object>> byBatch(@RequestParam String batchNo) {
        UUID tenantId = TenantContext.getTenantId();
        List<Map<String, Object>> events = new ArrayList<>();
        addSafely(events, () -> queryInvTxnByBatch(batchNo, tenantId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchNo", batchNo);
        data.put("events", events);
        data.put("eventsCount", events.size());
        return ApiResponse.ok(data);
    }

    // ============ 独立事务的查询方法（单个失败不影响整体）============

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Map<String, Object>> queryReceipts(String sn, UUID tenantId) {
        var q = em.createNativeQuery(
                "SELECT CAST(r.arrived_at AS varchar) AS ev_at, 'RECEIPT' AS event, rl.receipt_id AS ref_id, " +
                " r.code AS ref_code, rl.qty AS qty, rl.batch_no AS batch_no, rl.product_id AS product_id, " +
                " rl.warehouse_id AS wh_id " +
                "FROM receipt_lines rl JOIN receipts r ON r.id = rl.receipt_id " +
                "WHERE r.tenant_id = ?1 AND rl.serial_no = ?2 ORDER BY r.arrived_at",
                Tuple.class);
        q.setParameter(1, tenantId).setParameter(2, sn);
        return toEventList(q.getResultList());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Map<String, Object>> querySalesOut(String sn, UUID tenantId) {
        var q = em.createNativeQuery(
                "SELECT CAST(s.sales_date AS varchar) AS ev_at, 'SALES_OUT' AS event, s.id AS ref_id, " +
                " s.code AS ref_code, sl.qty AS qty, sl.batch_no AS batch_no, sl.product_id AS product_id, " +
                " CAST(NULL AS bigint) AS wh_id " +
                "FROM sales_out_lines sl JOIN sales_outs s ON s.id = sl.sales_out_id " +
                "WHERE s.tenant_id = ?1 AND sl.serial_no = ?2 ORDER BY s.sales_date",
                Tuple.class);
        q.setParameter(1, tenantId).setParameter(2, sn);
        return toEventList(q.getResultList());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Map<String, Object>> queryInvTxn(String sn, UUID tenantId) {
        var q = em.createNativeQuery(
                "SELECT CAST(at_time AS varchar) AS ev_at, txn_type AS event, ref_doc_id AS ref_id, " +
                " CAST(ref_doc_type AS varchar) AS ref_code, qty_change AS qty, batch_no, product_id, " +
                " warehouse_id AS wh_id " +
                "FROM inventory_transactions " +
                "WHERE tenant_id = ?1 AND serial_no = ?2 ORDER BY at_time",
                Tuple.class);
        q.setParameter(1, tenantId).setParameter(2, sn);
        return toEventList(q.getResultList());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Map<String, Object>> queryInvTxnByBatch(String batchNo, UUID tenantId) {
        var q = em.createNativeQuery(
                "SELECT CAST(at_time AS varchar) AS ev_at, txn_type AS event, ref_doc_id AS ref_id, " +
                " CAST(ref_doc_type AS varchar) AS ref_code, qty_change AS qty, batch_no, product_id, " +
                " warehouse_id AS wh_id, serial_no " +
                "FROM inventory_transactions " +
                "WHERE tenant_id = ?1 AND batch_no = ?2 ORDER BY at_time LIMIT 200",
                Tuple.class);
        q.setParameter(1, tenantId).setParameter(2, batchNo);
        List<Map<String, Object>> list = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", t.get("ev_at"));
            m.put("event", t.get("event"));
            m.put("refId", t.get("ref_id"));
            m.put("qty", t.get("qty"));
            m.put("batchNo", t.get("batch_no"));
            m.put("productId", t.get("product_id"));
            m.put("warehouseId", t.get("wh_id"));
            m.put("serialNo", t.get("serial_no"));
            list.add(m);
        }
        return list;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<String, Object> queryCurrent(String sn, UUID tenantId) {
        try {
            var q = em.createNativeQuery(
                    "SELECT warehouse_id, dealer_id, qty, batch_no, CAST(exp_date AS varchar) AS exp_date " +
                    "FROM inventory WHERE tenant_id = ?1 AND serial_no = ?2 LIMIT 1",
                    Tuple.class);
            q.setParameter(1, tenantId).setParameter(2, sn);
            @SuppressWarnings("unchecked")
            List<Tuple> list = q.getResultList();
            if (list.isEmpty()) return null;
            Tuple t = list.get(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("warehouseId", t.get("warehouse_id"));
            m.put("dealerId", t.get("dealer_id"));
            m.put("qty", t.get("qty"));
            m.put("batchNo", t.get("batch_no"));
            m.put("expDate", t.get("exp_date"));
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toEventList(List rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object o : rows) {
            Tuple t = (Tuple) o;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", t.get("ev_at"));
            m.put("event", t.get("event"));
            m.put("refId", t.get("ref_id"));
            try { m.put("refCode", t.get("ref_code")); } catch (Exception ignored) {}
            m.put("qty", t.get("qty"));
            m.put("batchNo", t.get("batch_no"));
            m.put("productId", t.get("product_id"));
            m.put("warehouseId", t.get("wh_id"));
            list.add(m);
        }
        return list;
    }

    private void addSafely(List<Map<String, Object>> target, java.util.function.Supplier<List<Map<String, Object>>> supplier) {
        try { target.addAll(supplier.get()); } catch (Exception ignored) {}
    }
}
