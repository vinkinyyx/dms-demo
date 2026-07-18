/*
 * 销售发票仓储。
 */
package com.dms.invoice.repository;

import com.dms.invoice.entity.SalesInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {
    Page<SalesInvoice> findByTenantId(UUID tenantId, Pageable pageable);
    boolean existsByTenantIdAndInvoiceNo(UUID tenantId, String invoiceNo);
}
