/*
 * 收货单仓储。
 */
package com.dms.inventory.repository;

import com.dms.inventory.entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    Page<Receipt> findByTenantId(UUID tenantId, Pageable pageable);
}
