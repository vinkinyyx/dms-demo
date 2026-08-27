/*
 * 客户代金券使用记录：映射 customer_voucher_usages，记录下单核销与作废返还。
 */
package com.dms.voucher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customer_voucher_usages")
public class CustomerVoucherUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "voucher_id", nullable = false)
    private Long voucherId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_code", length = 64)
    private String orderCode;

    @Column(name = "used_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal usedAmount;

    /** USED / REFUNDED / REVERSED */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
