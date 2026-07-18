/*
 * 盘点单实体：映射 stocktakes 表。
 */
package com.dms.inventory.entity;

import com.dms.common.jpa.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stocktakes")
@SQLRestriction("deleted_at IS NULL")
public class Stocktake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "period_yyyymm", nullable = false, length = 6)
    private String periodYyyymm;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "is_late")
    private Boolean isLate;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "diff_summary", columnDefinition = "jsonb")
    private Map<String, Object> diffSummary;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureJson() {
        if (diffSummary == null) diffSummary = new HashMap<>();
    }
}
