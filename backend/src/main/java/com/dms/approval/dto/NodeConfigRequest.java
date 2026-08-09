package com.dms.approval.dto;

import lombok.Data;

import java.util.List;

@Data
public class NodeConfigRequest {
    private Long id;
    private Integer nodeOrder;
    private String name;
    private String approveMode;
    private Boolean allowTransfer;
    private Boolean allowAddSign;
    private Integer timeoutHours;
    private Integer remindIntervalHours;
    private Integer maxRemindCount;
    private List<AssigneeConfigRequest> assignees;
    private List<AssigneeConfigRequest> ccs;
}
