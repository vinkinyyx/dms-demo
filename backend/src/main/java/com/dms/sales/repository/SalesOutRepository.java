/*
 * 销售出库单仓储。
 */
package com.dms.sales.repository;

import com.dms.sales.entity.SalesOut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SalesOutRepository extends JpaRepository<SalesOut, Long> {
    Page<SalesOut> findByTenantId(UUID tenantId, Pageable pageable);
}
