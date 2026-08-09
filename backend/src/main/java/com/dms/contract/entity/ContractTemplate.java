package com.dms.contract.entity;

import com.dms.common.jpa.JsonListConverter;
import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contract_templates")
@SQLRestriction("deleted_at IS NULL")
public class ContractTemplate {

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

    @Column(name = "original_file_id")
    private Long originalFileId;

    @Convert(converter = JsonListConverter.class)
    @Column(name = "fields", columnDefinition = "jsonb")
    private List<Map<String, Object>> fields;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "numbering_rules", columnDefinition = "jsonb")
    private Map<String, Object> numberingRules;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
