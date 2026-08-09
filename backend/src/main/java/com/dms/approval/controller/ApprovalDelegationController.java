package com.dms.approval.controller;

import com.dms.approval.dto.DelegationRequest;
import com.dms.approval.entity.ApprovalDelegation;
import com.dms.approval.service.ApprovalDelegationService;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approval/delegations")
@RequiredArgsConstructor
public class ApprovalDelegationController {
    private final ApprovalDelegationService delegationService;

    @GetMapping
    public ApiResponse<PageResult<ApprovalDelegation>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(delegationService.list(pageQuery));
    }

    @PostMapping
    public ApiResponse<ApprovalDelegation> create(@RequestBody DelegationRequest request) {
        return ApiResponse.ok(delegationService.create(request));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<ApprovalDelegation> disable(@PathVariable Long id) {
        return ApiResponse.ok(delegationService.disable(id));
    }
}
