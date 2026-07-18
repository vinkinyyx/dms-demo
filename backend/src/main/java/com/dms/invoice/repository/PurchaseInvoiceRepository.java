/*
 * 采购发票仓储。
 */
package com.dms.invoice.repository;

import com.dms.invoice.entity.PurchaseInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {
    Page<PurchaseInvoice> findByTenantId(UUID tenantId, Pageable pageable);
    boolean existsByTenantIdAndInvoiceNo(UUID tenantId, String invoiceNo);
}
