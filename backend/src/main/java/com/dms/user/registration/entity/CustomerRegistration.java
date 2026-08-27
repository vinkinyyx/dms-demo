/*
 * 客户自助注册申请：映射 customer_registrations，审核通过后自动创建经销商主数据与客户账号。
 */
package com.dms.user.registration.entity;

import com.dms.common.jpa.JsonListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customer_registrations")
@SQLRestriction("deleted_at IS NULL")
public class CustomerRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "register_name", nullable = false, length = 64)
    private String registerName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "usc_no", length = 32)
    private String uscNo;

    @Column(name = "legal_person", length = 64)
    private String legalPerson;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "reg_address", length = 500)
    private String regAddress;

    @Convert(converter = JsonListConverter.class)
    @Column(name = "addresses", columnDefinition = "jsonb")
    private List<Map<String, Object>> addresses;

    @Convert(converter = JsonListConverter.class)
    @Column(name = "attachments", columnDefinition = "jsonb")
    private List<Map<String, Object>> attachments;

    /** PENDING / APPROVED / REJECTED */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_user_id")
    private Long createdUserId;

    @Column(name = "created_dealer_id")
    private Long createdDealerId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public void ensureJsonLists() {
        if (addresses == null) addresses = new ArrayList<>();
        if (attachments == null) attachments = new ArrayList<>();
    }
}
