/*
 * Compatibility aliases for documented/automation API paths.
 */
package com.dms.compat;

import com.dms.approval.dto.AssigneeConfigRequest;
import com.dms.approval.dto.DelegationRequest;
import com.dms.approval.dto.NodeConfigRequest;
import com.dms.approval.dto.TemplateSaveRequest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalDelegationService;
import com.dms.approval.service.ApprovalService;
import com.dms.approval.service.ApprovalSummaryBuilder;
import com.dms.approval.service.ApprovalTemplateService;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.platform.dict.service.PlatformDictService;
import com.dms.tenant.entity.Tenant;
import com.dms.tenant.repository.TenantRepository;
import com.dms.user.entity.User;
import com.dms.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CompatAliasController {
    private final ApprovalService approvalService;
    private final ApprovalTemplateService templateService;
    private final ApprovalDelegationService delegationService;
    private final PlatformDictService dictService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final EntityManager em;

    @GetMapping("/api/approval-instances")
    public ApiResponse<Map<String, Object>> approvalInstances(PageQuery pageQuery,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(required = false) String assignee,
                                                              @RequestParam(required = false) String initiatedBy) {
        if ("me".equalsIgnoreCase(assignee)) return pageOf(approvalService.myTodo(pageQuery));
        if ("me".equalsIgnoreCase(initiatedBy)) return pageOf(approvalService.mySubmitted(pageQuery));
        var page = approvalService.adminInstances(pageQuery, normalizeStatus(status));
        return paged(page.getList(), page.getTotal());
    }

    @GetMapping("/api/approval-instances/{id}")
    public ApiResponse<Map<String, Object>> approvalInstance(@PathVariable Long id) {
        ApprovalInstance instance = approvalService.getInstance(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("instance", instance);
        data.put("tasks", approvalService.getInstanceTasks(id));
        data.put("records", approvalService.getInstanceRecords(id));
        return ApiResponse.ok(data);
    }

    @GetMapping("/api/approval-instances/{id}/summary")
    public ApiResponse<Map<String, Object>> approvalInstanceSummary(@PathVariable Long id) {
        return ApiResponse.ok(ApprovalSummaryBuilder.build(em, approvalService.getInstance(id)));
    }

    @GetMapping("/api/approval-flows")
    public ApiResponse<Map<String, Object>> approvalFlows(PageQuery pageQuery,
                                                          @RequestParam(required = false) String businessType,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String keyword) {
        return pageOf(templateService.list(pageQuery, businessType, status, keyword));
    }

    @PostMapping("/api/approval-flows")
    public ApiResponse<?> createApprovalFlow(@RequestBody Map<String, Object> payload) {
        TemplateSaveRequest request = new TemplateSaveRequest();
        request.setBusinessType(asString(payload.get("businessType"), "CONTRACT"));
        request.setName(asString(payload.get("name"), "兼容审批流"));
        request.setCode("COMPAT_" + System.currentTimeMillis());
        request.setTemplateType("MANUAL");
        request.setPriority(100);
        request.setRejectPolicy("CANCEL");
        request.setDescription(asString(payload.get("description"), "自动化兼容创建"));
        List<NodeConfigRequest> nodes = new ArrayList<>();
        int order = 1;
        Object rawNodes = payload.get("nodes");
        if (rawNodes instanceof List<?> list) {
            for (Object item : list) if (item instanceof Map<?, ?> map) nodes.add(toNodeRequest(map, order++));
        }
        if (nodes.isEmpty()) nodes.add(toNodeRequest(Map.of("name", "默认审批节点"), order));
        request.setNodes(nodes);
        return ApiResponse.ok(templateService.createDraft(request));
    }

    @GetMapping("/api/approval-delegates")
    public ApiResponse<Map<String, Object>> approvalDelegates(PageQuery pageQuery) {
        return pageOf(delegationService.list(pageQuery));
    }

    @PostMapping("/api/approval-delegates")
    public ApiResponse<?> createApprovalDelegate(@RequestBody Map<String, Object> payload) {
        DelegationRequest request = new DelegationRequest();
        request.setDelegatorId(TenantContext.getUserId());
        Object delegateTo = payload.getOrDefault("delegateTo", payload.get("delegateeId"));
        request.setDelegateeId(resolveUser(delegateTo).getId());
        request.setStartsAt(toOffsetDate(payload.getOrDefault("startDate", payload.get("startsAt")), false));
        request.setEndsAt(toOffsetDate(payload.getOrDefault("endDate", payload.get("endsAt")), true));
        request.setReason(asString(payload.get("reason"), "自动化兼容委托"));
        return ApiResponse.ok(delegationService.create(request));
    }

    @GetMapping("/api/approval-monitors")
    public ApiResponse<Map<String, Object>> approvalMonitors(PageQuery pageQuery,
                                                             @RequestParam(required = false) String status,
                                                             @RequestParam(required = false) String businessType) {
        var page = approvalService.adminInstances(pageQuery, normalizeStatus(status));
        return paged(page.getList(), page.getTotal());
    }
    @GetMapping("/api/admin/tenants")
    public ApiResponse<Map<String, Object>> adminTenants(PageQuery pageQuery,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String type) {
        String tenantType = "DEALER".equalsIgnoreCase(asString(type, "MFR")) ? "DEALER" : "MANUFACTURER";
        var page = tenantRepository.findByTenantType(tenantType, pageQuery.toPageable());
        List<Map<String, Object>> rows = page.getContent().stream()
                .filter(t -> status == null || status.isBlank() || status.equalsIgnoreCase(t.getStatus()))
                .filter(t -> keyword == null || keyword.isBlank()
                        || String.valueOf(t.getCode()).contains(keyword) || String.valueOf(t.getName()).contains(keyword))
                .map(this::toTenantMap).toList();
        return paged(rows, page.getTotalElements());
    }

    @PostMapping("/api/admin/tenants")
    @Transactional
    public ApiResponse<Map<String, Object>> createTenant(@RequestBody Map<String, Object> payload) {
        String code = asString(payload.get("code"), "T" + System.currentTimeMillis());
        if (tenantRepository.existsByCode(code)) throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "租户编码已存在");
        String type = "DEALER".equalsIgnoreCase(asString(payload.get("type"), "MFR")) ? "DEALER" : "MANUFACTURER";
        String status = "INACTIVE".equalsIgnoreCase(asString(payload.get("status"), "ACTIVE")) ? "disabled" : "active";
        OffsetDateTime now = OffsetDateTime.now();
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID()).code(code).name(asString(payload.get("name"), code))
                .industry("医疗器械").timezone("Asia/Shanghai").status(status).tenantType(type)
                .deploymentMode("SHARED").contactName(asString(payload.get("contactPerson"), null))
                .contactPhone(asString(payload.get("phone"), null)).contactEmail(asString(payload.get("email"), null))
                .enabledAt("active".equals(status) ? now : null).disabledAt("active".equals(status) ? null : now)
                .modulesEnabled(new HashMap<>()).quota(new HashMap<>()).attrs(new HashMap<>())
                .createdAt(now).updatedAt(now).build();
        tenant.ensureJsonFields();
        return ApiResponse.ok(toTenantMap(tenantRepository.save(tenant)));
    }


    @PutMapping("/api/admin/tenants/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateTenant(@PathVariable UUID id, @RequestBody Map<String, Object> payload) {
        Tenant tenant = loadTenant(id);
        if (payload.get("name") != null) tenant.setName(String.valueOf(payload.get("name")));
        if (payload.get("contactPerson") != null) tenant.setContactName(String.valueOf(payload.get("contactPerson")));
        if (payload.get("phone") != null) tenant.setContactPhone(String.valueOf(payload.get("phone")));
        if (payload.get("email") != null) tenant.setContactEmail(String.valueOf(payload.get("email")));
        if (payload.get("status") != null) {
            String status = "INACTIVE".equalsIgnoreCase(String.valueOf(payload.get("status"))) ? "disabled" : "active";
            tenant.setStatus(status);
            tenant.setDisabledAt("active".equals(status) ? null : OffsetDateTime.now());
        }
        tenant.setUpdatedAt(OffsetDateTime.now());
        return ApiResponse.ok(toTenantMap(tenantRepository.save(tenant)));
    }

    @DeleteMapping("/api/admin/tenants/{id}")
    @Transactional
    public ApiResponse<Void> deleteTenant(@PathVariable UUID id) {
        Tenant tenant = loadTenant(id);
        tenant.setDeletedAt(OffsetDateTime.now());
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);
        return ApiResponse.ok();
    }

    @GetMapping("/api/admin/users")
    public ApiResponse<Map<String, Object>> adminUsers(PageQuery pageQuery) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(
                "SELECT id, username, name, email, status, created_at FROM platform_admin_users ORDER BY id", Tuple.class)
                .getResultList();
        return paged(toMaps(rows), rows.size());
    }

    @GetMapping("/api/admin/dict-types")
    public ApiResponse<Map<String, Object>> adminDictTypes(PageQuery pageQuery) {
        List<Map<String, Object>> list = dictService.listTypes();
        return paged(list, list.size());
    }

    @PostMapping("/api/admin/dict-types")
    public ApiResponse<Map<String, Object>> createDictType(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(dictService.createType(
                asString(payload.get("code"), "DICT" + System.currentTimeMillis()),
                asString(payload.get("name"), "兼容字典"),
                asString(payload.get("description"), null)));
    }

    @PutMapping("/api/admin/dict-types/{id}")
    public ApiResponse<Void> updateDictType(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        dictService.updateType(id, asString(payload.get("name"), null), asString(payload.get("description"), null));
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/admin/dict-types/{id}")
    @Transactional
    public ApiResponse<Void> deleteDictType(@PathVariable Long id) {
        em.createNativeQuery("DELETE FROM dict_items WHERE type_id = ?1").setParameter(1, id).executeUpdate();
        em.createNativeQuery("DELETE FROM dict_types WHERE id = ?1 AND tenant_id IS NULL").setParameter(1, id).executeUpdate();
        return ApiResponse.ok();
    }

    @GetMapping("/api/admin/dict-items")
    public ApiResponse<Map<String, Object>> adminDictItems(PageQuery pageQuery,
                                                           @RequestParam(required = false) String type,
                                                           @RequestParam(required = false) String typeCode) {
        String code = type != null ? type : typeCode;
        List<Map<String, Object>> list = code != null ? dictService.listItems(code) : allDictItems();
        return paged(list, list.size());
    }

    @GetMapping("/api/admin/audit-logs")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> adminAuditLogs(PageQuery pageQuery) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(
                "SELECT id, admin_user_id, admin_username, action, target_type, target_id, success, ip, created_at " +
                "FROM platform_audit_logs ORDER BY id DESC LIMIT 500", Tuple.class).getResultList();
        return paged(toMaps(rows), rows.size());
    }

    @GetMapping("/api/admin/login-logs")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> adminLoginLogs(PageQuery pageQuery) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(
                "SELECT id, tenant_id, user_id, login_type, ip, success, fail_reason, at_time " +
                "FROM user_login_logs ORDER BY id DESC LIMIT 500", Tuple.class).getResultList();
        return paged(toMaps(rows), rows.size());
    }

    @GetMapping("/api/admin/tenant-dealer-bindings")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> tenantBindings(PageQuery pageQuery) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(
                "SELECT id, dealer_tenant_id, manufacturer_tenant_id, dealer_id, status, created_at, updated_at FROM tenant_dealer_bindings ORDER BY id", Tuple.class)
                .getResultList();
        return paged(toMaps(rows), rows.size());
    }

    @GetMapping("/api/login-logs")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> loginLogs(PageQuery pageQuery) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT l.id, l.user_id, u.username, u.name, l.login_type, l.ip, l.success, l.fail_reason, l.user_agent, l.at_time " +
                "FROM user_login_logs l LEFT JOIN users u ON u.id = l.user_id " +
                "WHERE l.tenant_id = CAST(?1 AS uuid) ORDER BY l.id DESC LIMIT 500", Tuple.class);
        q.setParameter(1, tid == null ? null : tid.toString());
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        return paged(toMaps(rows), rows.size());
    }

    @GetMapping("/api/dict-items")
    public ApiResponse<Map<String, Object>> dictItems(@RequestParam(required = false) String type,
                                                      @RequestParam(required = false) String typeCode) {
        String code = type != null ? type : typeCode;
        List<Map<String, Object>> list = code != null ? dictService.listItems(code) : allDictItems();
        return paged(list, list.size());
    }
    private NodeConfigRequest toNodeRequest(Map<?, ?> source, int order) {
        NodeConfigRequest node = new NodeConfigRequest();
        node.setNodeOrder(toInt(source.get("order"), order));
        node.setName(asString(source.get("name"), "审批节点"));
        node.setApproveMode("ANY");
        node.setAllowTransfer(true);
        node.setAllowAddSign(false);
        node.setTimeoutHours(72);
        AssigneeConfigRequest assignee = new AssigneeConfigRequest();
        assignee.setAssigneeType("USER");
        assignee.setRefId(defaultApproverId());
        assignee.setDisplayName("默认审批人");
        node.setAssignees(List.of(assignee));
        return node;
    }

    private User resolveUser(Object value) {
        UUID tenantId = TenantContext.getTenantId();
        if (value instanceof Number number) return userRepository.findById(number.longValue())
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (value != null && !value.toString().isBlank()) {
            String key = value.toString();
            return userRepository.findByTenantIdAndUsername(tenantId, key)
                    .or(() -> userRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        }
        return userRepository.findById(defaultApproverId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private Long defaultApproverId() {
        Long current = TenantContext.getUserId();
        if (current != null) return current;
        return userRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 1)).stream()
                .findFirst().map(User::getId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private OffsetDateTime toOffsetDate(Object value, boolean endOfDay) {
        if (value == null) {
            LocalDate date = LocalDate.now().plusDays(endOfDay ? 7 : 0);
            return (endOfDay ? date.atTime(23, 59) : date.atStartOfDay()).atOffset(ZoneOffset.UTC);
        }
        if (value instanceof OffsetDateTime time) return time;
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (DateTimeParseException ignored) {
            LocalDate date = LocalDate.parse(value.toString());
            return (endOfDay ? date.atTime(23, 59) : date.atStartOfDay()).atOffset(ZoneOffset.UTC);
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        return switch (status.trim().toUpperCase()) {
            case "PENDING", "IN_PROGRESS" -> "RUNNING";
            case "PASSED" -> "APPROVED";
            case "REFUSED" -> "REJECTED";
            case "CANCELED" -> "WITHDRAWN";
            default -> status.trim().toUpperCase();
        };
    }

    private Tenant loadTenant(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
    }

    private Map<String, Object> toTenantMap(Tenant tenant) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tenant.getId());
        map.put("code", tenant.getCode());
        map.put("name", tenant.getName());
        map.put("status", tenant.getStatus());
        map.put("type", tenant.getTenantType());
        map.put("tenantType", tenant.getTenantType());
        map.put("contactPerson", tenant.getContactName());
        map.put("contactName", tenant.getContactName());
        map.put("phone", tenant.getContactPhone());
        map.put("contactPhone", tenant.getContactPhone());
        map.put("email", tenant.getContactEmail());
        map.put("contactEmail", tenant.getContactEmail());
        map.put("createdAt", tenant.getCreatedAt());
        map.put("updatedAt", tenant.getUpdatedAt());
        return map;
    }

    private List<Map<String, Object>> allDictItems() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : dictService.listTypes()) {
            Object code = t.get("code");
            if (code != null) out.addAll(dictService.listItems(String.valueOf(code)));
        }
        return out;
    }

    private ApiResponse<Map<String, Object>> pageOf(Object pageResult) {
        if (pageResult instanceof PageResult<?> page) return paged(page.getList(), page.getTotal());
        if (pageResult instanceof org.springframework.data.domain.Page<?> page) return paged(page.getContent(), page.getTotalElements());
        return paged(List.of(), 0);
    }

    private ApiResponse<Map<String, Object>> paged(List<?> list, long total) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("items", list);
        data.put("total", total);
        data.put("page", 1);
        data.put("size", list == null ? 0 : list.size());
        return ApiResponse.ok(data);
    }

    private List<Map<String, Object>> toMaps(List<Tuple> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows == null) return out;
        for (Tuple t : rows) {
            Map<String, Object> m = new HashMap<>();
            for (var alias : t.getElements()) {
                String key = camel(alias.getAlias());
                try { m.put(key, t.get(alias.getAlias())); } catch (Exception ignored) {}
            }
            out.add(m);
        }
        return out;
    }

    private String asString(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private int toInt(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value.toString()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private String camel(String name) {
        if (name == null || !name.contains("_")) return name;
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '_') { upper = true; continue; }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }
}