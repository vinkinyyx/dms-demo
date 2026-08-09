package com.dms.approval.dto;

import lombok.Data;

@Data
public class ReassignTaskRequest {
    private Long targetUserId;
    private String reason;
}
