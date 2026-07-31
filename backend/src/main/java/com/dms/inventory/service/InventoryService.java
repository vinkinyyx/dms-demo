/*
 * 库存核心服务：明细查询 + applyTransaction（锁 + 加减 + 写流水）。
 * 扣减类：SELECT FOR UPDATE 库存记录 → 校验 qty 足够 → 扣减；
 * 增加类：查不到则 upsert 新增 → 加库存；
 * 每次调用一条 inventory_transactions 流水。
 */
package com.dms.inventory.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.inventory.entity.Inventory;
import com.dms.inventory.entity.InventoryTransaction;
import com.dms.inventory.repository.InventoryRepository;
import com.dms.inventory.repository.InventoryTransactionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository txnRepository;
    private final EntityManager em;

    @Transactional(readOnly = true)
    public PageResult<Inventory> query(Long dealerId, Long productId, String batchNo, PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        Page<Inventory> page = inventoryRepository.query(tenantId, dealerId, productId, batchNo, pageQuery.toPageable());
        return PageResult.of(page);
    }

    /**
     * 单条库存变动：dealerId/warehouseId/product/batch/serial + qtyChange(正:入库/负:出库) + 单据类型。
     * 悲观锁定库存行避免并发；出库时严格校验 qty >= |qtyChange|。
     */
    @Transactional
    public Inventory applyTransaction(UUID tenantId, Long dealerId, Long warehouseId,
                                      Long productId, String batchNo, String serialNo,
                                      BigDecimal qtyChange, String txnType,
                                      String refDocType, Long refDocId) {
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (qtyChange == null || qtyChange.signum() == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "qtyChange 不能为 0");
        }

        Inventory inv = inventoryRepository
                .lockKeyed(tenantId, warehouseId, productId, batchNo, serialNo)
                .orElse(null);

        if (inv == null) {
            if (qtyChange.signum() < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "库存不存在，无法扣减: product=" + productId + " batch=" + batchNo);
            }
            inv = Inventory.builder()
                    .tenantId(tenantId)
                    .dealerId(dealerId)
                    .warehouseId(warehouseId)
                    .productId(productId)
                    .batchNo(batchNo)
                    .serialNo(serialNo)
                    .qty(BigDecimal.ZERO)
                    .inSource(txnType)
                    .updatedAt(OffsetDateTime.now())
                    .build();
        }

        BigDecimal newQty = inv.getQty().add(qtyChange);
        if (newQty.signum() < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "库存不足: product=" + productId + " batch=" + batchNo
                            + " 现有=" + inv.getQty() + " 变动=" + qtyChange);
        }
        inv.setQty(newQty);
        inv.setUpdatedAt(OffsetDateTime.now());
        Inventory saved = inventoryRepository.save(inv);

        InventoryTransaction txn = InventoryTransaction.builder()
                .tenantId(tenantId)
                .dealerId(dealerId)
                .warehouseId(warehouseId)
                .productId(productId)
                .batchNo(batchNo)
                .serialNo(serialNo)
                .qtyChange(qtyChange)
                .txnType(txnType)
                .refDocType(refDocType)
                .refDocId(refDocId)
                .atTime(OffsetDateTime.now())
                .operatorId(TenantContext.getUserId())
                .build();
        txnRepository.save(txn);
        return saved;
    }

    /**
     * v3.7.3 增强：支持记录 source_line_id / reversal_of_id 的对冲事务版本。
     * 用于部分取消、红字冲销等需要明确指向原事务/明细行的场景。
     */
    @Transactional
    public Inventory applyTransactionWithSource(UUID tenantId, Long dealerId, Long warehouseId,
                                                Long productId, String batchNo, String serialNo,
                                                BigDecimal qtyChange, String txnType,
                                                String refDocType, Long refDocId,
                                                Long sourceLineId, Long reversalOfId) {
        Inventory inv = applyTransaction(tenantId, dealerId, warehouseId,
                productId, batchNo, serialNo, qtyChange, txnType, refDocType, refDocId);
        // 直接更新最近一条流水（applyTransaction 已写）的对冲字段
        if (sourceLineId != null || reversalOfId != null) {
            em.createNativeQuery(
                    "UPDATE inventory_transactions " +
                    "SET source_line_id = COALESCE(?1, source_line_id), " +
                    "    reversal_of_id = COALESCE(?2, reversal_of_id) " +
                    "WHERE id = (SELECT id FROM inventory_transactions " +
                    "            WHERE tenant_id = ?3 AND ref_doc_type = ?4 AND ref_doc_id = ?5 " +
                    "              AND product_id = ?6 AND qty_change = ?7 " +
                    "            ORDER BY id DESC LIMIT 1)")
                    .setParameter(1, sourceLineId)
                    .setParameter(2, reversalOfId)
                    .setParameter(3, tenantId)
                    .setParameter(4, refDocType)
                    .setParameter(5, refDocId)
                    .setParameter(6, productId)
                    .setParameter(7, qtyChange)
                    .executeUpdate();
        }
        return inv;
    }
}
