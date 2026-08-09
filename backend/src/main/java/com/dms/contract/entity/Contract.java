package com.dms.contract.entity;

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
@Table(name = "contracts")
@SQLRestriction("deleted_at IS NULL")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(length = 200)
    private String name;

    @Column(length = 32)
    private String category;

    @Column(name = "application_type", nullable = false, length = 16)
    private String applicationType;

    @Column(name = "ref_contract_id")
    private Long refContractId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "vendor_party", length = 160)
    private String vendorParty;

    @Column(name = "dealer_party", length = 160)
    private String dealerParty;

    @Column(name = "sign_city", length = 80)
    private String signCity;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "target_amount", precision = 14, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "signed_amount", precision = 14, scale = 2)
    private BigDecimal signedAmount;

    @Column(name = "payment_terms", length = 160)
    private String paymentTerms;

    @Column(name = "settlement_cycle", length = 64)
    private String settlementCycle;

    @Column(name = "owner_name", length = 64)
    private String ownerName;

    @Column(name = "owner_phone", length = 32)
    private String ownerPhone;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "form_data", columnDefinition = "jsonb")
    private Map<String, Object> formData;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "source_file_id")
    private Long sourceFileId;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "effective_at")
    private OffsetDateTime effectiveAt;

    @Column(name = "terminated_at")
    private OffsetDateTime terminatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureMaps() {
        if (formData == null) formData = new HashMap<>();
    }
}
