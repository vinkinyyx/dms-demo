package com.dms.masterdata.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import com.dms.masterdata.service.DealerGlobalDiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dealer-global-discounts")
@RequiredArgsConstructor
@Validated
public class DealerGlobalDiscountController {

    private final DealerGlobalDiscountService service;

    @GetMapping
    @PreAuthorize("@perm.hasAny('dealer_global_discount:view','dealer_global_discount:search')")
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) Long dealerId,
                                                 @RequestParam(required = false) String dealerName,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String validFrom,
                                                 @RequestParam(required = false) String validTo) {
        return ApiResponse.ok(service.list(page, size, dealerId, dealerName, status, validFrom, validTo));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.hasAny('dealer_global_discount:view')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("@perm.hasAny('dealer_global_discount:create')")
    @OperationLog(businessType = "dealer_global_discount", action = OperationAction.CREATE, remark = "新建客户全局折扣")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.hasAny('dealer_global_discount:edit')")
    @OperationLog(businessType = "dealer_global_discount", action = OperationAction.UPDATE, remark = "编辑客户全局折扣")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("@perm.hasAny('dealer_global_discount:edit')")
    public ApiResponse<Map<String, Object>> activate(@PathVariable Long id) {
        return ApiResponse.ok(service.setStatus(id, "active"));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("@perm.hasAny('dealer_global_discount:edit')")
    public ApiResponse<Map<String, Object>> deactivate(@PathVariable Long id) {
        return ApiResponse.ok(service.setStatus(id, "inactive"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.hasAny('dealer_global_discount:delete')")
    @OperationLog(businessType = "dealer_global_discount", action = OperationAction.DELETE, remark = "删除客户全局折扣")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    @PreAuthorize("@perm.hasAny('dealer_global_discount:view')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long dealerId,
                                         @RequestParam(required = false) String status) throws Exception {
        return service.export(dealerId, status);
    }
}
