/*
 * 临时授权实体：映射 temp_authorizations 表。
 */
package com.dms.authz.entity;

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
@Table(name = "temp_authorizations")
@SQLRestriction("deleted_at IS NULL")
public class TempAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "auth_type", length = 32)
    private String authType;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "scope", columnDefinition = "jsonb")
    private Map<String, Object> scope;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(length = 16)
    private String status;

    @Column(name = "applicant_id")
    private Long applicantId;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureScope() {
        if (scope == null) scope = new HashMap<>();
    }
}
