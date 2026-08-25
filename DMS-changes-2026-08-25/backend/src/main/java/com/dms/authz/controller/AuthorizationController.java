/*
 * 授权 REST 控制器：/api/authorizations、/api/temp-authorizations。
 */
package com.dms.authz.controller;

import com.dms.annotation.OperationLog;
import com.dms.authz.dto.AuthorizationCheckRequest;
import com.dms.authz.dto.AuthorizationCheckResult;
import com.dms.authz.entity.Authorization;
import com.dms.authz.entity.TempAuthorization;
import com.dms.authz.service.AuthorizationService;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.ContentDispositionUtils;
import com.dms.common.util.ExcelExportUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class AuthorizationController {

    private final AuthorizationService service;

    @GetMapping("/api/authorizations")
    public ApiResponse<PageResult<Authorization>> list(@Valid PageQuery pageQuery,
                                                       @RequestParam(required = false) Long id,
                                                       @RequestParam(required = false) String code,
                                                       @RequestParam(required = false) String dealerName,
                                                       @RequestParam(required = false) String validFrom,
                                                       @RequestParam(required = false) String validTo,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String createdAtFrom,
                                                       @RequestParam(required = false) String createdAtTo,
                                                       @RequestParam(required = false) String updatedAtFrom,
                                                       @RequestParam(required = false) String updatedAtTo) {
        return ApiResponse.ok(service.list(pageQuery, id, code, dealerName, validFrom, validTo, status,
                createdAtFrom, createdAtTo, updatedAtFrom, updatedAtTo));
    }

    @GetMapping({"/api/authorizations/{id}", "/api/authorizations/{id}/detail"})
    public ApiResponse<Authorization> get(@PathVariable Long id) {
        return ApiResponse.ok(service.getDetail(id));
    }

    @GetMapping("/api/authorizations/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        PageQuery pq = new PageQuery();
        pq.setPage(1);
        pq.setSize(10000);
        List<Authorization> list = service.list(pq, null, null, null, null, null, null, null, null, null, null).getList();

        String[] headers = {"ID", "\u7ecf\u9500\u5546ID", "\u7ecf\u9500\u5546", "\u6388\u6743\u4ea7\u54c1\u5206\u7c7b", "\u6388\u6743\u533b\u9662/\u7ec8\u7aef", "\u751f\u6548", "\u622a\u6b62", "\u72b6\u6001", "\u5907\u6ce8", "\u521b\u5efa\u65f6\u95f4", "\u66f4\u65b0\u65f6\u95f4"};
        String[] fieldNames = {"id", "dealerId", "dealerName", "categoryNames", "terminalNames", "validFrom", "validTo", "status", "remark", "createdAt", "updatedAt"};

        byte[] excelBytes = ExcelExportUtils.exportToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("\u6388\u6743\u5217\u8868.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @PostMapping("/api/authorizations")
    @OperationLog(businessType = "authorization", action = OperationAction.CREATE, remark = "授权-创建")
    public ApiResponse<Authorization> create(@RequestBody Authorization request) {
        return ApiResponse.ok(service.create(request));
    }

    @PostMapping("/api/authorizations/check")
    public ApiResponse<List<AuthorizationCheckResult>> check(@RequestBody AuthorizationCheckRequest request) {
        return ApiResponse.ok(service.check(request));
    }

    @PostMapping("/api/temp-authorizations")
    @OperationLog(businessType = "tempAuthorization", action = OperationAction.CREATE, remark = "临时授权-创建")
    public ApiResponse<TempAuthorization> createTemp(@RequestBody TempAuthorization request) {
        return ApiResponse.ok(service.createTemp(request));
    }

    @DeleteMapping("/api/authorizations/{id}")
    @OperationLog(businessType = "authorization", action = OperationAction.DELETE, remark = "授权-删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
