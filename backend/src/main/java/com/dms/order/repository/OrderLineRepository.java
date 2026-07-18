/*
 * 订单明细仓储接口。
 */
package com.dms.order.repository;

import com.dms.order.entity.OrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {
    List<OrderLine> findByOrderIdOrderBySeqAsc(Long orderId);
}
