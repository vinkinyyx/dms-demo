/*
 * 单据列表增强 Controller (v3.4.6)
 * 覆盖 /api/sales-outs 和 /api/receipts 的 GET，用 native SQL 返回：
 *   - dealerName / warehouseName / sourceOrderCode / sourcePoCode
 *   - autoCreated / sourceOrderId / sourcePoId
 * 使得前端表格可以显示来源单链接。
 */
package com.dms.execution.controller;

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
@RequiredArgsConstructor
public class BizDocListController {

    private final EntityManager em;

    /**
     * v3.4.6：销售出库列表增强
     * GET /api/sales-outs 覆盖原实现，返回丰富字段
     * 支持 filter: id=x, dealerId=x, sourceOrderId=x
     */
    @GetMapping("/api/sales-outs")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> listSalesOuts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) Long sourceOrderId,
            @RequestParam(required = false) String status) {
        UUID tid = TenantContext.getTenantId();
        int offset = (page - 1) * size;

        StringBuilder where = new StringBuilder("WHERE so.tenant_id = ?1");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (id != null) { where.append(" AND so.id = ?").append(idx++); params.add(id); }
        if (dealerId != null) { where.append(" AND so.dealer_id = ?").append(idx++); params.add(dealerId); }
        if (sourceOrderId != null) { where.append(" AND so.source_order_id = ?").append(idx++); params.add(sourceOrderId); }
        if (status != null && !status.isBlank()) { where.append(" AND so.status = ?").append(idx++); params.add(status); }

        var cntQ = em.createNativeQuery("SELECT COUNT(*) FROM sales_outs so " + where);
        for (int i = 0; i < params.size(); i++) cntQ.setParameter(i + 1, params.get(i));
        long total = ((Number) cntQ.getSingleResult()).longValue();

        String sql = "SELECT so.id, so.code, so.is_red, so.dealer_id, so.terminal_id, so.sales_date, " +
                "so.amount_incl_tax, so.status, so.auto_created, so.source_order_id, " +
                "so.created_at, so.updated_at, " +
                "d.name AS dealer_name, h.name AS terminal_name, " +
                "o.code AS source_order_code " +
                "FROM sales_outs so " +
                "LEFT JOIN dealers d ON d.id = so.dealer_id " +
                "LEFT JOIN hospitals h ON h.id = so.terminal_id " +
                "LEFT JOIN orders o ON o.id = so.source_order_id " +
                where +
                " ORDER BY so.updated_at DESC NULLS LAST, so.id DESC LIMIT ?" + idx + " OFFSET ?" + (idx + 1);
        var q = em.createNativeQuery(sql, Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(idx, size);
        q.setParameter(idx + 1, offset);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("isRed", t.get("is_red"));
            m.put("dealerId", t.get("dealer_id"));
            m.put("dealerName", t.get("dealer_name"));
            m.put("terminalId", t.get("terminal_id"));
            m.put("terminalName", t.get("terminal_name"));
            m.put("salesDate", com.dms.common.util.DateFmt.fmt(t.get("sales_date")));
            m.put("amountInclTax", t.get("amount_incl_tax"));
            m.put("status", t.get("status"));
            m.put("autoCreated", t.get("auto_created"));
            m.put("sourceOrderId", t.get("source_order_id"));
            m.put("sourceOrderCode", t.get("source_order_code"));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("list", list);
        return ApiResponse.ok(data);
    }

    /**
     * v3.4.6：收货入库列表增强
     */
    @GetMapping("/api/receipts")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> listReceipts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long sourcePoId,
            @RequestParam(required = false) String status) {
        UUID tid = TenantContext.getTenantId();
        int offset = (page - 1) * size;

        StringBuilder where = new StringBuilder("WHERE r.tenant_id = ?1");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (id != null) { where.append(" AND r.id = ?").append(idx++); params.add(id); }
        if (warehouseId != null) { where.append(" AND r.warehouse_id = ?").append(idx++); params.add(warehouseId); }
        if (sourcePoId != null) { where.append(" AND r.source_po_id = ?").append(idx++); params.add(sourcePoId); }
        if (status != null && !status.isBlank()) { where.append(" AND r.status = ?").append(idx++); params.add(status); }

        var cntQ = em.createNativeQuery("SELECT COUNT(*) FROM receipts r " + where);
        for (int i = 0; i < params.size(); i++) cntQ.setParameter(i + 1, params.get(i));
        long total = ((Number) cntQ.getSingleResult()).longValue();

        String sql = "SELECT r.id, r.code, r.is_red, r.warehouse_id, r.ref_doc_type, r.ref_doc_id, " +
                "r.status, r.auto_created, r.source_po_id, r.received_at, " +
                "r.created_at, r.updated_at, " +
                "w.name AS warehouse_name, po.code AS source_po_code " +
                "FROM receipts r " +
                "LEFT JOIN warehouses w ON w.id = r.warehouse_id " +
                "LEFT JOIN purchase_orders po ON po.id = r.source_po_id " +
                where +
                " ORDER BY r.updated_at DESC NULLS LAST, r.id DESC LIMIT ?" + idx + " OFFSET ?" + (idx + 1);
        var q = em.createNativeQuery(sql, Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(idx, size);
        q.setParameter(idx + 1, offset);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("isRed", t.get("is_red"));
            m.put("warehouseId", t.get("warehouse_id"));
            m.put("warehouseName", t.get("warehouse_name"));
            m.put("refDocType", t.get("ref_doc_type"));
            m.put("refDocId", t.get("ref_doc_id"));
            m.put("status", t.get("status"));
            m.put("autoCreated", t.get("auto_created"));
            m.put("sourcePoId", t.get("source_po_id"));
            m.put("sourcePoCode", t.get("source_po_code"));
            m.put("receivedAt", com.dms.common.util.DateFmt.fmt(t.get("received_at")));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("list", list);
        return ApiResponse.ok(data);
    }
}
