/*
 * RMA 订单关联销售出库单：映射 rma_order_refs 表。
 */
package com.dms.rma.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rma_order_refs")
public class RmaOrderRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "rma_id", nullable = false)
    private Long rmaId;

    @Column(name = "sales_out_id", nullable = false)
    private Long salesOutId;

    @Column(name = "sales_out_code", length = 64)
    private String salesOutCode;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

}
