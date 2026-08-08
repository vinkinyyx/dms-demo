/*
 * 经销商租户与厂家 dealer 主数据绑定关系，映射 tenant_dealer_bindings 表。
 */
package com.dms.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_dealer_bindings")
@SQLRestriction("deleted_at IS NULL")
public class TenantDealerBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dealer_tenant_id", nullable = false)
    private UUID dealerTenantId;

    @Column(name = "manufacturer_tenant_id", nullable = false)
    private UUID manufacturerTenantId;

    @Column(name = "dealer_id", nullable = false)
    private Long dealerId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "bound_at", nullable = false)
    private OffsetDateTime boundAt;

    @Column(name = "bound_by")
    private Long boundBy;

    @Column(name = "unbound_at")
    private OffsetDateTime unboundAt;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
