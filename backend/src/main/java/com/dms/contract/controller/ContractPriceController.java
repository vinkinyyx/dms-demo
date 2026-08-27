package com.dms.contract.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.contract.service.ContractPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts/{contractId}/prices")
@RequiredArgsConstructor
@Validated
public class ContractPriceController {

    private final ContractPriceService service;

    @GetMapping
    @PreAuthorize("@perm.hasAny('contract:view','contract_price:view')")
    public ApiResponse<Map<String, Object>> list(@PathVariable Long contractId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String productCode) {
        return ApiResponse.ok(service.list(contractId, page, size, status, productCode));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.hasAny('contract:view','contract_price:view')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long contractId, @PathVariable Long id) {
        return ApiResponse.ok(service.detail(contractId, id));
    }

    @PostMapping
    @PreAuthorize("@perm.hasAny('contract_price:create','contract:edit')")
    @OperationLog(businessType = "contract_price", action = OperationAction.CREATE, remark = "新增合同价")
    public ApiResponse<Map<String, Object>> create(@PathVariable Long contractId,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(contractId, body));
    }

    @PostMapping("/batch")
    @PreAuthorize("@perm.hasAny('contract_price:create','contract_price:edit','contract:edit')")
    @OperationLog(businessType = "contract_price", action = OperationAction.UPDATE, remark = "批量保存合同价清单")
    public ApiResponse<Map<String, Object>> batchSave(@PathVariable Long contractId,
                                                      @RequestBody List<Map<String, Object>> rows) {
        return ApiResponse.ok(service.batchSave(contractId, rows));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.hasAny('contract_price:edit','contract:edit')")
    @OperationLog(businessType = "contract_price", action = OperationAction.UPDATE, remark = "编辑合同价")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long contractId,
                                                   @PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(contractId, id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.hasAny('contract_price:delete','contract:edit')")
    @OperationLog(businessType = "contract_price", action = OperationAction.DELETE, remark = "删除合同价")
    public ApiResponse<Void> delete(@PathVariable Long contractId, @PathVariable Long id) {
        service.delete(contractId, id);
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    @PreAuthorize("@perm.hasAny('contract:view','contract_price:view')")
    public ResponseEntity<byte[]> export(@PathVariable Long contractId) throws Exception {
        return service.export(contractId);
    }

    @GetMapping("/actions/export-template")
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        return service.exportTemplate();
    }

    @PostMapping("/actions/import")
    @PreAuthorize("@perm.hasAny('contract_price:create','contract_price:edit','contract:edit')")
    @OperationLog(businessType = "contract_price", action = OperationAction.CREATE, remark = "导入合同价清单")
    public ApiResponse<Map<String, Object>> importExcel(@PathVariable Long contractId,
                                                        @RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(service.importExcel(contractId, file));
    }
}
