/*
 * 分销出库服务：一级向二级出库，1:1 关联订单。
 */
package com.dms.sales.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.inventory.service.InventoryService;
import com.dms.sales.entity.DistributionLine;
import com.dms.sales.entity.DistributionShipment;
import com.dms.sales.repository.DistributionLineRepository;
import com.dms.sales.repository.DistributionShipmentRepository;
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
public class DistributionShipmentService {

    private final DistributionShipmentRepository shipmentRepository;
    private final DistributionLineRepository lineRepository;
    private final InventoryService inventoryService;
    private final DocNoGenerator docNoGenerator;

    @Transactional
    public DistributionShipment create(DistributionShipment shipment, List<DistributionLine> lines) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (shipment.getRefOrderId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "分销出库必须关联订单 refOrderId");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少分销明细");
        }

        shipment.setId(null);
        shipment.setTenantId(tenantId);
        shipment.setCode(docNoGenerator.next("DS"));
        shipment.setStatus("COMPLETED");
        shipment.setUpdatedAt(OffsetDateTime.now());
        DistributionShipment saved = shipmentRepository.save(shipment);

        for (DistributionLine l : lines) {
            l.setId(null);
            l.setShipmentId(saved.getId());
            lineRepository.save(l);
            // 从上级经销商扣库存（源仓库简化为 null → 依赖 batch/serial 精确定位）
            inventoryService.applyTransaction(tenantId, saved.getFromDealerId(), null,
                    l.getProductId(), l.getBatchNo(), l.getSerialNo(),
                    l.getQty().negate(), "DISTRIBUTION_OUT", "DISTRIBUTION", saved.getId());
            // 下级经销商入库
            inventoryService.applyTransaction(tenantId, saved.getToDealerId(), null,
                    l.getProductId(), l.getBatchNo(), l.getSerialNo(),
                    l.getQty(), "DISTRIBUTION_IN", "DISTRIBUTION", saved.getId());
        }
        log.info("分销出库 {} 完成 refOrderId={}", saved.getCode(), saved.getRefOrderId());
        return saved;
    }
}
