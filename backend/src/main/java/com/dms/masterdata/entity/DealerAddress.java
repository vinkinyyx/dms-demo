/*
 * 经销商收货地址实体：映射 dealer_addresses 表。
 */
package com.dms.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dealer_addresses")
@SQLRestriction("deleted_at IS NULL")
public class DealerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dealer_id")
    private Long dealerId;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(length = 32)
    private String phone;

    @Column(length = 64)
    private String province;

    @Column(length = 64)
    private String city;

    @Column(length = 64)
    private String district;

    @Column(length = 500)
    private String address;

    @Column(name = "postal_code", length = 16)
    private String postalCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
