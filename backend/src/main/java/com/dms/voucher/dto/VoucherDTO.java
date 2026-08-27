/*
 * 代金券展示 DTO。
 */
package com.dms.voucher.dto;

import com.dms.voucher.entity.CustomerVoucher;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class VoucherDTO {

    private Long id;
    private UUID tenantId;
    private String code;
    private String name;
    private Long dealerId;
    private String dealerName;
    private BigDecimal faceValue;
    private BigDecimal minSpend;
    private String scopeType;
    private List<Map<String, Object>> scopeRefs;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private String status;
    private String batchNo;
    private String remark;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static VoucherDTO of(CustomerVoucher v) {
        return VoucherDTO.builder()
                .id(v.getId())
                .tenantId(v.getTenantId())
                .code(v.getCode())
                .name(v.getName())
                .dealerId(v.getDealerId())
                .faceValue(v.getFaceValue())
                .minSpend(v.getMinSpend())
                .scopeType(v.getScopeType())
                .scopeRefs(v.getScopeRefs())
                .validFrom(v.getValidFrom())
                .validTo(v.getValidTo())
                .status(v.getStatus())
                .batchNo(v.getBatchNo())
                .remark(v.getRemark())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
