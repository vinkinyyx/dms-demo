package com.dms.approval.dto;

import lombok.Data;

@Data
public class AddSignRequest {
    private Long targetUserId;
    private String signType;
    private String comment;
}
