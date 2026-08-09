package com.dms.approval.controller;

import com.dms.approval.dto.*;
import com.dms.approval.entity.*;
import com.dms.approval.service.ApprovalService;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApprovalController {
    private final ApprovalService approvalService;

    @PostMapping("/instances/start")
    public ApiResponse<ApprovalInstance> start(@RequestBody StartApprovalRequest request) {
        return ApiResponse.ok(approvalService.start(request));
    }

    @GetMapping("/tasks/my-todo")
    public ApiResponse<PageResult<ApprovalTask>> myTodo(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(approvalService.myTodo(pageQuery));
    }

    @GetMapping("/tasks/my-done")
    public ApiResponse<PageResult<ApprovalTask>> myDone(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(approvalService.myDone(pageQuery));
    }

    @GetMapping("/instances/my-submitted")
    public ApiResponse<PageResult<ApprovalInstance>> mySubmitted(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(approvalService.mySubmitted(pageQuery));
    }

    @GetMapping("/cc/my")
    public ApiResponse<PageResult<ApprovalCcRecord>> myCc(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(approvalService.myCc(pageQuery));
    }

    @GetMapping("/admin/instances")
    public ApiResponse<PageResult<ApprovalInstance>> adminInstances(@Valid PageQuery pageQuery,
                                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(approvalService.adminInstances(pageQuery, status));
    }

    @GetMapping("/instances/{id}")
    public ApiResponse<Map<String, Object>> getInstance(@PathVariable Long id) {
        return ApiResponse.ok(Map.of(
                "instance", approvalService.getInstance(id),
                "tasks", approvalService.getInstanceTasks(id),
                "records", approvalService.getInstanceRecords(id)
        ));
    }

    @GetMapping("/instances/by-business")
    public ApiResponse<ApprovalInstance> latestInstance(@RequestParam String businessType, @RequestParam Long businessId) {
        return ApiResponse.ok(approvalService.latestInstance(businessType, businessId));
    }

    @PostMapping("/instances/{id}/withdraw")
    public ApiResponse<ApprovalInstance> withdraw(@PathVariable Long id, @RequestBody(required = false) ApprovalActionRequest request) {
        return ApiResponse.ok(approvalService.withdraw(id, request));
    }

    @PostMapping("/tasks/{id}/approve")
    public ApiResponse<ApprovalTask> approve(@PathVariable Long id, @RequestBody(required = false) ApprovalActionRequest request) {
        return ApiResponse.ok(approvalService.approve(id, request));
    }

    @PostMapping("/tasks/{id}/reject")
    public ApiResponse<ApprovalTask> reject(@PathVariable Long id, @RequestBody(required = false) ApprovalActionRequest request) {
        return ApiResponse.ok(approvalService.reject(id, request));
    }

    @PostMapping("/tasks/{id}/transfer")
    public ApiResponse<ApprovalTask> transfer(@PathVariable Long id, @RequestBody TransferTaskRequest request) {
        return ApiResponse.ok(approvalService.transfer(id, request));
    }

    @PostMapping("/tasks/{id}/add-sign")
    public ApiResponse<ApprovalTask> addSign(@PathVariable Long id, @RequestBody AddSignRequest request) {
        return ApiResponse.ok(approvalService.addSign(id, request));
    }

    @PostMapping("/admin/tasks/{id}/reassign")
    public ApiResponse<ApprovalTask> reassign(@PathVariable Long id, @RequestBody ReassignTaskRequest request) {
        return ApiResponse.ok(approvalService.reassign(id, request));
    }

    @PostMapping("/admin/instances/{id}/terminate")
    public ApiResponse<ApprovalInstance> terminate(@PathVariable Long id, @RequestBody TerminateInstanceRequest request) {
        return ApiResponse.ok(approvalService.terminate(id, request));
    }
}
