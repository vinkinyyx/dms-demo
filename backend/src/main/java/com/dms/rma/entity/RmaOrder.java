/*
 * RMA 订单实体：映射 rma_orders 表。
 */
package com.dms.rma.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rma_orders")
@SQLRestriction("deleted_at IS NULL")
public class RmaOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(length = 64)
    private String code;

    @Column(name = "ref_rma_auth_id")
    private Long refRmaAuthId;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "rma_type", length = 16)
    private String rmaType;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 16)
    private String status;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> lines;

    @Column(columnDefinition = "text")
    private String reason;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> attachments;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureJson() {
        if (lines == null) lines = new HashMap<>();
        if (attachments == null) attachments = new HashMap<>();
    }
}
