package com.dms.approval.service;

import com.dms.approval.dto.*;
import com.dms.approval.entity.*;
import com.dms.approval.repository.*;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ApprovalTemplateService {
    private final ApprovalTemplateRepository templateRepository;
    private final ApprovalTemplateNodeRepository nodeRepository;
    private final ApprovalNodeAssigneeRepository assigneeRepository;
    private final ApprovalTemplateCcRepository ccRepository;

    @Transactional(readOnly = true)
    public PageResult<TemplateSummaryDTO> list(PageQuery pageQuery, String businessType, String status, String keyword) {
        UUID tenantId = requireTenantId();
        if (businessType != null && !businessType.isBlank()) {
            var all = templateRepository.findByTenantIdAndBusinessTypeOrderByPriorityDescVersionNoDescIdDesc(tenantId, businessType);
            var statuses = parseStatuses(status);
            var filtered = all.stream()
                    .filter(t -> statuses.isEmpty() || statuses.contains(t.getStatus()))
                    .filter(t -> keyword == null || keyword.isBlank() || t.getName().contains(keyword) || t.getCode().contains(keyword))
                    .toList();
            int start = Math.min((pageQuery.getPage() - 1) * pageQuery.getSize(), filtered.size());
            int end = Math.min(start + pageQuery.getSize(), filtered.size());
            return new PageResult<>((long) filtered.size(), pageQuery.getPage(), pageQuery.getSize(),
                    filtered.subList(start, end).stream().map(this::toSummary).toList());
        }
        Page<ApprovalTemplate> page = templateRepository.findAll(PageRequest.of(
                pageQuery.getPage() - 1, pageQuery.getSize(), Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(page.map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public TemplateDetailDTO get(Long id) {
        return toDetail(getTenantTemplate(id));
    }

    @Transactional
    public TemplateDetailDTO createDraft(TemplateSaveRequest request) {
        UUID tenantId = requireTenantId();
        validateRequest(request);
        ApprovalTemplate template = ApprovalTemplate.builder()
                .tenantId(tenantId)
                .businessType(request.getBusinessType())
                .code(request.getCode())
                .name(request.getName())
                .versionNo(1)
                .templateType(parseTemplateType(request.getTemplateType()))
                .status(ApprovalTemplateStatus.DRAFT)
                .priority(request.getPriority() == null ? 100 : request.getPriority())
                .rejectPolicy(parseRejectPolicy(request.getRejectPolicy()))
                .conditionConfig(request.getConditionConfig())
                .timeoutHours(request.getTimeoutHours())
                .remindIntervalHours(request.getRemindIntervalHours())
                .maxRemindCount(defaultInt(request.getMaxRemindCount()))
                .description(request.getDescription())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdBy(TenantContext.getUserId())
                .updatedBy(TenantContext.getUserId())
                .build();
        template = templateRepository.save(template);
        saveNodesAndCcs(template, request);
        return toDetail(template);
    }

    @Transactional
    public TemplateDetailDTO updateDraft(Long id, TemplateSaveRequest request) {
        ApprovalTemplate template = getTenantTemplate(id);
        if (template.getStatus() != ApprovalTemplateStatus.DRAFT) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已发布或停用的审批流不能直接修改，请复制为新版本");
        }
        validateRequest(request);
        template.setBusinessType(request.getBusinessType());
        template.setCode(request.getCode());
        template.setName(request.getName());
        template.setTemplateType(parseTemplateType(request.getTemplateType()));
        template.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        template.setRejectPolicy(parseRejectPolicy(request.getRejectPolicy()));
        template.setConditionConfig(request.getConditionConfig());
        template.setTimeoutHours(request.getTimeoutHours());
        template.setRemindIntervalHours(request.getRemindIntervalHours());
        template.setMaxRemindCount(defaultInt(request.getMaxRemindCount()));
        template.setDescription(request.getDescription());
        template.setUpdatedAt(OffsetDateTime.now());
        template.setUpdatedBy(TenantContext.getUserId());
        clearConfig(template.getId());
        saveNodesAndCcs(template, request);
        return toDetail(templateRepository.save(template));
    }

    @Transactional
    public TemplateDetailDTO publish(Long id) {
        ApprovalTemplate template = getTenantTemplate(id);
        if (template.getTemplateType() == ApprovalTemplateType.MANUAL) {
            var nodes = nodeRepository.findByTemplateIdOrderByNodeOrderAscIdAsc(template.getId());
            if (nodes.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "人工审批流至少需要一个审批节点");
            for (ApprovalTemplateNode node : nodes) {
                if (assigneeRepository.findByNodeIdOrderByIdAsc(node.getId()).isEmpty()) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "节点[" + node.getName() + "]未配置审批人");
                }
            }
        }
        template.setStatus(ApprovalTemplateStatus.ENABLED);
        template.setPublishedAt(OffsetDateTime.now());
        template.setPublishedBy(TenantContext.getUserId());
        template.setUpdatedAt(OffsetDateTime.now());
        return toDetail(templateRepository.save(template));
    }

    @Transactional
    public TemplateDetailDTO disable(Long id) {
        ApprovalTemplate template = getTenantTemplate(id);
        template.setStatus(ApprovalTemplateStatus.DISABLED);
        template.setUpdatedAt(OffsetDateTime.now());
        return toDetail(templateRepository.save(template));
    }

    @Transactional
    public TemplateDetailDTO newVersion(Long id) {
        ApprovalTemplate source = getTenantTemplate(id);
        int nextVersion = templateRepository
                .findFirstByTenantIdAndBusinessTypeAndCodeOrderByVersionNoDescIdDesc(source.getTenantId(), source.getBusinessType(), source.getCode())
                .map(t -> t.getVersionNo() + 1).orElse(1);
        ApprovalTemplate copy = ApprovalTemplate.builder()
                .tenantId(source.getTenantId())
                .businessType(source.getBusinessType())
                .code(source.getCode())
                .name(source.getName())
                .versionNo(nextVersion)
                .templateType(source.getTemplateType())
                .status(ApprovalTemplateStatus.DRAFT)
                .priority(source.getPriority())
                .rejectPolicy(source.getRejectPolicy())
                .conditionConfig(source.getConditionConfig())
                .timeoutHours(source.getTimeoutHours())
                .remindIntervalHours(source.getRemindIntervalHours())
                .maxRemindCount(source.getMaxRemindCount())
                .description(source.getDescription())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdBy(TenantContext.getUserId())
                .updatedBy(TenantContext.getUserId())
                .build();
        copy = templateRepository.save(copy);
        for (ApprovalTemplateNode oldNode : nodeRepository.findByTemplateIdOrderByNodeOrderAscIdAsc(source.getId())) {
            ApprovalTemplateNode node = copyNode(copy, oldNode);
            node = nodeRepository.save(node);
            for (ApprovalNodeAssignee a : assigneeRepository.findByNodeIdOrderByIdAsc(oldNode.getId())) {
                assigneeRepository.save(copyAssignee(node, a));
            }
        }
        for (ApprovalTemplateCc cc : ccRepository.findByTemplateIdOrderByIdAsc(source.getId())) {
            ccRepository.save(ApprovalTemplateCc.builder()
                    .templateId(copy.getId()).tenantId(copy.getTenantId())
                    .ccType(cc.getCcType()).refId(cc.getRefId()).displayName(cc.getDisplayName())
                    .ccStage(cc.getCcStage()).build());
        }
        return toDetail(copy);
    }

    private void saveNodesAndCcs(ApprovalTemplate template, TemplateSaveRequest request) {
        List<NodeConfigRequest> nodes = request.getNodes() == null ? List.of() : request.getNodes();
        int order = 1;
        for (NodeConfigRequest n : nodes) {
            ApprovalTemplateNode node = ApprovalTemplateNode.builder()
                    .templateId(template.getId()).tenantId(template.getTenantId())
                    .nodeOrder(n.getNodeOrder() == null ? order : n.getNodeOrder())
                    .name(n.getName())
                    .approveMode(n.getApproveMode() == null ? ApprovalApproveMode.ANY : ApprovalApproveMode.valueOf(n.getApproveMode()))
                    .allowTransfer(n.getAllowTransfer() == null || n.getAllowTransfer())
                    .allowAddSign(n.getAllowAddSign() == null || n.getAllowAddSign())
                    .timeoutHours(n.getTimeoutHours())
                    .remindIntervalHours(n.getRemindIntervalHours())
                    .maxRemindCount(defaultInt(n.getMaxRemindCount()))
                    .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                    .build();
            node = nodeRepository.save(node);
            if (n.getAssignees() != null) {
                for (AssigneeConfigRequest a : n.getAssignees()) {
                    assigneeRepository.save(ApprovalNodeAssignee.builder()
                            .nodeId(node.getId()).tenantId(template.getTenantId())
                            .assigneeType(ApprovalAssigneeType.valueOf(a.getAssigneeType()))
                            .refId(a.getRefId()).displayName(a.getDisplayName()).build());
                }
            }
            if (n.getCcs() != null) {
                for (AssigneeConfigRequest cc : n.getCcs()) {
                    ccRepository.save(ApprovalTemplateCc.builder()
                            .templateId(template.getId()).nodeId(node.getId()).tenantId(template.getTenantId())
                            .ccType(ApprovalAssigneeType.valueOf(cc.getAssigneeType()))
                            .refId(cc.getRefId()).displayName(cc.getDisplayName()).ccStage("NODE").build());
                }
            }
            order++;
        }
        if (request.getFinishCcs() != null) {
            for (AssigneeConfigRequest cc : request.getFinishCcs()) {
                ccRepository.save(ApprovalTemplateCc.builder()
                        .templateId(template.getId()).tenantId(template.getTenantId())
                        .ccType(ApprovalAssigneeType.valueOf(cc.getAssigneeType()))
                        .refId(cc.getRefId()).displayName(cc.getDisplayName()).ccStage("AFTER_FINISH").build());
            }
        }
    }

    private ApprovalTemplateNode copyNode(ApprovalTemplate template, ApprovalTemplateNode oldNode) {
        return ApprovalTemplateNode.builder()
                .templateId(template.getId()).tenantId(template.getTenantId()).nodeOrder(oldNode.getNodeOrder()).name(oldNode.getName())
                .approveMode(oldNode.getApproveMode()).allowTransfer(oldNode.getAllowTransfer()).allowAddSign(oldNode.getAllowAddSign())
                .timeoutHours(oldNode.getTimeoutHours()).remindIntervalHours(oldNode.getRemindIntervalHours())
                .maxRemindCount(oldNode.getMaxRemindCount()).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
    }

    private ApprovalNodeAssignee copyAssignee(ApprovalTemplateNode node, ApprovalNodeAssignee oldAssignee) {
        return ApprovalNodeAssignee.builder().nodeId(node.getId()).tenantId(node.getTenantId())
                .assigneeType(oldAssignee.getAssigneeType()).refId(oldAssignee.getRefId())
                .displayName(oldAssignee.getDisplayName()).build();
    }

    private void clearConfig(Long templateId) {
        var nodes = nodeRepository.findByTemplateIdOrderByNodeOrderAscIdAsc(templateId);
        for (ApprovalTemplateNode node : nodes) {
            assigneeRepository.deleteByNodeId(node.getId());
        }
        ccRepository.deleteByTemplateId(templateId);
        nodeRepository.deleteByTemplateId(templateId);
    }

    private void validateRequest(TemplateSaveRequest request) {
        if (request.getBusinessType() == null || request.getBusinessType().isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "业务类型不能为空");
        if (request.getCode() == null || request.getCode().isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "审批流编码不能为空");
        if (request.getName() == null || request.getName().isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "审批流名称不能为空");
        if (parseTemplateType(request.getTemplateType()) == ApprovalTemplateType.MANUAL) {
            if (request.getNodes() == null || request.getNodes().isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "人工审批流至少需要一个审批节点");
            for (NodeConfigRequest node : request.getNodes()) {
                if (node.getName() == null || node.getName().isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "审批节点名称不能为空");
                if (node.getAssignees() == null || node.getAssignees().isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "审批节点至少需要一个审批人");
            }
        }
    }

    private List<ApprovalTemplateStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) return List.of();
        return List.of(ApprovalTemplateStatus.valueOf(status));
    }

    private ApprovalTemplateType parseTemplateType(String value) {
        return value == null || value.isBlank() ? ApprovalTemplateType.MANUAL : ApprovalTemplateType.valueOf(value);
    }

    private ApprovalRejectPolicy parseRejectPolicy(String value) {
        return value == null || value.isBlank() ? ApprovalRejectPolicy.RETURN_TO_SUBMITTER : ApprovalRejectPolicy.valueOf(value);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private ApprovalTemplate getTenantTemplate(Long id) {
        ApprovalTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审批流不存在"));
        if (!requireTenantId().equals(template.getTenantId())) throw new BusinessException(ErrorCode.FORBIDDEN, "不能操作其他租户的审批流");
        return template;
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少租户上下文");
        return tenantId;
    }

    private TemplateSummaryDTO toSummary(ApprovalTemplate t) {
        return TemplateSummaryDTO.builder().id(t.getId()).tenantId(t.getTenantId()).businessType(t.getBusinessType())
                .code(t.getCode()).name(t.getName()).versionNo(t.getVersionNo()).templateType(t.getTemplateType().name())
                .status(t.getStatus().name()).priority(t.getPriority()).rejectPolicy(t.getRejectPolicy().name())
                .conditionConfig(t.getConditionConfig()).timeoutHours(t.getTimeoutHours())
                .remindIntervalHours(t.getRemindIntervalHours()).maxRemindCount(t.getMaxRemindCount())
                .description(t.getDescription()).publishedAt(t.getPublishedAt()).createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt()).build();
    }

    private TemplateDetailDTO toDetail(ApprovalTemplate t) {
        var nodes = nodeRepository.findByTemplateIdOrderByNodeOrderAscIdAsc(t.getId());
        var allCcs = ccRepository.findByTemplateIdOrderByIdAsc(t.getId());
        var nodeDTOs = new ArrayList<TemplateDetailDTO.NodeDTO>();
        for (ApprovalTemplateNode node : nodes) {
            var assignees = assigneeRepository.findByNodeIdOrderByIdAsc(node.getId());
            nodeDTOs.add(TemplateDetailDTO.NodeDTO.builder()
                    .id(node.getId()).nodeOrder(node.getNodeOrder()).name(node.getName()).approveMode(node.getApproveMode().name())
                    .allowTransfer(node.getAllowTransfer()).allowAddSign(node.getAllowAddSign())
                    .timeoutHours(node.getTimeoutHours()).remindIntervalHours(node.getRemindIntervalHours()).maxRemindCount(node.getMaxRemindCount())
                    .assignees(assignees.stream().map(a -> toAssignee(a, null)).toList())
                    .ccs(allCcs.stream().filter(cc -> node.getId().equals(cc.getNodeId())).map(cc -> toAssignee(null, cc)).toList())
                    .build());
        }
        return TemplateDetailDTO.builder()
                .id(t.getId()).tenantId(t.getTenantId()).businessType(t.getBusinessType()).code(t.getCode())
                .name(t.getName()).versionNo(t.getVersionNo()).templateType(t.getTemplateType().name()).status(t.getStatus().name())
                .priority(t.getPriority()).rejectPolicy(t.getRejectPolicy().name()).conditionConfig(t.getConditionConfig())
                .timeoutHours(t.getTimeoutHours()).remindIntervalHours(t.getRemindIntervalHours()).maxRemindCount(t.getMaxRemindCount())
                .description(t.getDescription()).publishedAt(t.getPublishedAt()).createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt())
                .nodes(nodeDTOs)
                .finishCcs(allCcs.stream().filter(cc -> cc.getNodeId() == null).map(cc -> toAssignee(null, cc)).toList())
                .build();
    }

    private TemplateDetailDTO.AssigneeDTO toAssignee(ApprovalNodeAssignee a, ApprovalTemplateCc cc) {
        if (a != null) {
            return TemplateDetailDTO.AssigneeDTO.builder().id(a.getId()).assigneeType(a.getAssigneeType().name())
                    .refId(a.getRefId()).displayName(a.getDisplayName()).build();
        }
        return TemplateDetailDTO.AssigneeDTO.builder().id(cc.getId()).assigneeType(cc.getCcType().name())
                .refId(cc.getRefId()).displayName(cc.getDisplayName()).ccStage(cc.getCcStage()).build();
    }
}
