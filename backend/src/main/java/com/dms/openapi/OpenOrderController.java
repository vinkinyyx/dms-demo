package com.dms.openapi;

import com.dms.common.ApiResponse;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 对外开放接口（v3.8.3）—— 供外部系统调用 DMS 创建订单。
 *
 * <p>路径前缀 /open/api，由 {@link OpenApiAuthFilter} 做 HMAC 鉴权并写入租户上下文。
 * <ul>
 *   <li>POST /open/api/sales-orders    创建销售订单（草稿）</li>
 *   <li>POST /open/api/purchase-orders 创建采购订单（草稿）</li>
 * </ul>
 * 所有主数据均使用 code 传参（dealerCode/supplierCode/warehouseCode/productCode），
 * 由 DMS 解析为内部 ID。
 */
@Slf4j
@RestController
@RequestMapping("/open/api")
@RequiredArgsConstructor
public class OpenOrderController {

    private final EntityManager em;
    private final DocNoGenerator docNoGenerator;

    /** 创建销售订单 */
    @PostMapping("/sales-orders")
    @Transactional
    public ApiResponse<Map<String, Object>> createSalesOrder(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) return ApiResponse.fail(40100, "未识别的租户");

        String dealerCode = str(body.get("dealerCode"));
        String warehouseCode = str(body.get("warehouseCode"));
        if (dealerCode == null) return ApiResponse.fail(40001, "dealerCode 不能为空");
        if (warehouseCode == null) return ApiResponse.fail(40001, "warehouseCode 不能为空");

        Long dealerId = lookupId("dealers", "code", dealerCode, tid);
        if (dealerId == null) return ApiResponse.fail(40401, "经销商不存在: " + dealerCode);
        Long warehouseId = lookupWarehouse(warehouseCode, tid);
        if (warehouseId == null) return ApiResponse.fail(40401, "仓库不存在: " + warehouseCode);

