/*
 * 订单响应 DTO：附带 lines 与 promotionHits。
 */
package com.dms.order.dto;

import com.dms.order.entity.Order;
import com.dms.order.entity.OrderLine;
import com.dms.order.entity.OrderPromotionHit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderDTO {

    private Order order;
    private List<OrderLine> lines;
    private List<OrderPromotionHit> promotionHits;
    private List<String> warnings;
}
