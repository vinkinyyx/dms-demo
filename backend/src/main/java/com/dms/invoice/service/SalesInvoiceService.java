/*
 * 销售发票服务：upload → 校验 invoiceNo 唯一 → 保存。
 */
package com.dms.invoice.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.invoice.entity.SalesInvoice;
import com.dms.invoice.repository.SalesInvoiceRepository;
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
public class SalesInvoiceService {

    private final SalesInvoiceRepository repository;

    @Transactional(readOnly = true)
    public PageResult<SalesInvoice> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<SalesInvoice> page = tenantId == null
                ? repository.findAll(pageQuery.toPageable())
                : repository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional
    public SalesInvoice upload(Long refSalesOutId, String invoiceNo, BigDecimal amount,
                                BigDecimal taxAmount, String imageUrl) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (invoiceNo == null || invoiceNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 invoiceNo");
        }
        if (repository.existsByTenantIdAndInvoiceNo(tenantId, invoiceNo)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "发票号已存在: " + invoiceNo);
        }
        SalesInvoice inv = SalesInvoice.builder()
                .tenantId(tenantId)
                .refSalesOutId(refSalesOutId)
                .invoiceNo(invoiceNo)
                .invoiceDate(LocalDate.now())
                .amount(amount)
                .taxAmount(taxAmount)
                .imageUrl(imageUrl)
                .uploadedBy(TenantContext.getUserId())
                .uploadedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        log.info("销售发票 {} 上传 refSalesOutId={}", invoiceNo, refSalesOutId);
        return repository.save(inv);
    }
}
