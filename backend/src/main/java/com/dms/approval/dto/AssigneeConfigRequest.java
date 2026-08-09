package com.dms.approval.dto;

import lombok.Data;

@Data
public class AssigneeConfigRequest {
    private String assigneeType;
    private Long refId;
    private String displayName;
}