        List<Map<String, Object>> lines = lines(body);
        if (lines.isEmpty()) return ApiResponse.fail(40001, "明细不能为空");

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> l : lines) total = total.add(bd(l.get("qty")).multiply(bd(l.get("unitPrice"))));

        String code = docNoGenerator.next("SO");
        var ins = em.createNativeQuery(
                "INSERT INTO orders (tenant_id, code, order_type, dealer_id, warehouse_id, ship_snapshot, " +
                "amount_incl_tax, discount_amount, final_amount, tax_amount, expected_date, status, remark, extra, " +
                "created_at, updated_at) " +
                "VALUES (?1,?2,?3,?4,?5,CAST(?6 AS jsonb),?7,0,?7,0,CAST(?8 AS date),'DRAFT',?9,CAST(?10 AS jsonb),now(),now()) " +
                "RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code)
           .setParameter(3, strOr(body.get("orderType"), "NORMAL"))
           .setParameter(4, dealerId).setParameter(5, warehouseId)
           .setParameter(6, "{\"dealerName\":\"" + safe(str(body.get("dealerName"))) + "\"}")
           .setParameter(7, total).setParameter(8, body.get("expectedDate"))
           .setParameter(9, strOr(body.get("remark"), "")).setParameter(10, extra(body))
           ;
        Long id = ((Number) ins.getSingleResult()).longValue();
        insertSalesLines(id, lines);

        log.info("[OPEN-API] 创建销售订单 id={} code={} app={}", id, code, TenantContext.get("appKey"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("code", code); res.put("status", "DRAFT");
        return ApiResponse.ok(res);
    }

    /** 创建采购订单 */
    @PostMapping("/purchase-orders")
    @Transactional
    public ApiResponse<Map<String, Object>> createPurchaseOrder(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) return ApiResponse.fail(40100, "未识别的租户");

        String supplierCode = str(body.get("supplierCode"));
        String warehouseCode = str(body.get("warehouseCode"));
        if (supplierCode == null) return ApiResponse.fail(40001, "supplierCode 不能为空");
        if (warehouseCode == null) return ApiResponse.fail(40001, "warehouseCode 不能为空");

        Long supplierId = lookupId("suppliers", "code", supplierCode, tid);
        if (supplierId == null) return ApiResponse.fail(40401, "供应商不存在: " + supplierCode);
        Long warehouseId = lookupId("warehouses", "code", warehouseCode, tid);
        if (warehouseId == null) return ApiResponse.fail(40401, "仓库不存在: " + warehouseCode);

        List<Map<String, Object>> lines = lines(body);
        if (lines.isEmpty()) return ApiResponse.fail(40001, "明细不能为空");

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> l : lines) total = total.add(bd(l.get("qty")).multiply(bd(l.get("unitPrice"))));

        String code = docNoGenerator.next("PO");
        var ins = em.createNativeQuery(
                "INSERT INTO purchase_orders (tenant_id, code, order_type, supplier_id, supplier_name, warehouse_id, is_red, " +
                "amount_incl_tax, final_amount, expected_date, status, remark, extra, created_at, updated_at) " +
                "VALUES (?1,?2,?3,?4,?5,?6,false,?7,?7,CAST(?8 AS date),'DRAFT',?9,CAST(?10 AS jsonb),now(),now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code)
           .setParameter(3, strOr(body.get("orderType"), "NORMAL"))
           .setParameter(4, supplierId).setParameter(5, strOr(body.get("supplierName"), ""))
           .setParameter(6, warehouseId).setParameter(7, total)
           .setParameter(8, body.get("expectedDate"))
           .setParameter(9, strOr(body.get("remark"), "")).setParameter(10, extra(body))
           ;
        Long id = ((Number) ins.getSingleResult()).longValue();
        insertPurchaseLines(id, lines);

        log.info("[OPEN-API] 创建采购订单 id={} code={} app={}", id, code, TenantContext.get("appKey"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id); res.put("code", code); res.put("status", "DRAFT");
        return ApiResponse.ok(res);
    }

    // ---------------- helpers ----------------
    private void insertSalesLines(Long orderId, List<Map<String, Object>> lines) {
        int seq = 1;
        for (Map<String, Object> l : lines) {
            Long pid = resolveProduct(l);
            if (pid == null) throw new IllegalArgumentException("productCode 不存在: " + l.get("productCode"));
            BigDecimal qty = bd(l.get("qty")), price = bd(l.get("unitPrice")), tax = bd(l.get("taxRate"));
            if (tax.signum() == 0) tax = new BigDecimal("0.13");
            em.createNativeQuery(
                    "INSERT INTO order_lines (order_id, seq, product_id, qty, unit_price, tax_rate, sub_total, is_gift, created_at, updated_at) " +
                    "VALUES (?1,?2,?3,?4,?5,?6,?7,?8,now(),now())")
              .setParameter(1, orderId).setParameter(2, seq++).setParameter(3, pid)
              .setParameter(4, qty).setParameter(5, price).setParameter(6, tax)
              .setParameter(7, qty.multiply(price)).setParameter(8, Boolean.TRUE.equals(l.get("isGift")))
              .executeUpdate();
        }
    }

    private void insertPurchaseLines(Long poId, List<Map<String, Object>> lines) {
        int seq = 1;
        for (Map<String, Object> l : lines) {
            Long pid = resolveProduct(l);
            if (pid == null) throw new IllegalArgumentException("productCode 不存在: " + l.get("productCode"));
            BigDecimal qty = bd(l.get("qty")), price = bd(l.get("unitPrice")), tax = bd(l.get("taxRate"));
            if (tax.signum() == 0) tax = new BigDecimal("0.13");
            em.createNativeQuery(
                    "INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, unit_price, tax_rate, subtotal, created_at) " +
                    "VALUES (?1,?2,?3,?4,?5,?6,?7,now())")
              .setParameter(1, poId).setParameter(2, seq++).setParameter(3, pid)
              .setParameter(4, qty).setParameter(5, price).setParameter(6, tax)
              .setParameter(7, qty.multiply(price))
              .executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lines(Map<String, Object> body) {
        Object o = body.get("lines");
        return o instanceof List ? (List<Map<String, Object>>) o : Collections.emptyList();
    }

    private Long resolveProduct(Map<String, Object> l) {
        UUID tid = TenantContext.getTenantId();
        Object pid = l.get("productId");
        if (pid != null) {
            try { return Long.valueOf(String.valueOf(pid)); } catch (Exception ignored) {}
        }
        String code = str(l.get("productCode"));
        if (code == null) return null;
        return lookupId("products", "code", code, tid);
    }

    private Long lookupId(String table, String col, String code, UUID tid) {
        var q = em.createNativeQuery("SELECT id FROM " + table + " WHERE tenant_id=?1 AND " + col + "=?2 LIMIT 1");
        q.setParameter(1, tid).setParameter(2, code);
        List<?> rs = q.getResultList();
        return rs.isEmpty() ? null : ((Number) rs.get(0)).longValue();
    }

    private Long lookupWarehouse(String code, UUID tid) {
        var q = em.createNativeQuery("SELECT id FROM warehouses WHERE tenant_id=?1 AND code=?2 LIMIT 1");
        q.setParameter(1, tid).setParameter(2, code);
        List<?> rs = q.getResultList();
        return rs.isEmpty() ? null : ((Number) rs.get(0)).longValue();
    }

    private String extra(Map<String, Object> body) {
        Object e = body.get("extra");
        if (e == null) return "{}";
        if (e instanceof String) return (String) e;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(e); }
        catch (Exception ex) { return "{}"; }
    }

    private BigDecimal bd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return BigDecimal.ZERO; }
    }
    private String str(Object o) { if (o == null) return null; String s = String.valueOf(o).trim(); return s.isEmpty() ? null : s; }
    private String strOr(Object o, String def) { String s = str(o); return s == null ? def : s; }
    private String safe(String s) { return s == null ? "" : s.replace("\"", ""); }
}