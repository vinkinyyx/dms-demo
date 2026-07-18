/*
 * 销售出库事实表：映射 sales_out_facts 表。
 */
package com.dms.sales.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_out_facts")
public class SalesOutFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "terminal_id")
    private Long terminalId;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "sales_date")
    private LocalDate salesDate;

    @Column(precision = 14, scale = 4)
    private BigDecimal qty;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
