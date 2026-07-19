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
        if (receipt.getCode() == null) receipt.setCode(docNoGenerator.next("RK"));
        if (receipt.getStatus() == null) receipt.setStatus("PENDING");
        receipt.setCreatedBy(TenantContext.getUserId());
        receipt.setUpdatedAt(OffsetDateTime.now());
        return receiptRepository.save(receipt);
    }

    /**
     * v3.4.10 确认收货（支持分次收货）：
     *  - lines 中的 receivedQty 视为"本次收货量"，累加到 receipt_lines 已有的同 product 记录
     *  - 若累计已收 == 预期数量，单据 COMPLETED
     *  - 否则单据 PARTIAL_RECEIVED（允许继续收）
     */
    @Transactional
    public Receipt confirm(Long receiptId, List<ReceiptLine> lines) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收货单不存在"));
        if ("COMPLETED".equals(receipt.getStatus()) || "CANCELLED".equals(receipt.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货单已完成或已取消，不能再收货");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少收货明细");
        }

        // serial_no 单据内唯一
        Set<String> serials = new HashSet<>();
        for (ReceiptLine l : lines) {
            if (l.getSerialNo() != null && !l.getSerialNo().isBlank()) {
                if (!serials.add(l.getSerialNo())) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "收货单序列号重复: " + l.getSerialNo());
                }
            }
        }

        // 每一行都作为"本次收货流水"追加保存
        for (ReceiptLine l : lines) {
            l.setId(null);
            l.setReceiptId(receiptId);
            if (l.getReceivedQty() == null) l.setReceivedQty(l.getExpectedQty());
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

        // 判断累计已收 vs 预期：查该单所有 lines
        List<ReceiptLine> allLines = lineRepository.findByReceiptId(receiptId);
        // 按 product 聚合预期/已收
        java.util.Map<Long, java.math.BigDecimal> expectedByProduct = new java.util.HashMap<>();
        java.util.Map<Long, java.math.BigDecimal> receivedByProduct = new java.util.HashMap<>();
        for (ReceiptLine l : allLines) {
            if (l.getProductId() == null) continue;
            java.math.BigDecimal exp = l.getExpectedQty() == null ? java.math.BigDecimal.ZERO : l.getExpectedQty();
            java.math.BigDecimal rcv = l.getReceivedQty() == null ? java.math.BigDecimal.ZERO : l.getReceivedQty();
            expectedByProduct.merge(l.getProductId(), exp, java.math.BigDecimal::max);
            receivedByProduct.merge(l.getProductId(), rcv, java.math.BigDecimal::add);
        }
        boolean allReceived = !expectedByProduct.isEmpty();
        for (Long pid : expectedByProduct.keySet()) {
            java.math.BigDecimal exp = expectedByProduct.get(pid);
            java.math.BigDecimal rcv = receivedByProduct.getOrDefault(pid, java.math.BigDecimal.ZERO);
            if (rcv.compareTo(exp) < 0) { allReceived = false; break; }
        }

        receipt.setStatus(allReceived ? "COMPLETED" : "PARTIAL_RECEIVED");
        if (allReceived) {
            receipt.setReceivedAt(OffsetDateTime.now());
            receipt.setReceivedBy(TenantContext.getUserId());
        }
        receipt.setUpdatedAt(OffsetDateTime.now());
        return receiptRepository.save(receipt);
    }

    /**
     * v3.4.10 取消剩余未收部分：单据转为 CANCELLED（若已 PARTIAL 则保留已收流水）
     */
    @Transactional
    public Receipt cancel(Long receiptId, String reason) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收货单不存在"));
        if ("COMPLETED".equals(receipt.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已完成的收货单不能取消");
        }
        receipt.setStatus("CANCELLED");
        receipt.setRemark((receipt.getRemark() == null ? "" : receipt.getRemark() + "; ") + "已取消：" + (reason == null ? "" : reason));
        receipt.setUpdatedAt(OffsetDateTime.now());
        return receiptRepository.save(receipt);
    }
}
