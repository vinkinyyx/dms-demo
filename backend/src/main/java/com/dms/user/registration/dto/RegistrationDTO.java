/*
 * 注册申请展示 DTO（不回传密码哈希）。
 */
package com.dms.user.registration.dto;

import com.dms.user.registration.entity.CustomerRegistration;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class RegistrationDTO {

    private Long id;
    private UUID tenantId;
    private String registerName;
    private String phone;
    private String email;
    private String companyName;
    private String uscNo;
    private String legalPerson;
    private String contactName;
    private String contactPhone;
    private String regAddress;
    private List<Map<String, Object>> addresses;
    private List<Map<String, Object>> attachments;
    private String status;
    private String rejectReason;
    private Long reviewerId;
    private OffsetDateTime reviewedAt;
    private Long createdUserId;
    private Long createdDealerId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static RegistrationDTO of(CustomerRegistration r) {
        return RegistrationDTO.builder()
                .id(r.getId())
                .tenantId(r.getTenantId())
                .registerName(r.getRegisterName())
                .phone(r.getPhone())
                .email(r.getEmail())
                .companyName(r.getCompanyName())
                .uscNo(r.getUscNo())
                .legalPerson(r.getLegalPerson())
                .contactName(r.getContactName())
                .contactPhone(r.getContactPhone())
                .regAddress(r.getRegAddress())
                .addresses(r.getAddresses())
                .attachments(r.getAttachments())
                .status(r.getStatus())
                .rejectReason(r.getRejectReason())
                .reviewerId(r.getReviewerId())
                .reviewedAt(r.getReviewedAt())
                .createdUserId(r.getCreatedUserId())
                .createdDealerId(r.getCreatedDealerId())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
