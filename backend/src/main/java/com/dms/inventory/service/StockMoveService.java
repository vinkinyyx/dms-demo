/*
 * 移库服务：create（源库存扣减 + 目的库存增加 + 2 条流水）。
 */
package com.dms.inventory.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.inventory.entity.StockMove;
import com.dms.inventory.entity.StockMoveLine;
import com.dms.inventory.repository.StockMoveLineRepository;
import com.dms.inventory.repository.StockMoveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMoveService {

    private final StockMoveRepository moveRepository;
    private final StockMoveLineRepository lineRepository;
    private final InventoryService inventoryService;
    private final DocNoGenerator docNoGenerator;

    @Transactional
    public StockMove create(StockMove move, List<StockMoveLine> lines) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少移库明细");
        }
        if (move.getSrcWarehouseId() == null || move.getDstWarehouseId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少源/目的仓库");
        }
        if (move.getSrcWarehouseId().equals(move.getDstWarehouseId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "源/目的仓库不能相同");
        }

        move.setId(null);
        move.setTenantId(tenantId);
        move.setCode(docNoGenerator.next("MV"));
        move.setStatus("COMPLETED");
        move.setOperatorId(TenantContext.getUserId());
        move.setAtTime(OffsetDateTime.now());
        move.setUpdatedAt(OffsetDateTime.now());
        StockMove saved = moveRepository.save(move);

        for (StockMoveLine l : lines) {
            l.setId(null);
            l.setMoveId(saved.getId());
            lineRepository.save(l);

            // 源仓库出库（负）
            inventoryService.applyTransaction(tenantId, move.getDealerId(), move.getSrcWarehouseId(),
                    l.getProductId(), l.getBatchNo(), l.getSerialNo(),
                    l.getQty().negate(), "STOCK_MOVE_OUT", "STOCK_MOVE", saved.getId());
            // 目的仓库入库（正）
            inventoryService.applyTransaction(tenantId, move.getDealerId(), move.getDstWarehouseId(),
                    l.getProductId(), l.getBatchNo(), l.getSerialNo(),
                    l.getQty(), "STOCK_MOVE_IN", "STOCK_MOVE", saved.getId());
        }
        return saved;
    }
}
