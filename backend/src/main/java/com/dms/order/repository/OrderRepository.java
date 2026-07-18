/*
 * 订单仓储接口。
 */
package com.dms.order.repository;

import com.dms.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByTenantId(UUID tenantId, Pageable pageable);
}
