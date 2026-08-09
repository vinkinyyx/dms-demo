package com.dms.approval.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class TemplateDetailDTO {
    private Long id;
    private UUID tenantId;
    private String businessType;
    private String code;
    private String name;
    private Integer versionNo;
    private String templateType;
    private String status;
    private Integer priority;
    private String rejectPolicy;
    private Map<String, Object> conditionConfig;
    private Integer timeoutHours;
    private Integer remindIntervalHours;
    private Integer maxRemindCount;
    private String description;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<NodeDTO> nodes;
    private List<AssigneeDTO> finishCcs;

    @Data
    @Builder
    public static class NodeDTO {
        private Long id;
        private Integer nodeOrder;
        private String name;
        private String approveMode;
        private Boolean allowTransfer;
        private Boolean allowAddSign;
        private Integer timeoutHours;
        private Integer remindIntervalHours;
        private Integer maxRemindCount;
        private List<AssigneeDTO> assignees;
        private List<AssigneeDTO> ccs;
    }

    @Data
    @Builder
    public static class AssigneeDTO {
        private Long id;
        private String assigneeType;
        private Long refId;
        private String displayName;
        private String ccStage;
    }
}
