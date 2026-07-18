/*
 * 订单状态历史仓储接口。
 */
package com.dms.order.repository;

import com.dms.order.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findByOrderIdOrderByAtTimeDesc(Long orderId);
}
