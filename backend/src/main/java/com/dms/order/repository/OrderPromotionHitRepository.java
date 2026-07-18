/*
 * 订单促销命中仓储接口。
 */
package com.dms.order.repository;

import com.dms.order.entity.OrderPromotionHit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderPromotionHitRepository extends JpaRepository<OrderPromotionHit, Long> {
    List<OrderPromotionHit> findByOrderId(Long orderId);
}
