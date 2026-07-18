/*
 * 采购发票服务：upload → 校验 invoiceNo 唯一 → 保存 → 订单状态 COMPLETED。
 */
package com.dms.invoice.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.invoice.entity.PurchaseInvoice;
import com.dms.invoice.repository.PurchaseInvoiceRepository;
import com.dms.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseInvoiceService {

    private final PurchaseInvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public PageResult<PurchaseInvoice> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<PurchaseInvoice> page = tenantId == null
                ? invoiceRepository.findAll(pageQuery.toPageable())
                : invoiceRepository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional
    public PurchaseInvoice upload(Long refOrderId, String invoiceNo, BigDecimal amount,
                                   BigDecimal taxAmount, BigDecimal taxRate, String imageUrl) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (invoiceNo == null || invoiceNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 invoiceNo");
        }
        if (invoiceRepository.existsByTenantIdAndInvoiceNo(tenantId, invoiceNo)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "发票号已存在: " + invoiceNo);
        }
        PurchaseInvoice inv = PurchaseInvoice.builder()
                .tenantId(tenantId)
                .refOrderId(refOrderId)
                .invoiceNo(invoiceNo)
                .invoiceDate(LocalDate.now())
                .amount(amount)
                .taxAmount(taxAmount)
                .taxRate(taxRate)
                .imageUrl(imageUrl)
                .uploadedBy(TenantContext.getUserId())
                .uploadedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        PurchaseInvoice saved = invoiceRepository.save(inv);

        // 触发订单状态 → COMPLETED（简化：直接改）
        if (refOrderId != null) {
            orderRepository.findById(refOrderId).ifPresent(order -> {
                if (!"COMPLETED".equals(order.getStatus())) {
                    order.setStatus("COMPLETED");
                    order.setUpdatedAt(OffsetDateTime.now());
                    orderRepository.save(order);
                    log.info("采购发票 {} 上传，订单 {} 状态 → COMPLETED", invoiceNo, order.getCode());
                }
            });
        }
        return saved;
    }
}
