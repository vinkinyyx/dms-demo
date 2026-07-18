/*
 * RMA 订单仓储。
 */
package com.dms.rma.repository;

import com.dms.rma.entity.RmaOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RmaOrderRepository extends JpaRepository<RmaOrder, Long> {
    Page<RmaOrder> findByTenantId(UUID tenantId, Pageable pageable);
}
