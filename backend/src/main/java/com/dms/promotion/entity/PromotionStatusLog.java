/*
 * 促销状态日志实体：映射 promotion_status_logs 表。
 */
package com.dms.promotion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "promotion_status_logs")
public class PromotionStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "from_status", length = 16)
    private String fromStatus;

    @Column(name = "to_status", length = 16)
    private String toStatus;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "at_time", insertable = false, updatable = false)
    private OffsetDateTime atTime;
}
