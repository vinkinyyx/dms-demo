package com.dms.masterdata.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.masterdata.entity.DealerContact;
import com.dms.masterdata.service.DealerContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dealer-contacts")
@RequiredArgsConstructor
@Validated
public class DealerContactController {

    private final DealerContactService service;

    @GetMapping
    @PreAuthorize("@perm.hasAny('dealer_contact:view','dealer:view','dealer:search')")
    public ApiResponse<PageResult<DealerContact>> list(@Valid PageQuery pageQuery,
                                                       @RequestParam(required = false) Long dealerId,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(pageQuery, dealerId, status));
    }

    @GetMapping("/all")
    @PreAuthorize("@perm.hasAny('dealer_contact:view','dealer:view','dealer:search')")
    public ApiResponse<List<DealerContact>> listByDealer(@RequestParam Long dealerId) {
        return ApiResponse.ok(service.listByDealer(dealerId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.hasAny('dealer_contact:view','dealer:view')")
    public ApiResponse<DealerContact> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("@perm.hasAny('dealer_contact:create','dealer:edit')")
    @OperationLog(businessType = "dealer_contact", action = OperationAction.CREATE, remark = "新建经销商联系人")
    public ApiResponse<DealerContact> create(@RequestBody DealerContact request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.hasAny('dealer_contact:edit','dealer:edit')")
    @OperationLog(businessType = "dealer_contact", action = OperationAction.UPDATE, remark = "编辑经销商联系人")
    public ApiResponse<DealerContact> update(@PathVariable Long id, @RequestBody DealerContact request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/set-default")
    @PreAuthorize("@perm.hasAny('dealer_contact:edit','dealer:edit')")
    @OperationLog(businessType = "dealer_contact", action = OperationAction.UPDATE, remark = "设置默认联系人")
    public ApiResponse<DealerContact> setDefault(@PathVariable Long id) {
        return ApiResponse.ok(service.setDefault(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.hasAny('dealer_contact:delete','dealer:edit')")
    @OperationLog(businessType = "dealer_contact", action = OperationAction.DELETE, remark = "删除经销商联系人")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    @PreAuthorize("@perm.hasAny('dealer_contact:view','dealer:view')")
    public org.springframework.http.ResponseEntity<byte[]> export(@RequestParam(required = false) Long dealerId)
            throws java.io.IOException {
        List<DealerContact> list = service.listByDealer(dealerId);
        String[] headers = {"ID", "联系人", "电话", "邮箱", "职务", "默认", "状态", "备注"};
        String[] fieldNames = {"id", "contactName", "phone", "email", "position", "isDefault", "status", "remark"};
        byte[] bytes = com.dms.common.util.ExcelExportUtils.exportToExcel(list, headers, fieldNames);
        org.springframework.http.HttpHeaders headers1 = new org.springframework.http.HttpHeaders();
        headers1.setContentType(org.springframework.http.MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers1.setContentDispositionFormData("attachment",
                java.net.URLEncoder.encode("dealer-contacts.xlsx", java.nio.charset.StandardCharsets.UTF_8));
        return new org.springframework.http.ResponseEntity<>(bytes, headers1, org.springframework.http.HttpStatus.OK);
    }
}
