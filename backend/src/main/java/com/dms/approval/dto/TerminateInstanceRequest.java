package com.dms.approval.dto;

import lombok.Data;

@Data
public class TerminateInstanceRequest {
    private String result;
    private String reason;
}
