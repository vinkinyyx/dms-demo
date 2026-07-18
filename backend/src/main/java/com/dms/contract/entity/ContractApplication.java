/*
 * 合同申请实体：映射 contract_applications 表。
 */
package com.dms.contract.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

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
@Table(name = "contract_applications")
@SQLRestriction("deleted_at IS NULL")
public class ContractApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "application_type", nullable = false, length = 16)
    private String applicationType;

    @Column(name = "contract_category", nullable = false, length = 32)
    private String contractCategory;

    @Column(name = "ref_contract_id")
    private Long refContractId;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "dealer_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> dealerSnapshot;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "authorization_scope", columnDefinition = "jsonb")
    private Map<String, Object> authorizationScope;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "indicators", columnDefinition = "jsonb")
    private Map<String, Object> indicators;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(length = 16)
    private String status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "effective_at")
    private OffsetDateTime effectiveAt;

    @Column(columnDefinition = "text")
    private String remark;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureMaps() {
        if (dealerSnapshot == null) dealerSnapshot = new HashMap<>();
        if (authorizationScope == null) authorizationScope = new HashMap<>();
        if (indicators == null) indicators = new HashMap<>();
    }
}
