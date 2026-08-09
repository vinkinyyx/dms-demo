package com.dms.approval.controller;

import com.dms.approval.dto.TemplateDetailDTO;
import com.dms.approval.dto.TemplateSaveRequest;
import com.dms.approval.dto.TemplateSummaryDTO;
import com.dms.approval.service.ApprovalTemplateService;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approval/templates")
@RequiredArgsConstructor
public class ApprovalTemplateController {
    private final ApprovalTemplateService templateService;

    @GetMapping
    public ApiResponse<PageResult<TemplateSummaryDTO>> list(@Valid PageQuery pageQuery,
                                                            @RequestParam(required = false) String businessType,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(templateService.list(pageQuery, businessType, status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<TemplateDetailDTO> get(@PathVariable Long id) {
        return ApiResponse.ok(templateService.get(id));
    }

    @PostMapping
    public ApiResponse<TemplateDetailDTO> create(@RequestBody TemplateSaveRequest request) {
        return ApiResponse.ok(templateService.createDraft(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TemplateDetailDTO> update(@PathVariable Long id, @RequestBody TemplateSaveRequest request) {
        return ApiResponse.ok(templateService.updateDraft(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<TemplateDetailDTO> publish(@PathVariable Long id) {
        return ApiResponse.ok(templateService.publish(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<TemplateDetailDTO> disable(@PathVariable Long id) {
        return ApiResponse.ok(templateService.disable(id));
    }

    @PostMapping("/{id}/new-version")
    public ApiResponse<TemplateDetailDTO> newVersion(@PathVariable Long id) {
        return ApiResponse.ok(templateService.newVersion(id));
    }
}
