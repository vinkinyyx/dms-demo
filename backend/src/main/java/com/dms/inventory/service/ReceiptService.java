/*
 * 收货单服务：list / confirm。
 * confirm：校验医疗器械 serial_no 唯一（简化：依赖数据库 ux_rcpt_serial 唯一索引）
 *          → 循环写库存 + 流水（applyTransaction 正数）
 *          → 收货单状态置 COMPLETED。
 *
 * v3.7.3 新增：
 *   - confirmFull(receiptId)：按 expected_qty 一次性整单入库
 *   - cancelPartial(receiptId, lines, reason)：按明细行部分取消，回收库存
 *   - cancelFull(receiptId, reason)：整单作废，已入库的全部回滚出库
 */
package com.dms.inventory.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.inventory.dto.ReceiptCancelLineRequest;
import com.dms.inventory.entity.Receipt;
import com.dms.inventory.entity.ReceiptLine;
import com.dms.inventory.entity.StockSerial;
import com.dms.inventory.repository.ReceiptLineRepository;
import com.dms.inventory.repository.ReceiptRepository;
import com.dms.inventory.repository.StockSerialRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private final StockSerialRepository stockSerialRepository;
    private final EntityManager em;

    @Transactional
    public void deleteById(Long id) {
        receiptRepository.deleteById(id);
    }

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
        if (receipt.getWarehouseId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少仓库");
        }
        receipt.setId(null);
        receipt.setTenantId(tenantId);
        if (receipt.getCode() == null) receipt.setCode(docNoGenerator.next("GR"));
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

    // ============================================================
    // v3.7.3 重构：SAP MM/Oracle WMS 风格入库业务
    // ============================================================

    /**
     * v3.7.3 整单确认收货：按 receipt_lines.expected_qty 自动全部收到。
     *  - 仅对未收满的明细执行入库（已收过的跳过）
     *  - 序列号管理的产品自动写入 stock_serials
     *  - 单据转 COMPLETED
     */
    @Transactional
    public Receipt confirmFull(Long receiptId) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收货单不存在"));
        if ("COMPLETED".equals(receipt.getStatus()) || "CANCELLED".equals(receipt.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货单已完成或已取消，不能再收货");
        }
        List<ReceiptLine> lines = lineRepository.findByReceiptId(receiptId);
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "收货单无明细，无法整单确认");
        }

        // 按 product+batch+serial 聚合每个明细行已收数量（用于跳过已收部分）
        // 直接对每条 line 判断 receivedQty == expectedQty 即视为已收满
        for (ReceiptLine l : lines) {
            BigDecimal exp = l.getExpectedQty() == null ? BigDecimal.ZERO : l.getExpectedQty();
            BigDecimal rcv = l.getReceivedQty() == null ? BigDecimal.ZERO : l.getReceivedQty();
            BigDecimal ccl = l.getCancelledQty() == null ? BigDecimal.ZERO : l.getCancelledQty();
            BigDecimal pending = exp.subtract(rcv).subtract(ccl);
            if (pending.signum() <= 0) {
                continue; // 已收满
            }
            // 入库 pending
            l.setReceivedQty(rcv.add(pending));
            lineRepository.save(l);
            inventoryService.applyTransaction(
                    receipt.getTenantId(),
                    receipt.getDealerId(),
                    receipt.getWarehouseId(),
                    l.getProductId(),
                    l.getBatchNo(),
                    l.getSerialNo(),
                    pending,
                    "RECEIPT",
                    "RECEIPT",
                    receiptId);
            // 若有序列号，写入 stock_serials 在库清单
            if (l.getSerialNo() != null && !l.getSerialNo().isBlank()) {
                StockSerial ss = StockSerial.builder()
                        .tenantId(receipt.getTenantId())
                        .warehouseId(receipt.getWarehouseId())
                        .productId(l.getProductId())
                        .batchNo(l.getBatchNo())
                        .serialNo(l.getSerialNo())
                        .stockStatus("QUALIFIED")
                        .sourceDocType("RECEIPT")
                        .sourceDocId(receiptId)
                        .sourceLineId(l.getId())
                        .receivedAt(OffsetDateTime.now())
                        .build();
                try {
                    stockSerialRepository.save(ss);
                } catch (Exception ex) {
                    log.warn("写入 stock_serials 失败（可能已存在）: serial={}", l.getSerialNo());
                }
            }
        }

        receipt.setStatus("COMPLETED");
        receipt.setReceivedAt(OffsetDateTime.now());
        receipt.setReceivedBy(TenantContext.getUserId());
        receipt.setUpdatedAt(OffsetDateTime.now());
        return receiptRepository.save(receipt);
    }

    /**
     * v3.7.3 部分取消：按明细行取消，回收库存。
     *  - 每行取消数量 <= 该行 expected_qty - 已取消
     *  - 库存反向变动（applyTransaction 负数），事务记录 reversal_of_id 指向原行
     *  - 取消后若全部行都收满+取消则单据置 CANCELLED；若还有未收行则 PARTIAL_CANCELLED
     */
    @Transactional
    public Receipt cancelPartial(Long receiptId, List<ReceiptCancelLineRequest> cancels, String reason) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收货单不存在"));
        if ("CANCELLED".equals(receipt.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货单已取消，不能再次取消");
        }
        if (cancels == null || cancels.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少取消明细");
        }

        for (ReceiptCancelLineRequest req : cancels) {
            if (req.getLineId() == null || req.getCancelQty() == null
                    || req.getCancelQty().signum() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "取消明细需 lineId 和正数 cancelQty");
            }
            ReceiptLine line = lineRepository.findById(req.getLineId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收货明细行不存在: " + req.getLineId()));
            if (!receiptId.equals(line.getReceiptId())) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "明细行不属于该收货单");
            }
            BigDecimal rcv = line.getReceivedQty() == null ? BigDecimal.ZERO : line.getReceivedQty();
            BigDecimal ccl = line.getCancelledQty() == null ? BigDecimal.ZERO : line.getCancelledQty();
            BigDecimal alreadyCancelled = ccl;
            // 已收 - 累计已取消 = 可继续取消数
            BigDecimal cancellable = rcv.subtract(alreadyCancelled);
            if (req.getCancelQty().compareTo(cancellable) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "明细行 " + line.getId() + " 取消数量 " + req.getCancelQty()
                        + " 超过可取消数 " + cancellable);
            }

            // 库存反向变动（负数），记录 reversal_of_id 指向原 line
            inventoryService.applyTransactionWithSource(
                    receipt.getTenantId(),
                    receipt.getDealerId(),
                    receipt.getWarehouseId(),
                    line.getProductId(),
                    line.getBatchNo(),
                    line.getSerialNo(),
                    req.getCancelQty().negate(),
                    "RECEIPT_CANCEL",
                    "RECEIPT",
                    receiptId,
                    line.getId(),   // source_line_id
                    null);

            // 若有序列号，标记 stock_serials shipped_at（视为已离开库）
            if (line.getSerialNo() != null && !line.getSerialNo().isBlank()) {
                stockSerialRepository.findByTenantIdAndSerialNoAndShippedAtIsNull(
                                receipt.getTenantId(), line.getSerialNo())
                        .ifPresent(ss -> {
                            ss.setShippedAt(OffsetDateTime.now());
                            stockSerialRepository.save(ss);
                        });
            }

            line.setCancelledQty(alreadyCancelled.add(req.getCancelQty()));
            line.setCancelledAt(OffsetDateTime.now());
            lineRepository.save(line);
        }

        // 重新评估单据状态
        List<ReceiptLine> all = lineRepository.findByReceiptId(receiptId);
        boolean allDone = !all.isEmpty();
        boolean anyPending = false;
        for (ReceiptLine l : all) {
            BigDecimal exp = l.getExpectedQty() == null ? BigDecimal.ZERO : l.getExpectedQty();
            BigDecimal ccl = l.getCancelledQty() == null ? BigDecimal.ZERO : l.getCancelledQty();
            BigDecimal left = exp.subtract(ccl);
            if (left.signum() > 0) {
                allDone = false;
                anyPending = true;
            }
        }
        if (allDone) {
            receipt.setStatus("CANCELLED");
        } else if (anyPending) {
            receipt.setStatus("PARTIAL_CANCELLED");
        }
        receipt.setRemark((receipt.getRemark() == null ? "" : receipt.getRemark() + "; ") + "部分取消：" + (reason == null ? "" : reason));
        receipt.setUpdatedAt(OffsetDateTime.now());
        return receiptRepository.save(receipt);
    }

    /**
     * v3.7.3 整单作废：已入库的全部回滚出库（按明细行 receivedQty - cancelledQty 回退）。
     */
    @Transactional
    public Receipt cancelFull(Long receiptId, String reason) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收货单不存在"));
        if ("CANCELLED".equals(receipt.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "收货单已取消");
        }

        List<ReceiptLine> lines = lineRepository.findByReceiptId(receiptId);
        if (lines != null) {
            for (ReceiptLine l : lines) {
                BigDecimal rcv = l.getReceivedQty() == null ? BigDecimal.ZERO : l.getReceivedQty();
                BigDecimal ccl = l.getCancelledQty() == null ? BigDecimal.ZERO : l.getCancelledQty();
                BigDecimal rollbackQty = rcv.subtract(ccl);
                if (rollbackQty.signum() <= 0) {
                    continue;
                }
                // 整单作废时，即使行原状态是已收满，也将其视为取消
                inventoryService.applyTransactionWithSource(
                        receipt.getTenantId(),
                        receipt.getDealerId(),
                        receipt.getWarehouseId(),
                        l.getProductId(),
                        l.getBatchNo(),
                        l.getSerialNo(),
                        rollbackQty.negate(),
                        "RECEIPT_CANCEL",
                        "RECEIPT",
                        receiptId,
                        l.getId(),
                        null);
                if (l.getSerialNo() != null && !l.getSerialNo().isBlank()) {
                    stockSerialRepository.findByTenantIdAndSerialNoAndShippedAtIsNull(
                                    receipt.getTenantId(), l.getSerialNo())
                            .ifPresent(ss -> {
                                ss.setShippedAt(OffsetDateTime.now());
                                stockSerialRepository.save(ss);
                            });
                }
                l.setCancelledQty(rcv);
                l.setCancelledAt(OffsetDateTime.now());
                lineRepository.save(l);
            }
        }

        receipt.setStatus("CANCELLED");
        receipt.setRemark((receipt.getRemark() == null ? "" : receipt.getRemark() + "; ") + "整单作废：" + (reason == null ? "" : reason));
        receipt.setUpdatedAt(OffsetDateTime.now());
        return receiptRepository.save(receipt);
    }
}
