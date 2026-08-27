package com.dms.masterdata.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.masterdata.entity.DealerAddress;
import com.dms.masterdata.service.DealerAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dealer-addresses")
@RequiredArgsConstructor
@Validated
public class DealerAddressController {

    private final DealerAddressService service;

    @GetMapping
    @PreAuthorize("@perm.hasAny('dealer_address:view','dealer:view','dealer:search')")
    public ApiResponse<PageResult<DealerAddress>> list(@Valid PageQuery pageQuery,
                                                       @RequestParam(required = false) Long dealerId,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(pageQuery, dealerId, status));
    }

    @GetMapping("/all")
    @PreAuthorize("@perm.hasAny('dealer_address:view','dealer:view','dealer:search')")
    public ApiResponse<List<DealerAddress>> listByDealer(@RequestParam Long dealerId) {
        return ApiResponse.ok(service.listByDealer(dealerId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.hasAny('dealer_address:view','dealer:view')")
    public ApiResponse<DealerAddress> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("@perm.hasAny('dealer_address:create','dealer:edit')")
    @OperationLog(businessType = "dealer_address", action = OperationAction.CREATE, remark = "新建经销商收货地址")
    public ApiResponse<DealerAddress> create(@RequestBody DealerAddress request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.hasAny('dealer_address:edit','dealer:edit')")
    @OperationLog(businessType = "dealer_address", action = OperationAction.UPDATE, remark = "编辑经销商收货地址")
    public ApiResponse<DealerAddress> update(@PathVariable Long id, @RequestBody DealerAddress request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/set-default")
    @PreAuthorize("@perm.hasAny('dealer_address:edit','dealer:edit')")
    @OperationLog(businessType = "dealer_address", action = OperationAction.UPDATE, remark = "设置默认收货地址")
    public ApiResponse<DealerAddress> setDefault(@PathVariable Long id) {
        return ApiResponse.ok(service.setDefault(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.hasAny('dealer_address:delete','dealer:edit')")
    @OperationLog(businessType = "dealer_address", action = OperationAction.DELETE, remark = "删除经销商收货地址")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }
}
