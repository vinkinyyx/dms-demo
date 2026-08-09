package com.dms.approval.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TemplateSaveRequest {
    private String businessType;
    private String code;
    private String name;
    private String templateType;
    private Integer priority;
    private String rejectPolicy;
    private Map<String, Object> conditionConfig;
    private Integer timeoutHours;
    private Integer remindIntervalHours;
    private Integer maxRemindCount;
    private String description;
    private List<NodeConfigRequest> nodes;
    private List<AssigneeConfigRequest> finishCcs;
}
