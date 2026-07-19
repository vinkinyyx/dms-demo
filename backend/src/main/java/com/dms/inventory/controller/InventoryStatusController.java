/*
 * 库存状态与库存移动增强 Controller
 * 支持:
 *   1. 按产品/仓库/状态查询库存
 *   2. 库存状态迁移: 待检 -> 合格/不合格
 *   3. 收货入库/红字销售出库 -> 生成 PENDING 待检库存
 *   4. 销售出库 -> 只允许扣减 QUALIFIED 合格库存
 */
package com.dms.inventory.controller;

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

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory-status")
public class InventoryStatusController {

    private final EntityManager em;

    /**
     * 查询产品在指定状态下的库存汇总
     * GET /api/inventory-status/product/{productId}
     */
    @GetMapping("/product/{productId}")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> byProduct(@PathVariable Long productId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) Long warehouseId) {
        UUID tid = TenantContext.getTenantId();
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            String sql = "SELECT stock_status, COALESCE(SUM(qty),0) AS qty " +
                    "FROM inventory WHERE tenant_id = ?1 AND product_id = ?2";
            int paramIdx = 3;
            List<Object> params = new ArrayList<>();
            params.add(tid); params.add(productId);
            if (status != null && !status.isBlank()) {
                sql += " AND stock_status = ?" + paramIdx;
                params.add(status); paramIdx++;
            }
            if (warehouseId != null) {
                sql += " AND warehouse_id = ?" + paramIdx;
                params.add(warehouseId); paramIdx++;
            }
            sql += " GROUP BY stock_status";

            var q = em.createNativeQuery(sql, Tuple.class);
            for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();

            Map<String, BigDecimal> byStatus = new LinkedHashMap<>();
            byStatus.put("QUALIFIED", BigDecimal.ZERO);
            byStatus.put("PENDING", BigDecimal.ZERO);
            byStatus.put("DEFECTIVE", BigDecimal.ZERO);
            BigDecimal total = BigDecimal.ZERO;
            for (Tuple t : rows) {
                String s = String.valueOf(t.get("stock_status"));
                BigDecimal q2 = (BigDecimal) t.get("qty");
                byStatus.put(s, q2);
                total = total.add(q2);
            }
            res.put("productId", productId);
            res.put("total", total);
            res.put("qualified", byStatus.get("QUALIFIED"));
            res.put("pending", byStatus.get("PENDING"));
            res.put("defective", byStatus.get("DEFECTIVE"));
            res.put("availableForSale", byStatus.get("QUALIFIED")); // 下单可用 = 合格
        } catch (Exception e) {
            res.put("error", e.getMessage());
        }
        return ApiResponse.ok(res);
    }

    /**
     * 库存状态迁移
     * POST /api/inventory-status/move
     * body: { productId, warehouseId, qty, srcStatus, dstStatus, remark }
     * 场景: 待检 -> 合格/不合格
     */
    @PostMapping("/move")
    @Transactional
    public ApiResponse<Map<String, Object>> moveStatus(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long productId = toLong(body.get("productId"));
        Long warehouseId = toLong(body.get("warehouseId"));
        String batchNo = strOrNull(body.get("batchNo"));
        String srcStatus = String.valueOf(body.getOrDefault("srcStatus", "PENDING"));
        String dstStatus = String.valueOf(body.getOrDefault("dstStatus", "QUALIFIED"));
        BigDecimal qty = toBd(body.get("qty"));

        if (productId == null || warehouseId == null || qty == null || qty.signum() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "productId/warehouseId/qty 必填");
        }
        if (srcStatus.equals(dstStatus)) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "源状态不能等于目标状态");
        }

        // 1. 扣减源状态库存
        String srcSql = "UPDATE inventory SET qty = qty - ?1, updated_at = now() " +
                "WHERE tenant_id = ?2 AND product_id = ?3 AND warehouse_id = ?4 AND stock_status = ?5" +
                (batchNo != null ? " AND batch_no = ?6" : "") +
                " AND qty >= ?1";
        var srcQ = em.createNativeQuery(srcSql);
        srcQ.setParameter(1, qty).setParameter(2, tid).setParameter(3, productId)
                .setParameter(4, warehouseId).setParameter(5, srcStatus);
        if (batchNo != null) srcQ.setParameter(6, batchNo);
        int affected = srcQ.executeUpdate();
        if (affected == 0) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "源状态 [" + srcStatus + "] 库存不足");
        }

        // 2. 增加目标状态库存 (upsert)
        String bn = batchNo != null ? batchNo : "";
        String checkSql = "SELECT id FROM inventory WHERE tenant_id = ?1 AND product_id = ?2 " +
                "AND warehouse_id = ?3 AND stock_status = ?4 AND COALESCE(batch_no,'') = ?5 " +
                "AND COALESCE(serial_no,'') = '' LIMIT 1";
        var chk = em.createNativeQuery(checkSql);
        chk.setParameter(1, tid).setParameter(2, productId).setParameter(3, warehouseId)
                .setParameter(4, dstStatus).setParameter(5, bn);
        List<?> ids = chk.getResultList();
        if (ids.isEmpty()) {
            em.createNativeQuery(
                    "INSERT INTO inventory (tenant_id, warehouse_id, product_id, batch_no, qty, stock_status, in_source) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'STATUS_MOVE')")
              .setParameter(1, tid).setParameter(2, warehouseId).setParameter(3, productId)
              .setParameter(4, bn.isEmpty() ? null : bn).setParameter(5, qty).setParameter(6, dstStatus)
              .executeUpdate();
        } else {
            em.createNativeQuery(
                    "UPDATE inventory SET qty = qty + ?1, updated_at = now() WHERE id = ?2")
              .setParameter(1, qty).setParameter(2, ((Number) ids.get(0)).longValue())
              .executeUpdate();
        }

        // 3. 写事务日志
        try {
            em.createNativeQuery(
                    "INSERT INTO inventory_transactions (tenant_id, product_id, warehouse_id, txn_type, qty_change, ref_doc_type, ref_doc_id, at_time) " +
                    "VALUES (?1, ?2, ?3, 'STATUS_MOVE', ?4, 'STATUS_MOVE', 0, now())")
              .setParameter(1, tid).setParameter(2, productId).setParameter(3, warehouseId)
              .setParameter(4, qty)
              .executeUpdate();
        } catch (Exception ignored) {}

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("productId", productId);
        res.put("warehouseId", warehouseId);
        res.put("qty", qty);
        res.put("srcStatus", srcStatus);
        res.put("dstStatus", dstStatus);
        res.put("message", "库存状态已迁移: " + srcStatus + " → " + dstStatus);
        return ApiResponse.ok(res);
    }

    /**
     * 销售出库前的可用库存检查
     * GET /api/inventory-status/available?productId=X&warehouseId=Y
     * 返回 QUALIFIED 合格库存数量
     */
    @GetMapping("/available")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> available(@RequestParam Long productId,
                                                        @RequestParam(required = false) Long warehouseId) {
        UUID tid = TenantContext.getTenantId();
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            String sql = "SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id = ?1 " +
                    "AND product_id = ?2 AND stock_status = 'QUALIFIED'";
            var q = em.createNativeQuery(sql);
            q.setParameter(1, tid).setParameter(2, productId);
            if (warehouseId != null) {
                sql += " AND warehouse_id = ?3";
                q = em.createNativeQuery(sql);
                q.setParameter(1, tid).setParameter(2, productId).setParameter(3, warehouseId);
            }
            BigDecimal availableQty = (BigDecimal) q.getSingleResult();
            res.put("productId", productId);
            res.put("warehouseId", warehouseId);
            res.put("available", availableQty);
            res.put("canOrder", availableQty.signum() > 0);
        } catch (Exception e) {
            res.put("error", e.getMessage());
            res.put("available", BigDecimal.ZERO);
        }
        return ApiResponse.ok(res);
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private BigDecimal toBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private String strOrNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
