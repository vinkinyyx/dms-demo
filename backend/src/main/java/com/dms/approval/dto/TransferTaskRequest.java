package com.dms.approval.dto;

import lombok.Data;

@Data
public class TransferTaskRequest {
    private Long targetUserId;
    private String comment;
}
