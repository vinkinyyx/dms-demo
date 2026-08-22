package com.dms.compat;

import com.dms.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import com.dms.compat.service.CompatAliasService;

@RestController
@RequiredArgsConstructor
@Validated
public class CompatAliasController {

    private final CompatAliasService service;

    @GetMapping("/api/approval-instances")
    public ApiResponse<Map<String, Object>> approvalInstances(PageQuery pageQuery, @RequestParam(required = false) String status, @RequestParam(required = false) String assignee, @RequestParam(required = false) String initiatedBy) {
        return service.approvalInstances(pageQuery, status, assignee, initiatedBy);
    }

    @GetMapping("/api/approval-instances/{id}")
    public ApiResponse<Map<String, Object>> approvalInstance(@PathVariable Long id) {
        return service.approvalInstance(id);
    }

    @GetMapping("/api/approval-instances/{id}/summary")
    public ApiResponse<Map<String, Object>> approvalInstanceSummary(@PathVariable Long id) {
        return service.approvalInstanceSummary(id);
    }

    @GetMapping("/api/approval-flows")
    public ApiResponse<Map<String, Object>> approvalFlows(PageQuery pageQuery, @RequestParam(required = false) String businessType, @RequestParam(required = false) String status, @RequestParam(required = false) String keyword) {
        return service.approvalFlows(pageQuery, businessType, status, keyword);
    }

    @PostMapping("/api/approval-flows")
    public ApiResponse<?> createApprovalFlow(@RequestBody Map<String, Object> payload) {
        return service.createApprovalFlow(payload);
    }

    @GetMapping("/api/approval-delegates")
    public ApiResponse<Map<String, Object>> approvalDelegates(PageQuery pageQuery) {
        return service.approvalDelegates(pageQuery);
    }

    @PostMapping("/api/approval-delegates")
    public ApiResponse<?> createApprovalDelegate(@RequestBody Map<String, Object> payload) {
        return service.createApprovalDelegate(payload);
    }

    @GetMapping("/api/approval-monitors")
    public ApiResponse<Map<String, Object>> approvalMonitors(PageQuery pageQuery, @RequestParam(required = false) String status, @RequestParam(required = false) String businessType) {
        return service.approvalMonitors(pageQuery, status, businessType);
    }

    @GetMapping("/api/admin/tenants")
    public ApiResponse<Map<String, Object>> adminTenants(PageQuery pageQuery, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status, @RequestParam(required = false) String type) {
        return service.adminTenants(pageQuery, keyword, status, type);
    }

    @PostMapping("/api/admin/tenants")
    public ApiResponse<Map<String, Object>> createTenant(@RequestBody Map<String, Object> payload) {
        return service.createTenant(payload);
    }

    @PutMapping("/api/admin/tenants/{id}")
    public ApiResponse<Map<String, Object>> updateTenant(@PathVariable UUID id, @RequestBody Map<String, Object> payload) {
        return service.updateTenant(id, payload);
    }

    @DeleteMapping("/api/admin/tenants/{id}")
    public ApiResponse<Void> deleteTenant(@PathVariable UUID id) {
        return service.deleteTenant(id);
    }

    @GetMapping("/api/admin/users")
    public ApiResponse<Map<String, Object>> adminUsers(PageQuery pageQuery) {
        return service.adminUsers(pageQuery);
    }

    @GetMapping("/api/admin/dict-types")
    public ApiResponse<Map<String, Object>> adminDictTypes(PageQuery pageQuery) {
        return service.adminDictTypes(pageQuery);
    }

    @PostMapping("/api/admin/dict-types")
    public ApiResponse<Map<String, Object>> createDictType(@RequestBody Map<String, Object> payload) {
        return service.createDictType(payload);
    }

    @PutMapping("/api/admin/dict-types/{id}")
    public ApiResponse<Void> updateDictType(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return service.updateDictType(id, payload);
    }

    @DeleteMapping("/api/admin/dict-types/{id}")
    public ApiResponse<Void> deleteDictType(@PathVariable Long id) {
        return service.deleteDictType(id);
    }

    @GetMapping("/api/admin/dict-items")
    public ApiResponse<Map<String, Object>> adminDictItems(PageQuery pageQuery, @RequestParam(required = false) String type, @RequestParam(required = false) String typeCode) {
        return service.adminDictItems(pageQuery, type, typeCode);
    }

    @GetMapping("/api/admin/audit-logs")
    public ApiResponse<Map<String, Object>> adminAuditLogs(PageQuery pageQuery) {
        return service.adminAuditLogs(pageQuery);
    }

    @GetMapping("/api/admin/login-logs")
    public ApiResponse<Map<String, Object>> adminLoginLogs(PageQuery pageQuery) {
        return service.adminLoginLogs(pageQuery);
    }

    @GetMapping("/api/admin/tenant-dealer-bindings")
    public ApiResponse<Map<String, Object>> tenantBindings(PageQuery pageQuery) {
        return service.tenantBindings(pageQuery);
    }

    @GetMapping("/api/login-logs")
    public ApiResponse<Map<String, Object>> loginLogs(PageQuery pageQuery) {
        return service.loginLogs(pageQuery);
    }

    @GetMapping("/api/dict-items")
    public ApiResponse<Map<String, Object>> dictItems(@RequestParam(required = false) String type, @RequestParam(required = false) String typeCode) {
        return service.dictItems(type, typeCode);
    }

}
