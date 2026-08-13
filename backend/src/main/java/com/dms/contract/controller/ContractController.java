package com.dms.contract.controller;

import com.dms.annotation.OperationLog;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.contract.dto.ContractRequest;
import com.dms.contract.entity.Contract;
import com.dms.contract.entity.ContractAttachment;
import com.dms.contract.service.ContractService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Validated
public class ContractController {

    private final ContractService service;
    private final ApprovalService approvalService;

    @GetMapping("/actions/export")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String category) throws java.io.IOException {
        byte[] bytes = service.export(status, keyword, dealerId, category);
        String filename = "contracts_" + java.time.LocalDate.now() + ".xlsx";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8));
        return new org.springframework.http.ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
    }
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String category) {
        if (page < 1) throw new com.dms.common.BusinessException(com.dms.common.ErrorCode.PARAM_INVALID, "page: 页码从 1 起");
        return ApiResponse.ok(service.list(page, size, status, keyword, dealerId, category));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        return ApiResponse.ok(service.getDetail(id));
    }

    @PostMapping
    public ApiResponse<Contract> create(@RequestBody ContractRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<Contract> update(@PathVariable Long id, @RequestBody ContractRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Contract> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submit(id));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<Contract> withdraw(@PathVariable Long id) {
        return ApiResponse.ok(service.withdraw(id));
    }

    @PostMapping("/{id}/approve")
    @OperationLog(businessType = "contract", action = OperationAction.APPROVE, remark = "合同-审批通过")
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        String comment = body == null ? null : String.valueOf(body.getOrDefault("comment", ""));
        ApprovalInstance instance = approvalService.approveBusiness("CONTRACT", id, comment);
        if ("APPROVED".equals(instance.getStatus().name()) || "AUTO_APPROVED".equals(instance.getStatus().name())) {
            service.markApproved(id);
        }
        return ApiResponse.ok(Map.of(
                "id", id,
                "approvalStatus", instance.getStatus().name(),
                "approvalInstanceId", instance.getId()
        ));
    }

    @PostMapping("/{id}/reject")
    @OperationLog(businessType = "contract", action = OperationAction.REJECT, remark = "合同-驳回")
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        String comment = body == null ? null : String.valueOf(body.getOrDefault("comment", ""));
        ApprovalInstance instance = approvalService.rejectBusiness("CONTRACT", id, comment);
        service.markRejected(id, comment);
        return ApiResponse.ok(Map.of(
                "id", id,
                "approvalStatus", instance.getStatus().name(),
                "approvalInstanceId", instance.getId()
        ));
    }

    @PostMapping("/{id}/attachments")
    public ApiResponse<ContractAttachment> addAttachment(
            @PathVariable Long id,
            @RequestParam @NotNull Long fileId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) Long sizeBytes,
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(service.addAttachment(id, fileId, fileName, sizeBytes, category));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ApiResponse<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        service.deleteAttachment(id, attachmentId);
        return ApiResponse.ok();
    }
}
