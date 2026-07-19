/*
 * 通用库存变动工具（支持指定 stock_status）
 * v3.1 新增:
 *   业务语义:
 *     - 采购入库 (PO isRed=false) : + PENDING (待检)
 *     - 红字采购入库(PO isRed=true) : - QUALIFIED (采退,退货给上游)
 *     - 销售出库 (SO isRed=false) : - QUALIFIED (合格发货)
 *     - 红字销售出库(SO isRed=true) : + PENDING  (销退退回,重新质检)
 *     - 库存调整 IN : + QUALIFIED
 *     - 库存调整 OUT : - QUALIFIED
 *     - 状态迁移 : (- 源状态) + (+ 目标状态)
 *
 * 每次变动都:
 *   1. UPDATE/INSERT inventory (按 tenant + product + warehouse + stock_status + batch_no)
 *   2. INSERT inventory_transactions 流水
 */
package com.dms.inventory.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryStatusOps {

    private final EntityManager em;

    /**
     * 变动库存（正数增加/负数扣减），可指定 stock_status
     */
    @Transactional
    public void change(UUID tenantId, Long productId, Long warehouseId, String batchNo,
                       BigDecimal delta, String stockStatus,
                       String txnType, String refDocType, Long refDocId) {
        if (tenantId == null || productId == null || warehouseId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "tenantId/productId/warehouseId 必填");
        }
        if (delta == null || delta.signum() == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "delta 不能为 0");
        }
        if (stockStatus == null || stockStatus.isBlank()) stockStatus = "QUALIFIED";
        String bn = batchNo == null ? "" : batchNo;

        // 1. 查是否已存在该状态的库存行
        // 策略：如果 batchNo 指定则精确匹配；否则找该状态下 qty>0 的任意一条记录
        String checkSql;
        boolean hasBatch = batchNo != null && !batchNo.isEmpty();
        if (hasBatch) {
            checkSql = "SELECT id, qty FROM inventory " +
                "WHERE tenant_id = ?1 AND product_id = ?2 AND warehouse_id = ?3 " +
                "  AND stock_status = ?4 AND COALESCE(batch_no,'') = ?5 " +
                "LIMIT 1";
        } else {
            // 不带批次时，找该状态下 qty>0 的记录，优先无批次
            checkSql = "SELECT id, qty FROM inventory " +
                "WHERE tenant_id = ?1 AND product_id = ?2 AND warehouse_id = ?3 " +
                "  AND stock_status = ?4 AND qty > 0 " +
                "ORDER BY CASE WHEN batch_no IS NULL OR batch_no = '' THEN 0 ELSE 1 END, qty DESC LIMIT 1";
        }
        var checkQ = em.createNativeQuery(checkSql, Tuple.class);
        checkQ.setParameter(1, tenantId).setParameter(2, productId).setParameter(3, warehouseId)
                .setParameter(4, stockStatus);
        if (hasBatch) checkQ.setParameter(5, bn);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = checkQ.getResultList();

        if (rows.isEmpty()) {
            if (delta.signum() < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "无[" + stockStatus + "]状态库存可扣减: product=" + productId + " warehouse=" + warehouseId);
            }
            // 新建
            var insert = em.createNativeQuery(
                    "INSERT INTO inventory (tenant_id, product_id, warehouse_id, batch_no, qty, stock_status, in_source, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, now(), now())");
            insert.setParameter(1, tenantId).setParameter(2, productId).setParameter(3, warehouseId)
                    .setParameter(4, bn.isEmpty() ? null : bn)
                    .setParameter(5, delta).setParameter(6, stockStatus).setParameter(7, txnType);
            insert.executeUpdate();
        } else {
            Tuple t = rows.get(0);
            Long invId = ((Number) t.get("id")).longValue();
            BigDecimal cur = (BigDecimal) t.get("qty");
            BigDecimal newQty = cur.add(delta);
            if (newQty.signum() < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "库存不足: product=" + productId + " status=" + stockStatus +
                        " 现有=" + cur + " 变动=" + delta);
            }
            em.createNativeQuery("UPDATE inventory SET qty = ?1, updated_at = now() WHERE id = ?2")
                    .setParameter(1, newQty).setParameter(2, invId).executeUpdate();
        }

        // 2. 写事务日志
        try {
            em.createNativeQuery(
                    "INSERT INTO inventory_transactions (tenant_id, product_id, warehouse_id, batch_no, " +
                    "qty_change, txn_type, ref_doc_type, ref_doc_id, at_time) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, now())")
                .setParameter(1, tenantId).setParameter(2, productId).setParameter(3, warehouseId)
                .setParameter(4, bn.isEmpty() ? null : bn)
                .setParameter(5, delta).setParameter(6, txnType)
                .setParameter(7, refDocType).setParameter(8, refDocId == null ? 0L : refDocId)
                .executeUpdate();
        } catch (Exception e) {
            log.warn("写库存流水失败: {}", e.getMessage());
        }
    }

    /**
     * 查询产品在指定状态的可用库存
     */
    public BigDecimal getAvailableQty(UUID tenantId, Long productId, Long warehouseId, String stockStatus) {
        String sql = "SELECT COALESCE(SUM(qty),0) FROM inventory " +
                "WHERE tenant_id = ?1 AND product_id = ?2 AND stock_status = ?3";
        if (warehouseId != null) sql += " AND warehouse_id = ?4";

        var q = em.createNativeQuery(sql);
        q.setParameter(1, tenantId).setParameter(2, productId).setParameter(3, stockStatus);
        if (warehouseId != null) q.setParameter(4, warehouseId);
        Object v = q.getSingleResult();
        return v == null ? BigDecimal.ZERO : (BigDecimal) v;
    }
}
