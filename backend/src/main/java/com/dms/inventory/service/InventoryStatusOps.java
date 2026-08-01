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
    @Transactional
    public int deductById(UUID tenantId, Long inventoryId, BigDecimal qty,
                          String txnType, String refDocType, Long refDocId, Long operatorId) {
        if (inventoryId == null || qty == null || qty.signum() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "deductById: inventoryId/qty required and >0");
        }
        int upd = em.createNativeQuery(
                "UPDATE inventory SET qty = qty - ?1, updated_at = now() " +
                "WHERE id = ?2 AND tenant_id = ?3 AND qty >= ?1")
                .setParameter(1, qty).setParameter(2, inventoryId).setParameter(3, tenantId)
                .executeUpdate();
        if (upd == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "库存不足或已被其他单据占用(inventoryId=" + inventoryId + ", need=" + qty + ")");
        }
        writeTxnByInvId(tenantId, inventoryId, qty.negate(), txnType, refDocType, refDocId, operatorId);
        return upd;
    }

    @Transactional
    public void addByKey(UUID tenantId, Long productId, Long warehouseId, String batchNo, String serialNo,
                         String stockStatus, BigDecimal qty,
                         String txnType, String refDocType, Long refDocId, Long operatorId) {
        if (productId == null || warehouseId == null || qty == null || qty.signum() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "addByKey: productId/warehouseId/qty required and >0");
        }
        if (stockStatus == null || stockStatus.isBlank()) stockStatus = "QUALIFIED";
        String bn = batchNo == null ? "" : batchNo;
        var findQ = em.createNativeQuery(
                "SELECT id, qty FROM inventory " +
                "WHERE tenant_id = ?1 AND product_id = ?2 AND warehouse_id = ?3 " +
                "  AND stock_status = ?4 AND COALESCE(batch_no,'') = ?5 " +
                "  AND ((CAST(?6 AS varchar) IS NULL AND serial_no IS NULL) OR serial_no = ?6)", Tuple.class);
        findQ.setParameter(1, tenantId).setParameter(2, productId).setParameter(3, warehouseId)
              .setParameter(4, stockStatus).setParameter(5, bn).setParameter(6, serialNo);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = findQ.getResultList();
        if (rows.isEmpty()) {
            em.createNativeQuery(
                    "INSERT INTO inventory (tenant_id, warehouse_id, product_id, batch_no, serial_no, qty, stock_status, in_source, created_at, updated_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, now(), now())")
              .setParameter(1, tenantId).setParameter(2, warehouseId).setParameter(3, productId)
              .setParameter(4, bn.isEmpty() ? null : bn).setParameter(5, serialNo)
              .setParameter(6, qty).setParameter(7, stockStatus).setParameter(8, txnType)
              .executeUpdate();
        } else {
            Tuple t = rows.get(0);
            em.createNativeQuery("UPDATE inventory SET qty = qty + ?1, updated_at = now() WHERE id = ?2")
              .setParameter(1, qty).setParameter(2, ((Number) t.get("id")).longValue()).executeUpdate();
        }
        writeTxn(tenantId, productId, warehouseId, batchNo, serialNo, qty, txnType, refDocType, refDocId, operatorId);
    }

    private void writeTxnByInvId(UUID tenantId, Long inventoryId, BigDecimal delta, String txnType,
                                 String refDocType, Long refDocId, Long operatorId) {
        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT product_id, warehouse_id, batch_no, serial_no FROM inventory WHERE id = ?1 AND tenant_id = ?2")
                .setParameter(1, inventoryId).setParameter(2, tenantId).getSingleResult();
        writeTxn(tenantId, ((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                row[2] == null ? null : String.valueOf(row[2]),
                row[3] == null ? null : String.valueOf(row[3]),
                delta, txnType, refDocType, refDocId, operatorId);
    }

    private void writeTxn(UUID tenantId, Long productId, Long warehouseId, String batchNo, String serialNo,
                          BigDecimal delta, String txnType, String refDocType, Long refDocId, Long operatorId) {
        try {
            em.createNativeQuery(
                    "INSERT INTO inventory_transactions (tenant_id, warehouse_id, product_id, batch_no, serial_no, " +
                    "qty_change, txn_type, ref_doc_type, ref_doc_id, at_time, operator_id) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, now(), ?10)")
              .setParameter(1, tenantId).setParameter(2, warehouseId).setParameter(3, productId)
              .setParameter(4, batchNo).setParameter(5, serialNo).setParameter(6, delta)
              .setParameter(7, txnType).setParameter(8, refDocType).setParameter(9, refDocId == null ? 0L : refDocId)
              .setParameter(10, operatorId)
              .executeUpdate();
        } catch (Exception e) {
            log.warn("write inventory transaction failed: {}", e.getMessage());
        }
    }
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
