package com.dms.contract.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contract_revisions")
public class ContractRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(nullable = false)
    private Integer round;

    @Column(length = 16)
    private String action;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @Column(columnDefinition = "text")
    private String comment;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> snapshot;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
