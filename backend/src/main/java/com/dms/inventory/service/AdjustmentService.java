/*
 * 库存调整服务：submit → INCREASE 走审批（简化：直接生效）；DECREASE 立即生效。
 */
package com.dms.inventory.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.inventory.entity.AdjustmentLine;
import com.dms.inventory.entity.InventoryAdjustment;
import com.dms.inventory.repository.AdjustmentLineRepository;
import com.dms.inventory.repository.InventoryAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final AdjustmentLineRepository lineRepository;
    private final InventoryService inventoryService;
    private final DocNoGenerator docNoGenerator;

    @Transactional
    public InventoryAdjustment submit(InventoryAdjustment adj, List<AdjustmentLine> lines) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少调整明细");
        }
        String category = adj.getAdjCategory();
        if (category == null || (!"INCREASE".equals(category) && !"DECREASE".equals(category))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "adjCategory 必须为 INCREASE/DECREASE");
        }
        adj.setId(null);
        adj.setTenantId(tenantId);
        adj.setCode(docNoGenerator.next("ADJ"));
        adj.setStatus("COMPLETED");
        adj.setOperatorId(TenantContext.getUserId());
        if ("INCREASE".equals(category)) {
            log.info("库存增加调整 code={} 简化直接通过审批", adj.getCode());
            adj.setApproverId(TenantContext.getUserId());
        }
        adj.setUpdatedAt(OffsetDateTime.now());
        InventoryAdjustment saved = adjustmentRepository.save(adj);

        boolean isIncrease = "INCREASE".equals(category);
        for (AdjustmentLine l : lines) {
            l.setId(null);
            l.setAdjustmentId(saved.getId());
            lineRepository.save(l);
            BigDecimal qtyChange = isIncrease ? l.getQty() : l.getQty().negate();
            inventoryService.applyTransaction(tenantId, adj.getDealerId(), adj.getWarehouseId(),
                    l.getProductId(), l.getBatchNo(), l.getSerialNo(),
                    qtyChange, "ADJUSTMENT_" + category, "ADJUSTMENT", saved.getId());
        }
        return saved;
    }
}
