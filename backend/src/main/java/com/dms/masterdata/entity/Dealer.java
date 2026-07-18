/*
 * 经销商主数据实体：映射 dealers 表。
 */
package com.dms.masterdata.entity;

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
@Table(name = "dealers")
@SQLRestriction("deleted_at IS NULL")
public class Dealer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 16)
    private String level;

    @Column(name = "parent_dealer_id")
    private Long parentDealerId;

    @Column(name = "legal_person", length = 64)
    private String legalPerson;

    @Column(name = "usc_no", length = 32)
    private String uscNo;

    @Column(name = "reg_address", length = 500)
    private String regAddress;

    @Column(name = "reg_capital", precision = 18, scale = 2)
    private BigDecimal regCapital;

    @Column(name = "founded_at")
    private LocalDate foundedAt;

    @Column(name = "business_scope", length = 500)
    private String businessScope;

    @Column(name = "gsp_status", length = 16)
    private String gspStatus;

    @Column(name = "gsp_expire")
    private LocalDate gspExpire;

    @Column(name = "gmp_status", length = 16)
    private String gmpStatus;

    @Column(name = "gmp_expire")
    private LocalDate gmpExpire;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "contact_email", length = 128)
    private String contactEmail;

    @Column(name = "sales_owner_user_id")
    private Long salesOwnerUserId;

    @Column(length = 16)
    private String status;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "attrs", columnDefinition = "jsonb")
    private Map<String, Object> attrs;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureAttrs() {
        if (attrs == null) attrs = new HashMap<>();
    }
}
