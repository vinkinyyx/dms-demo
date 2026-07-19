/*
 * 库存调整增强 Controller (v3.1)
 *   category=IN  → 增加 QUALIFIED 库存 (盘盈/数据修正)
 *   category=OUT → 扣减 QUALIFIED 库存 (盘亏/报损)
 *   type: STOCKTAKE/DAMAGE/CORRECT/OTHER
 *
 * 使用 /api/inventory-adj-ops 避免与已有 Controller 冲突
 */
package com.dms.inventory.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.inventory.service.InventoryStatusOps;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/inventory-adj-ops")
@RequiredArgsConstructor
public class InventoryAdjOpsController {

    private final EntityManager em;
    private final InventoryStatusOps inventoryOps;

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");

        Long warehouseId = toLong(body.get("warehouseId"));
        String category = String.valueOf(body.getOrDefault("category", "IN")).toUpperCase();
        String type = String.valueOf(body.getOrDefault("type", "STOCKTAKE"));
        String remark = strOr(body.get("remark"), "");
        String stockStatus = strOr(body.get("stockStatus"), "QUALIFIED");

        if (warehouseId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "仓库必填");
        if (!"IN".equals(category) && !"OUT".equals(category)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "category 必须为 IN 或 OUT");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "调整明细不能为空");
        }

        String code = "ADJ-" + System.currentTimeMillis();

        // 写主表 - 使用 native SQL 兼容不同的表结构
        Long adjId;
        try {
            var ins = em.createNativeQuery(
                    "INSERT INTO inventory_adjustments (tenant_id, code, warehouse_id, adj_category, adj_type, status, reason, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, 'COMPLETED', ?6, now(), now()) RETURNING id");
            ins.setParameter(1, tid).setParameter(2, code).setParameter(3, warehouseId)
                    .setParameter(4, category).setParameter(5, type).setParameter(6, remark);
            adjId = ((Number) ins.getSingleResult()).longValue();
        } catch (Exception e) {
            // 兼容旧表结构
            var ins = em.createNativeQuery(
                    "INSERT INTO inventory_adjustments (tenant_id, code, warehouse_id, adj_category, adj_type, status, updated_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, 'COMPLETED', now()) RETURNING id");
            ins.setParameter(1, tid).setParameter(2, code).setParameter(3, warehouseId)
                    .setParameter(4, category).setParameter(5, type);
            adjId = ((Number) ins.getSingleResult()).longValue();
        }

        int seq = 1;
        for (Map<String, Object> l : lines) {
            Long productId = toLong(l.get("productId"));
            BigDecimal qty = toBd(l.get("qty"));
            String batchNo = strOr(l.get("batchNo"), null);

            if (productId == null || qty == null || qty.signum() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "明细 productId/qty 必填且 > 0");
            }

            // 写明细
            try {
                em.createNativeQuery(
                        "INSERT INTO adjustment_lines (adjustment_id, product_id, batch_no, qty) " +
                        "VALUES (?1, ?2, ?3, ?4)")
                    .setParameter(1, adjId).setParameter(2, productId).setParameter(3, batchNo).setParameter(4, qty)
                    .executeUpdate();
            } catch (Exception ignored) {}

            // 库存变动
            BigDecimal delta = "IN".equals(category) ? qty : qty.negate();
            String txnType = "ADJ_" + category;
            inventoryOps.change(tid, productId, warehouseId, batchNo, delta, stockStatus, txnType, "adjustment", adjId);
            log.info("库存调整 code={} category={} product={} qty={}", code, category, productId, delta);
            seq++;
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", adjId);
        res.put("code", code);
        res.put("category", category);
        res.put("type", type);
        res.put("warehouseId", warehouseId);
        res.put("stockStatus", stockStatus);
        res.put("lineCount", lines.size());
        res.put("message", "库存调整完成: " + ("IN".equals(category) ? "已增加" : "已扣减") + " " + lines.size() + " 项 " + stockStatus + " 库存");
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
    private String strOr(Object o, String d) {
        if (o == null) return d;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? d : s;
    }
}
