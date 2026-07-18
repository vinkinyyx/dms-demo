/*
 * 销售发票实体：映射 sales_invoices 表。
 */
package com.dms.invoice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_invoices")
@SQLRestriction("deleted_at IS NULL")
public class SalesInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ref_sales_out_id")
    private Long refSalesOutId;

    @Column(name = "invoice_no", nullable = false, length = 64)
    private String invoiceNo;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_amount", precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
