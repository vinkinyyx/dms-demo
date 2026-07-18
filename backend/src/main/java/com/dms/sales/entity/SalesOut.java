/*
 * 销售出库单实体：映射 sales_outs 表。
 */
package com.dms.sales.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_outs")
@SQLRestriction("deleted_at IS NULL")
public class SalesOut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(length = 64)
    private String code;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "terminal_id")
    private Long terminalId;

    @Column(name = "business_type", length = 16)
    private String businessType;

    @Column(name = "sales_date")
    private LocalDate salesDate;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "surgery_info", columnDefinition = "jsonb")
    private Map<String, Object> surgeryInfo;

    @Column(name = "is_red")
    private Boolean isRed;

    @Column(name = "ref_sales_out_id")
    private Long refSalesOutId;

    @Column(length = 16)
    private String status;

    @Column(name = "amount_incl_tax", precision = 18, scale = 2)
    private BigDecimal amountInclTax;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureJson() {
        if (surgeryInfo == null) surgeryInfo = new HashMap<>();
    }
}
