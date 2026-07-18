/*
 * 收货单服务：list / confirm。
 * confirm：校验医疗器械 serial_no 唯一（简化：依赖数据库 ux_rcpt_serial 唯一索引）
 *          → 循环写库存 + 流水（applyTransaction 正数）
 *          → 收货单状态置 COMPLETED。
 */
package com.dms.inventory.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.inventory.entity.Receipt;
import com.dms.inventory.entity.ReceiptLine;
import com.dms.inventory.repository.ReceiptLineRepository;
import com.dms.inventory.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptLineRepository lineRepository;
    private final InventoryService inventoryService;
    private final DocNoGenerator docNoGenerator;

    @Transactional(readOnly = true)
    public PageResult<Receipt> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<Receipt> page = tenantId == null
                ? receiptRepository.findAll(pageQuery.toPageable())
                : receiptRepository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional
    public Receipt create(Receipt receipt) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        receipt.setId(null);
        receipt.setTenantId(tenantId);
        if (receipt.getCode() == null) receipt.setCode(docNoGenerator.next("IN"));
        if (receipt.getStatus() == null) receipt.setStatus("PENDING");
        receipt.setCreatedBy(TenantContext.getUserId());
        receipt.setUpdatedAt(OffsetDateTime.now());
        return receiptRepository.save(receipt);
    }

    /**
     * 确认收货：写入 lines + 更新库存 + 写流水。
     */
    @Transactional
    public Receipt confirm(Long receiptId, List<ReceiptLine> lines) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收货单不存在"));
        if ("COMPLETED".equals(receipt.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货单已完成");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少收货明细");
        }

        // serial_no 单据内唯一（医疗器械追溯：这里只做本地判重）
        Set<String> serials = new HashSet<>();
        for (ReceiptLine l : lines) {
            if (l.getSerialNo() != null && !l.getSerialNo().isBlank()) {
                if (!serials.add(l.getSerialNo())) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "收货单序列号重复: " + l.getSerialNo());
                }
            }
        }

        for (ReceiptLine l : lines) {
            l.setId(null);
            l.setReceiptId(receiptId);
            lineRepository.save(l);
            inventoryService.applyTransaction(
                    receipt.getTenantId(),
                    receipt.getDealerId(),
                    receipt.getWarehouseId(),
                    l.getProductId(),
                    l.getBatchNo(),
                    l.getSerialNo(),
                    l.getReceivedQty(),
                    "RECEIPT",
                    "RECEIPT",
                    receiptId);
        }

        receipt.setStatus("COMPLETED");
        receipt.setReceivedAt(OffsetDateTime.now());
        receipt.setReceivedBy(TenantContext.getUserId());
        receipt.setUpdatedAt(OffsetDateTime.now());
        return receiptRepository.save(receipt);
    }
}
