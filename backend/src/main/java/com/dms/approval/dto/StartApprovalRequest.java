package com.dms.approval.dto;

import lombok.Data;

import java.util.Map;

@Data
public class StartApprovalRequest {
    private String businessType;
    private Long businessId;
    private String businessCode;
    private String title;
    private Map<String, Object> businessSnapshot;
}
