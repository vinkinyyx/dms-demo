/*
 * 平台后台租户管理：厂家/经销商租户开通、启停、绑定查询。
 */
package com.dms.tenant.controller.admin;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.tenant.dto.admin.AdminTenantDTO;
import com.dms.tenant.dto.admin.DealerTenantCreateRequest;
import com.dms.tenant.dto.admin.ManufacturerTenantCreateRequest;
import com.dms.tenant.dto.admin.TenantDisableRequest;
import com.dms.tenant.service.TenantProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class AdminTenantController {

    private final TenantProvisioningService provisioningService;

    @GetMapping("/manufacturers")
    public ApiResponse<PageResult<AdminTenantDTO>> listManufacturers(@Valid PageQuery pageQuery,
                                                                     @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(provisioningService.listManufacturers(pageQuery, keyword));
    }

    @PostMapping("/manufacturers")
    public ApiResponse<AdminTenantDTO> createManufacturer(
            @Valid @RequestBody ManufacturerTenantCreateRequest request) {
        return ApiResponse.ok(provisioningService.createManufacturer(request));
    }

    @GetMapping("/dealers")
    public ApiResponse<PageResult<AdminTenantDTO>> listDealers(@Valid PageQuery pageQuery,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) UUID manufacturerTenantId) {
        return ApiResponse.ok(provisioningService.listDealers(pageQuery, keyword, manufacturerTenantId));
    }

    @PostMapping("/dealers")
    public ApiResponse<AdminTenantDTO> createDealer(@Valid @RequestBody DealerTenantCreateRequest request) {
        return ApiResponse.ok(provisioningService.createDealer(request));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(provisioningService.tenantStats());
    }
    @GetMapping("/{id}")
    public ApiResponse<AdminTenantDTO> get(@PathVariable UUID id) {
        return ApiResponse.ok(provisioningService.get(id));
    }

    @GetMapping("/manufacturers/{id}")
    public ApiResponse<AdminTenantDTO> getManufacturer(@PathVariable UUID id) {
        return ApiResponse.ok(provisioningService.get(id));
    }

    @GetMapping("/dealers/{id}")
    public ApiResponse<AdminTenantDTO> getDealer(@PathVariable UUID id) {
        return ApiResponse.ok(provisioningService.get(id));
    }

    @GetMapping("/{id}/bindings")
    public ApiResponse<Map<String, Object>> getBindings(@PathVariable UUID id) {
        return ApiResponse.ok(provisioningService.getBinding(id));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<AdminTenantDTO> enable(@PathVariable UUID id) {
        return ApiResponse.ok(provisioningService.enable(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<AdminTenantDTO> disable(@PathVariable UUID id,
                                               @RequestBody(required = false) TenantDisableRequest request) {
        String reason = request == null ? null : request.getReason();
        return ApiResponse.ok(provisioningService.disable(id, reason));
    }
}
