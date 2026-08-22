package com.dms.approval.service;

import com.dms.approval.dto.*;
import com.dms.approval.entity.*;
import com.dms.approval.repository.*;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.rbac.entity.UserRole;
import com.dms.rbac.repository.UserRoleRepository;
import com.dms.user.entity.User;
import com.dms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {
    private final ApprovalTemplateRepository templateRepository;
    private final ApprovalTemplateNodeRepository nodeRepository;
    private final ApprovalNodeAssigneeRepository assigneeRepository;
    private final ApprovalTemplateCcRepository templateCcRepository;
    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalTaskRepository taskRepository;
    private final ApprovalRecordRepository recordRepository;
    private final ApprovalCcRecordRepository ccRecordRepository;
    private final ApprovalDelegationRepository delegationRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApprovalNotifier notifier;
    private final List<ApprovalBusinessCallback> callbacks;

    @Transactional
    public ApprovalInstance start(StartApprovalRequest request) {
        UUID tenantId = requireTenantId();
        Long userId = requireUserId();
        User submitter = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "submitter not found"));
        ApprovalTemplate template = matchTemplate(tenantId, request.getBusinessType(), request.getBusinessSnapshot());
        if (template == null) {
            ApprovalInstance auto = ApprovalInstance.builder()
                    .tenantId(tenantId)
                    .businessType(request.getBusinessType())
                    .businessId(request.getBusinessId())
                    .businessCode(request.getBusinessCode())
                    .title(request.getTitle())
                    .submitterId(userId)
                    .submitterName(submitter.getName())
                    .rejectPolicy(ApprovalRejectPolicy.RETURN_TO_SUBMITTER)
                    .templateSnapshot(Map.of("code", "AUTO", "reason", "no matching template"))
                    .businessSnapshot(request.getBusinessSnapshot())
                    .startedAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            auto.setStatus(ApprovalInstanceStatus.AUTO_APPROVED);
            auto.setCurrentNodeName("AUTO_APPROVE");
            auto.setFinishedAt(OffsetDateTime.now());
            auto = instanceRepository.save(auto);
            record(auto, null, "SUBMIT", null, null, userId, submitter.getName(), "no template, auto approved", null);
            record(auto, null, "AUTO_APPROVE", null, null, userId, submitter.getName(), "auto approved (default)", null);
            invokeApproved(auto);
            return auto;
        }

        ApprovalInstance instance = ApprovalInstance.builder()
                .tenantId(tenantId)
                .templateId(template.getId())
                .templateVersionNo(template.getVersionNo())
                .businessType(request.getBusinessType())
                .businessId(request.getBusinessId())
                .businessCode(request.getBusinessCode())
                .title(request.getTitle())
                .submitterId(userId)
                .submitterName(submitter.getName())
                .rejectPolicy(template.getRejectPolicy())
                .templateSnapshot(Map.of("templateId", template.getId(), "versionNo", template.getVersionNo(), "code", String.valueOf(template.getCode())))
                .businessSnapshot(request.getBusinessSnapshot())
                .startedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        if (template.getTemplateType() == ApprovalTemplateType.AUTO_APPROVE) {
            instance.setStatus(ApprovalInstanceStatus.AUTO_APPROVED);
            instance.setCurrentNodeName("AUTO_APPROVE");
            instance.setFinishedAt(OffsetDateTime.now());
            instance = instanceRepository.save(instance);
            record(instance, null, "SUBMIT", null, null, userId, submitter.getName(), "auto matched", null);
            record(instance, null, "AUTO_APPROVE", null, null, userId, submitter.getName(), "auto approved", null);
            createCcRecords(instance, template, null);
            invokeApproved(instance);
            return instance;
        }

        instance.setStatus(ApprovalInstanceStatus.RUNNING);
        instance = instanceRepository.save(instance);
        record(instance, null, "SUBMIT", null, null, userId, submitter.getName(), "submitted", null);
        createCcRecords(instance, template, null);
        activateNextNode(instance, template, 0);
        return instanceRepository.save(instance);
    }
    @Transactional
    public ApprovalTask approve(Long taskId, ApprovalActionRequest request) {
        ApprovalTask task = getTenantTask(taskId);
        if (task.getStatus() != ApprovalTaskStatus.PENDING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "task not pending");
        ApprovalInstance instance = getTenantInstance(task.getInstanceId());
        if (instance.getStatus() != ApprovalInstanceStatus.RUNNING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "instance not running");

        task.setStatus(ApprovalTaskStatus.APPROVED);
        task.setComment(request == null ? null : request.getComment());
        task.setHandledAt(OffsetDateTime.now());
        ApprovalTask savedTask = taskRepository.save(task);
        record(instance, savedTask.getId(), "APPROVE", savedTask.getNodeId(), savedTask.getNodeName(), savedTask.getAssigneeId(), savedTask.getAssigneeName(), savedTask.getComment(), null);

        boolean hasPendingPreSign = taskRepository.findByInstanceIdOrderByIdAsc(instance.getId()).stream()
                .anyMatch(t -> Objects.equals(t.getParentTaskId(), savedTask.getId()) && "PRE_SIGN".equals(t.getTaskType()) && t.getStatus() == ApprovalTaskStatus.PENDING);
        if (hasPendingPreSign) {
            instance.setUpdatedAt(OffsetDateTime.now());
            instanceRepository.save(instance);
            return savedTask;
        }

        ApprovalTemplateNode currentNode = nodeRepository.findById(savedTask.getNodeId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "node not found"));
        List<ApprovalTask> pendingTasks = taskRepository.findByInstanceIdAndStatusOrderByIdAsc(instance.getId(), ApprovalTaskStatus.PENDING);
        boolean nodeFinished = currentNode.getApproveMode() == ApprovalApproveMode.ANY
                || pendingTasks.stream().noneMatch(t -> Objects.equals(t.getNodeId(), savedTask.getNodeId()) && t.getParentTaskId() == null);
        if (currentNode.getApproveMode() == ApprovalApproveMode.ANY && nodeFinished) {
            cancelOtherNodeTasks(instance, savedTask.getNodeId(), savedTask.getId());
        }
        if (nodeFinished) {
            advanceAfterNode(instance, savedTask.getNodeId());
        } else {
            instance.setUpdatedAt(OffsetDateTime.now());
            instanceRepository.save(instance);
        }
        return task;
    }

    @Transactional
    public ApprovalTask reject(Long taskId, ApprovalActionRequest request) {
        ApprovalTask task = getTenantTask(taskId);
        if (task.getStatus() != ApprovalTaskStatus.PENDING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "task not pending");
        ApprovalInstance instance = getTenantInstance(task.getInstanceId());
        if (instance.getStatus() != ApprovalInstanceStatus.RUNNING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "instance not running");
        task.setStatus(ApprovalTaskStatus.REJECTED);
        task.setComment(request == null ? null : request.getComment());
        task.setHandledAt(OffsetDateTime.now());
        taskRepository.save(task);
        cancelPendingTasks(instance, ApprovalTaskStatus.CANCELLED);
        instance.setStatus(instance.getRejectPolicy() == ApprovalRejectPolicy.CANCEL ? ApprovalInstanceStatus.REJECTED : ApprovalInstanceStatus.RETURNED);
        instance.setFinishedAt(OffsetDateTime.now());
        instance.setUpdatedAt(OffsetDateTime.now());
        instanceRepository.save(instance);
        record(instance, task.getId(), "REJECT", task.getNodeId(), task.getNodeName(), task.getAssigneeId(), task.getAssigneeName(), task.getComment(), Map.of("rejectPolicy", instance.getRejectPolicy().name()));
        if (instance.getRejectPolicy() == ApprovalRejectPolicy.CANCEL) {
            invokeRejected(instance);
        } else {
            invokeReturned(instance);
        }
        try { notifier.notifyFinished(instance); } catch (Exception ex) { log.warn("notifyFinished on reject failed: {}", ex.getMessage()); }
        return task;
    }

    @Transactional
    public ApprovalInstance withdraw(Long instanceId, ApprovalActionRequest request) {
        ApprovalInstance instance = getTenantInstance(instanceId);
        Long userId = requireUserId();
        if (!Objects.equals(instance.getSubmitterId(), userId)) throw new BusinessException(ErrorCode.FORBIDDEN, "only submitter can withdraw");
        if (instance.getStatus() != ApprovalInstanceStatus.RUNNING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "instance not running");
        cancelPendingTasks(instance, ApprovalTaskStatus.CANCELLED);
        instance.setStatus(ApprovalInstanceStatus.WITHDRAWN);
        instance.setFinishedAt(OffsetDateTime.now());
        instance.setUpdatedAt(OffsetDateTime.now());
        instanceRepository.save(instance);
        record(instance, null, "WITHDRAW", instance.getCurrentNodeId(), instance.getCurrentNodeName(), userId, TenantContext.getUsername(), request == null ? null : request.getComment(), null);
        return instance;
    }
    @Transactional
    public ApprovalTask transfer(Long taskId, TransferTaskRequest request) {
        ApprovalTask task = getTenantTask(taskId);
        if (task.getStatus() != ApprovalTaskStatus.PENDING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "task not pending");
        User target = requireActiveUser(request.getTargetUserId());
        ApprovalInstance instance = getTenantInstance(task.getInstanceId());
        task.setStatus(ApprovalTaskStatus.TRANSFERRED);
        task.setComment(request.getComment());
        task.setHandledAt(OffsetDateTime.now());
        taskRepository.save(task);
        ApprovalTask newTask = createTask(instance, task.getNodeId(), task.getNodeName(), target, task.getApproveMode(), task.getOriginalAssigneeId(), task.getDelegatedFromUserId(), "TRANSFERRED", task.getId(), task.getParentTaskId());
        record(instance, task.getId(), "TRANSFER", task.getNodeId(), task.getNodeName(), requireUserId(), currentUserName(), request.getComment(), Map.of("fromUserId", task.getAssigneeId(), "toUserId", target.getId()));
        return newTask;
    }

    @Transactional
    public ApprovalTask addSign(Long taskId, AddSignRequest request) {
        ApprovalTask task = getTenantTask(taskId);
        if (task.getStatus() != ApprovalTaskStatus.PENDING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "task not pending");
        if (!"BEFORE".equals(request.getSignType()) && !"AFTER".equals(request.getSignType())) throw new BusinessException(ErrorCode.PARAM_INVALID, "signType must be BEFORE or AFTER");
        User target = requireActiveUser(request.getTargetUserId());
        ApprovalInstance instance = getTenantInstance(task.getInstanceId());
        String taskType = "BEFORE".equals(request.getSignType()) ? "PRE_SIGN" : "POST_SIGN";
        ApprovalTask signTask = createTask(instance, task.getNodeId(), task.getNodeName(), target, ApprovalApproveMode.ANY, target.getId(), null, taskType, task.getId(), null);
        record(instance, task.getId(), "ADD_SIGN", task.getNodeId(), task.getNodeName(), requireUserId(), currentUserName(), request.getComment(), Map.of("signType", request.getSignType(), "targetUserId", target.getId()));
        return signTask;
    }

    @Transactional
    public ApprovalTask reassign(Long taskId, ReassignTaskRequest request) {
        ApprovalTask task = getTenantTask(taskId);
        if (task.getStatus() != ApprovalTaskStatus.PENDING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "task not pending");
        User target = requireActiveUser(request.getTargetUserId());
        ApprovalInstance instance = getTenantInstance(task.getInstanceId());
        task.setStatus(ApprovalTaskStatus.CANCELLED);
        task.setComment("admin reassign: " + request.getReason());
        task.setHandledAt(OffsetDateTime.now());
        taskRepository.save(task);
        ApprovalTask newTask = createTask(instance, task.getNodeId(), task.getNodeName(), target, task.getApproveMode(), target.getId(), null, "ADMIN_REASSIGN", task.getId(), task.getParentTaskId());
        record(instance, task.getId(), "ADMIN_REASSIGN", task.getNodeId(), task.getNodeName(), requireUserId(), currentUserName(), request.getReason(), Map.of("toUserId", target.getId()));
        return newTask;
    }

    @Transactional
    public ApprovalInstance terminate(Long instanceId, TerminateInstanceRequest request) {
        ApprovalInstance instance = getTenantInstance(instanceId);
        if (instance.getStatus() != ApprovalInstanceStatus.RUNNING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "instance not running");
        cancelPendingTasks(instance, ApprovalTaskStatus.TERMINATED);
        instance.setStatus(ApprovalInstanceStatus.TERMINATED);
        instance.setFinishedAt(OffsetDateTime.now());
        instance.setUpdatedAt(OffsetDateTime.now());
        instanceRepository.save(instance);
        record(instance, null, "ADMIN_TERMINATE", instance.getCurrentNodeId(), instance.getCurrentNodeName(), requireUserId(), currentUserName(), request.getReason(), Map.of("result", String.valueOf(request.getResult())));
        return instance;
    }
    @Transactional
    public ApprovalInstance approveBusiness(String businessType, Long businessId, String comment) {
        ApprovalInstance instance = instanceRepository.findFirstByTenantIdAndBusinessTypeAndBusinessIdOrderByIdDesc(requireTenantId(), businessType, businessId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "approval instance not found"));
        if (instance.getStatus() != ApprovalInstanceStatus.RUNNING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "approval instance not running");
        ApprovalTask task = taskRepository.findFirstByInstanceIdAndAssigneeIdAndStatusOrderByIdAsc(instance.getId(), requireUserId(), ApprovalTaskStatus.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "no pending task for current user"));
        ApprovalActionRequest request = new ApprovalActionRequest();
        request.setComment(comment);
        approve(task.getId(), request);
        return getTenantInstance(instance.getId());
    }

    @Transactional
    public ApprovalInstance rejectBusiness(String businessType, Long businessId, String comment) {
        ApprovalInstance instance = instanceRepository.findFirstByTenantIdAndBusinessTypeAndBusinessIdOrderByIdDesc(requireTenantId(), businessType, businessId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "approval instance not found"));
        if (instance.getStatus() != ApprovalInstanceStatus.RUNNING) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "approval instance not running");
        ApprovalTask task = taskRepository.findFirstByInstanceIdAndAssigneeIdAndStatusOrderByIdAsc(instance.getId(), requireUserId(), ApprovalTaskStatus.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "no pending task for current user"));
        ApprovalActionRequest request = new ApprovalActionRequest();
        request.setComment(comment);
        reject(task.getId(), request);
        return getTenantInstance(instance.getId());
    }

    @Transactional(readOnly = true)
    public PageResult<ApprovalTask> myTodo(PageQuery pageQuery) {
        UUID tenantId = requireTenantId();
        Long userId = requireUserId();
        Page<ApprovalTask> page = taskRepository.findByTenantIdAndAssigneeIdAndStatusOrderByCreatedAtDesc(tenantId, userId, ApprovalTaskStatus.PENDING, PageRequest.of(pageQuery.getPage() - 1, pageQuery.getSize(), Sort.by(Sort.Direction.DESC, "createdAt")));
        enrichTaskInstances(page.getContent());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public PageResult<ApprovalTask> myDone(PageQuery pageQuery) {
        UUID tenantId = requireTenantId();
        Long userId = requireUserId();
        Page<ApprovalTask> page = taskRepository.findByTenantIdAndAssigneeIdAndStatusNotOrderByHandledAtDesc(tenantId, userId, ApprovalTaskStatus.PENDING, PageRequest.of(pageQuery.getPage() - 1, pageQuery.getSize(), Sort.by(Sort.Direction.DESC, "handledAt")));
        enrichTaskInstances(page.getContent());
        return PageResult.of(page);
    }

    private void enrichTaskInstances(List<ApprovalTask> tasks) {
        if (tasks == null || tasks.isEmpty()) return;
        List<Long> instanceIds = tasks.stream().map(ApprovalTask::getInstanceId).filter(Objects::nonNull).distinct().toList();
        if (instanceIds.isEmpty()) return;
        Map<Long, ApprovalInstance> instanceMap = new HashMap<>();
        for (ApprovalInstance inst : instanceRepository.findAllById(instanceIds)) {
            instanceMap.put(inst.getId(), inst);
        }
        for (ApprovalTask task : tasks) {
            ApprovalInstance inst = instanceMap.get(task.getInstanceId());
            if (inst != null) {
                task.setTitle(inst.getTitle());
                task.setBusinessType(inst.getBusinessType());
                task.setBusinessCode(inst.getBusinessCode());
                task.setSubmitterName(inst.getSubmitterName());
                task.setInstanceStatus(inst.getStatus() == null ? null : inst.getStatus().name());
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResult<ApprovalInstance> mySubmitted(PageQuery pageQuery) {
        return PageResult.of(instanceRepository.findByTenantIdAndSubmitterIdOrderByIdDesc(requireTenantId(), requireUserId(), pageQuery.toPageable()));
    }

    @Transactional(readOnly = true)
    public PageResult<ApprovalCcRecord> myCc(PageQuery pageQuery) {
        return PageResult.of(ccRecordRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(requireTenantId(), requireUserId(), pageQuery.toPageable()));
    }

    @Transactional(readOnly = true)
    public PageResult<ApprovalInstance> adminInstances(PageQuery pageQuery, String status) {
        UUID tenantId = requireTenantId();
        if (status != null && !status.isBlank()) {
            return PageResult.of(instanceRepository.findByTenantIdAndStatusOrderByIdDesc(tenantId, ApprovalInstanceStatus.valueOf(normalizeInstanceStatus(status)), pageQuery.toPageable()));
        }
        return PageResult.of(instanceRepository.findByTenantIdOrderByIdDesc(tenantId, pageQuery.toPageable()));
    }

    private String normalizeInstanceStatus(String status) {
        return switch (status.trim().toUpperCase()) {
            case "PENDING", "IN_PROGRESS" -> "RUNNING";
            case "PASSED" -> "APPROVED";
            case "REFUSED" -> "REJECTED";
            case "CANCELED" -> "WITHDRAWN";
            default -> status.trim().toUpperCase();
        };
    }

    @Transactional(readOnly = true)
    public ApprovalInstance getInstance(Long instanceId) {
        return getTenantInstance(instanceId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalTask> getInstanceTasks(Long instanceId) {
        getTenantInstance(instanceId);
        return taskRepository.findByInstanceIdOrderByIdAsc(instanceId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRecord> getInstanceRecords(Long instanceId) {
        getTenantInstance(instanceId);
        return recordRepository.findByInstanceIdOrderByCreatedAtAscIdAsc(instanceId);
    }

    @Transactional(readOnly = true)
    public ApprovalInstance latestInstance(String businessType, Long businessId) {
        return instanceRepository.findFirstByTenantIdAndBusinessTypeAndBusinessIdOrderByIdDesc(requireTenantId(), businessType, businessId).orElse(null);
    }
    private void activateNextNode(ApprovalInstance instance, ApprovalTemplate template, int afterOrder) {
        List<ApprovalTemplateNode> nodes = nodeRepository.findByTemplateIdOrderByNodeOrderAscIdAsc(template.getId());
        ApprovalTemplateNode next = nodes.stream().filter(n -> n.getNodeOrder() > afterOrder).min(Comparator.comparing(ApprovalTemplateNode::getNodeOrder)).orElse(null);
        if (next == null) {
            instance.setStatus(ApprovalInstanceStatus.APPROVED);
            instance.setCurrentNodeId(null);
            instance.setCurrentNodeName(null);
            instance.setFinishedAt(OffsetDateTime.now());
            instance.setUpdatedAt(OffsetDateTime.now());
            instanceRepository.save(instance);
            record(instance, null, "APPROVE_FINISH", null, null, instance.getSubmitterId(), instance.getSubmitterName(), "approved", null);
            createCcRecords(instance, template, null);
            try { notifier.notifyFinished(instance); } catch (Exception ex) { log.warn("notifyFinished failed: {}", ex.getMessage()); }
            invokeApproved(instance);
            return;
        }
        activateNode(instance, template, next);
    }

    private void advanceAfterNode(ApprovalInstance instance, Long nodeId) {
        ApprovalTemplate template = templateRepository.findById(instance.getTemplateId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "template not found"));
        ApprovalTemplateNode node = nodeRepository.findById(nodeId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "node not found"));
        createCcRecords(instance, template, node);
        activateNextNode(instance, template, node.getNodeOrder());
    }

    private void activateNode(ApprovalInstance instance, ApprovalTemplate template, ApprovalTemplateNode node) {
        List<User> users = resolveNodeUsers(template.getTenantId(), node.getId());
        if (users.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "no approver for node " + node.getName());
        instance.setCurrentNodeId(node.getId());
        instance.setCurrentNodeName(node.getName());
        instance.setStatus(ApprovalInstanceStatus.RUNNING);
        instance.setUpdatedAt(OffsetDateTime.now());
        instanceRepository.save(instance);
        for (User user : users) {
            User actual = resolveDelegate(user);
            ApprovalTask task = createTask(instance, node.getId(), node.getName(), actual, node.getApproveMode(), user.getId(),
                    actual.getId().equals(user.getId()) ? null : user.getId(), "NORMAL", null, null);
            if (node.getTimeoutHours() != null && node.getTimeoutHours() > 0) {
                task.setDueAt(OffsetDateTime.now().plusHours(node.getTimeoutHours()));
                taskRepository.save(task);
            }
            try { notifier.notifyTaskCreated(task, instance); } catch (Exception ex) { log.warn("notifyTaskCreated failed: {}", ex.getMessage()); }
        }
        record(instance, null, "NODE_START", node.getId(), node.getName(), null, null, "node started", null);
    }

    private List<User> resolveNodeUsers(UUID tenantId, Long nodeId) {
        Map<Long, User> users = new LinkedHashMap<>();
        for (ApprovalNodeAssignee config : assigneeRepository.findByNodeIdOrderByIdAsc(nodeId)) {
            if (config.getAssigneeType() == ApprovalAssigneeType.USER) {
                userRepository.findById(config.getRefId()).filter(u -> tenantId.equals(u.getTenantId()) && "active".equals(u.getStatus())).ifPresent(u -> users.put(u.getId(), u));
            } else {
                for (UserRole userRole : userRoleRepository.findByRoleId(config.getRefId())) {
                    userRepository.findById(userRole.getUserId()).filter(u -> tenantId.equals(u.getTenantId()) && "active".equals(u.getStatus())).ifPresent(u -> users.put(u.getId(), u));
                }
            }
        }
        return new ArrayList<>(users.values());
    }

    private User resolveDelegate(User user) {
        OffsetDateTime now = OffsetDateTime.now();
        return delegationRepository.findByTenantIdAndDelegatorIdAndStatusAndStartsAtBeforeAndEndsAtAfter(user.getTenantId(), user.getId(), "ACTIVE", now, now)
                .stream().findFirst().flatMap(d -> userRepository.findById(d.getDelegateeId())).orElse(user);
    }
    private ApprovalTask createTask(ApprovalInstance instance, Long nodeId, String nodeName, User assignee,
                                    ApprovalApproveMode approveMode, Long originalAssigneeId, Long delegatedFromUserId,
                                    String taskType, Long sourceTaskId, Long parentTaskId) {
        Long actualParentTaskId = parentTaskId;
        if (actualParentTaskId == null && "PRE_SIGN".equals(taskType)) {
            actualParentTaskId = sourceTaskId;
        }
        return taskRepository.save(ApprovalTask.builder()
                .instanceId(instance.getId()).tenantId(instance.getTenantId()).nodeId(nodeId).nodeName(nodeName)
                .assigneeId(assignee.getId()).assigneeName(assignee.getName())
                .originalAssigneeId(originalAssigneeId).delegatedFromUserId(delegatedFromUserId)
                .taskType(taskType).parentTaskId(actualParentTaskId).status(ApprovalTaskStatus.PENDING)
                .approveMode(approveMode).remindedCount(0).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build());
    }

    private void cancelOtherNodeTasks(ApprovalInstance instance, Long nodeId, Long approvedTaskId) {
        for (ApprovalTask task : taskRepository.findByInstanceIdAndStatusOrderByIdAsc(instance.getId(), ApprovalTaskStatus.PENDING)) {
            if (Objects.equals(task.getNodeId(), nodeId) && !Objects.equals(task.getId(), approvedTaskId) && task.getParentTaskId() == null) {
                task.setStatus(ApprovalTaskStatus.CANCELLED);
                task.setComment("cancelled by another approval");
                task.setHandledAt(OffsetDateTime.now());
                taskRepository.save(task);
            }
        }
    }

    private void cancelPendingTasks(ApprovalInstance instance, ApprovalTaskStatus targetStatus) {
        for (ApprovalTask task : taskRepository.findByInstanceIdAndStatusOrderByIdAsc(instance.getId(), ApprovalTaskStatus.PENDING)) {
            task.setStatus(targetStatus);
            task.setComment("cancelled");
            task.setHandledAt(OffsetDateTime.now());
            taskRepository.save(task);
        }
    }

    private void createCcRecords(ApprovalInstance instance, ApprovalTemplate template, ApprovalTemplateNode node) {
        List<ApprovalTemplateCc> ccs = templateCcRepository.findByTemplateIdOrderByIdAsc(template.getId()).stream()
                .filter(cc -> node == null ? cc.getNodeId() == null : Objects.equals(cc.getNodeId(), node.getId()))
                .toList();
        for (ApprovalTemplateCc cc : ccs) {
            for (User user : resolveCcUsers(instance.getTenantId(), cc)) {
                ccRecordRepository.save(ApprovalCcRecord.builder()
                        .instanceId(instance.getId()).tenantId(instance.getTenantId())
                        .userId(user.getId()).userName(user.getName())
                        .stage(node == null ? "AFTER_FINISH" : "NODE")
                        .nodeId(node == null ? null : node.getId())
                        .createdAt(OffsetDateTime.now()).build());
                try { notifier.notifyCc(user.getId(), instance, node == null ? "AFTER_FINISH" : "NODE"); } catch (Exception ex) { log.warn("notifyCc failed: {}", ex.getMessage()); }
            }
        }
    }

    private List<User> resolveCcUsers(UUID tenantId, ApprovalTemplateCc cc) {
        Map<Long, User> users = new LinkedHashMap<>();
        if (cc.getCcType() == ApprovalAssigneeType.USER) {
            userRepository.findById(cc.getRefId()).filter(u -> tenantId.equals(u.getTenantId())).ifPresent(u -> users.put(u.getId(), u));
        } else {
            for (UserRole userRole : userRoleRepository.findByRoleId(cc.getRefId())) {
                userRepository.findById(userRole.getUserId()).filter(u -> tenantId.equals(u.getTenantId())).ifPresent(u -> users.put(u.getId(), u));
            }
        }
        return new ArrayList<>(users.values());
    }
    private ApprovalTemplate matchTemplate(UUID tenantId, String businessType, Map<String, Object> snapshot) {
        List<ApprovalTemplate> templates = templateRepository.findByTenantIdAndBusinessTypeAndStatusOrderByPriorityDescVersionNoDescIdDesc(
                tenantId, businessType, ApprovalTemplateStatus.ENABLED);
        for (ApprovalTemplate template : templates) {
            if (matchesCondition(template.getConditionConfig(), snapshot)) return template;
        }
        return null;
    }

    private boolean matchesCondition(Map<String, Object> condition, Map<String, Object> snapshot) {
        if (condition == null || condition.isEmpty()) return true;
        Object rulesObj = condition.get("rules");
        if (!(rulesObj instanceof List<?> rules)) return true;
        String logic = String.valueOf(condition.getOrDefault("logic", "AND")).toUpperCase(Locale.ROOT);
        boolean result = "OR".equals(logic);
        for (Object ruleObj : rules) {
            if (!(ruleObj instanceof Map<?, ?> rawRule)) continue;
            Map<String, Object> rule = new HashMap<>();
            rawRule.forEach((k, v) -> rule.put(String.valueOf(k), v));
            boolean matched = matchSingleRule(rule, snapshot);
            if ("OR".equals(logic)) result = result || matched;
            else result = result && matched;
        }
        return result;
    }

    private boolean matchSingleRule(Map<String, Object> rule, Map<String, Object> snapshot) {
        if (snapshot == null) return true;
        String field = String.valueOf(rule.get("field"));
        String operator = String.valueOf(rule.getOrDefault("operator", "EQ")).toUpperCase(Locale.ROOT);
        Object expected = rule.get("value");
        Object actual = snapshot == null ? null : snapshot.get(field);
        if ("EQ".equals(operator)) return Objects.equals(String.valueOf(actual), String.valueOf(expected));
        if ("NE".equals(operator)) return !Objects.equals(String.valueOf(actual), String.valueOf(expected));
        if ("IN".equals(operator) && expected instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).anyMatch(v -> v.equals(String.valueOf(actual)));
        }
        BigDecimal a = toBigDecimal(actual);
        BigDecimal b = toBigDecimal(expected);
        if (a == null || b == null) return false;
        int cmp = a.compareTo(b);
        return switch (operator) {
            case "GT" -> cmp > 0;
            case "GTE" -> cmp >= 0;
            case "LT" -> cmp < 0;
            case "LTE" -> cmp <= 0;
            default -> false;
        };
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        try { return new BigDecimal(String.valueOf(value)); } catch (Exception e) { return null; }
    }

    private void record(ApprovalInstance instance, Long taskId, String action, Long nodeId, String nodeName, Long operatorId, String operatorName, String comment, Map<String, Object> payload) {
        recordRepository.save(ApprovalRecord.builder()
                .instanceId(instance.getId()).taskId(taskId).tenantId(instance.getTenantId())
                .action(action).nodeId(nodeId).nodeName(nodeName)
                .operatorId(operatorId).operatorName(operatorName).comment(comment).payload(payload)
                .createdAt(OffsetDateTime.now()).build());
    }


    private void invokeApproved(ApprovalInstance instance) {
        for (ApprovalBusinessCallback callback : callbacks) {
            if (callback.supports(instance.getBusinessType())) callback.onApproved(instance);
        }
    }

    private void invokeReturned(ApprovalInstance instance) {
        for (ApprovalBusinessCallback callback : callbacks) {
            if (callback.supports(instance.getBusinessType())) callback.onReturned(instance);
        }
    }

    private void invokeRejected(ApprovalInstance instance) {
        for (ApprovalBusinessCallback callback : callbacks) {
            if (callback.supports(instance.getBusinessType())) callback.onRejected(instance);
        }
    }

    private void invokeWithdrawn(ApprovalInstance instance) {
        for (ApprovalBusinessCallback callback : callbacks) {
            if (callback.supports(instance.getBusinessType())) callback.onWithdrawn(instance);
        }
    }

    private void invokeTerminated(ApprovalInstance instance, String result) {
        for (ApprovalBusinessCallback callback : callbacks) {
            if (callback.supports(instance.getBusinessType())) callback.onTerminated(instance, result);
        }
    }

    private ApprovalTask getTenantTask(Long taskId) {
        ApprovalTask task = taskRepository.findById(taskId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "task not found"));
        if (!requireTenantId().equals(task.getTenantId())) throw new BusinessException(ErrorCode.FORBIDDEN, "task tenant mismatch");
        if (!Objects.equals(task.getAssigneeId(), requireUserId())) throw new BusinessException(ErrorCode.FORBIDDEN, "only assignee can process task");
        return task;
    }

    private ApprovalInstance getTenantInstance(Long instanceId) {
        ApprovalInstance instance = instanceRepository.findById(instanceId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "instance not found"));
        if (!requireTenantId().equals(instance.getTenantId())) throw new BusinessException(ErrorCode.FORBIDDEN, "instance tenant mismatch");
        return instance;
    }

    private User requireActiveUser(Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "userId required");
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "user not found"));
        if (!requireTenantId().equals(user.getTenantId())) throw new BusinessException(ErrorCode.FORBIDDEN, "user tenant mismatch");
        if (!"active".equals(user.getStatus())) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "user inactive");
        return user;
    }

    private String currentUserName() {
        return userRepository.findById(requireUserId()).map(User::getName).orElse(TenantContext.getUsername());
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "tenant context missing");
        return tenantId;
    }

    private Long requireUserId() {
        Long userId = TenantContext.getUserId();
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "user context missing");
        return userId;
    }
}
