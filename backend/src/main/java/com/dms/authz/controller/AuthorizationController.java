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

    @PostMapping("/api/authorizations/{id}/renew")
    @OperationLog(businessType = "authorization", action = OperationAction.CREATE, remark = "授权-续约")
    public ApiResponse<Authorization> renew(@PathVariable Long id, @RequestBody(required = false) Authorization request) {
        return ApiResponse.ok(service.renew(id, request));
    }

    @PostMapping("/api/authorizations/{id}/terminate")
    @OperationLog(businessType = "authorization", action = OperationAction.APPROVE, remark = "授权-发起终止")
    public ApiResponse<Authorization> terminate(@PathVariable Long id,
                                                @RequestBody(required = false) java.util.Map<String, Object> body) {
        String reason = body == null ? null : String.valueOf(body.getOrDefault("reason", ""));
        return ApiResponse.ok(service.terminate(id, reason));
    }

    /**
     * 终端医院选择：按区域（省/市）子树 + 关键字查询医院，供授权页批量选择。
     * regionId 为空时返回关键字命中的医院；返回 id/name/region 信息。
     */
    @GetMapping("/api/authorizations/terminals")
    public ApiResponse<List<java.util.Map<String, Object>>> terminals(
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.listTerminals(regionId, keyword));
    }

    /** 产品线选项（全部启用的产品线，供授权多选） */
    @GetMapping("/api/authorizations/product-lines")
    public ApiResponse<List<java.util.Map<String, Object>>> productLines() {
        return ApiResponse.ok(service.listProductLines());
    }

    /** 授权-下单挂钩开关：查询当前租户是否强制 */
    @GetMapping("/api/authorizations/order-enforce")
    public ApiResponse<java.util.Map<String, Object>> orderEnforce() {
        boolean enforced = service.isOrderAuthzEnforced();
        return ApiResponse.ok(java.util.Map.of(
                "enforced", enforced,
                "label", enforced ? "授权与下单已挂钩：无有效授权不能下单/出库" : "授权与下单解耦：可直接下单"));
    }

    /** 授权-下单挂钩开关：更新（业务前台租户配置） */
    @PostMapping("/api/authorizations/order-enforce")
    @OperationLog(businessType = "authorization", action = OperationAction.UPDATE, remark = "授权-下单开关设置")
    public ApiResponse<java.util.Map<String, Object>> setOrderEnforce(@RequestBody java.util.Map<String, Object> body) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", false)));
        service.setOrderAuthzEnforced(enabled);
        return ApiResponse.ok(java.util.Map.of("enforced", enabled));
    }
}
