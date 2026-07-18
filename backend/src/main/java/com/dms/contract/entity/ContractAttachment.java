/*
 * 合同附件实体：映射 contract_attachments 表。
 */
package com.dms.contract.entity;

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
@Table(name = "contract_attachments")
@SQLRestriction("deleted_at IS NULL")
public class ContractAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "ref_type", length = 16)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(length = 64)
    private String category;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "file_url", columnDefinition = "text")
    private String fileUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_at", insertable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
