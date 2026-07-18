/*
 * 订单状态历史实体：映射 order_status_history 表。
 */
package com.dms.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", length = 32)
    private String toStatus;

    @Column(length = 32)
    private String action;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "at_time", insertable = false, updatable = false)
    private OffsetDateTime atTime;
}
