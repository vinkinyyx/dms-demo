/*
 * 骞冲彴鍚庡彴绉熸埛绠＄悊鍛樼鐞嗭細鍒涘缓銆佸仠鐢ㄣ€侀噸缃瘑鐮併€傛瘡绉熸埛浠呬竴涓惎鐢ㄤ腑鐨勭鎴风鐞嗗憳銆? */
package com.dms.tenant.controller.admin;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.tenant.dto.admin.TenantAdminCreateRequest;
import com.dms.tenant.dto.admin.TenantAdminDTO;
import com.dms.tenant.dto.admin.TenantAdminResetPasswordRequest;
import com.dms.tenant.service.TenantProvisioningService;
import com.dms.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tenant-admins")
@RequiredArgsConstructor
public class AdminTenantAdminController {

    private final TenantProvisioningService provisioningService;

    @GetMapping
    public ApiResponse<PageResult<TenantAdminDTO>> list(@Valid PageQuery pageQuery,
                                                        @RequestParam(required = false) java.util.UUID tenantId,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(provisioningService.listTenantAdmins(pageQuery, tenantId, keyword));
    }

    @PostMapping
    public ApiResponse<TenantAdminDTO> create(@Valid @RequestBody TenantAdminCreateRequest request) {
        User user = provisioningService.createTenantAdmin(
                request.getTenantId(), request.getUsername(), request.getPassword(), request.getName(), null);
        return ApiResponse.ok(toDTO(user));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        provisioningService.disableTenantAdmin(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody TenantAdminResetPasswordRequest request) {
        provisioningService.resetTenantAdminPassword(id, request.getNewPassword());
        return ApiResponse.ok();
    }

    private TenantAdminDTO toDTO(User u) {
        return TenantAdminDTO.builder()
                .id(u.getId())
                .tenantId(u.getTenantId())
                .username(u.getUsername())
                .name(u.getName())
                .status(u.getStatus())
                .mustChangePassword(Boolean.TRUE.equals(u.getMustChangePassword()))
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
