package com.dms.approval.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DelegationRequest {
    private Long delegatorId;
    private Long delegateeId;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private String reason;
}
