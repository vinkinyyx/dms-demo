package com.dms.contract.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
public class ContractRequest {
    private String name;
    private String category;
    private String applicationType;
    private Long refContractId;
    private Long templateId;
    private Long dealerId;
    private String vendorParty;
    private String dealerParty;
    private String signCity;
    private LocalDate validFrom;
    private LocalDate validTo;
    private BigDecimal targetAmount;
    private BigDecimal signedAmount;
    private String paymentTerms;
    private String settlementCycle;
    private String ownerName;
    private String ownerPhone;
    private Map<String, Object> formData;
    private String remark;
}
