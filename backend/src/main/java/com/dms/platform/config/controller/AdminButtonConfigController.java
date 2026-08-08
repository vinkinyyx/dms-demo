/*
 * 按钮配置管理接口（admin-vue 调用）。
 */
package com.dms.platform.config.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import com.dms.platform.config.dto.ButtonConfigDTO;
import com.dms.platform.config.dto.ButtonConfigUpsertRequest;
import com.dms.platform.config.service.PlatformButtonConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminButtonConfigController {

    private final PlatformButtonConfigService service;

    /**
     * 管理后台：列出某页面下所有按钮（平台默认 + 所有租户覆盖）
     */
    @GetMapping("/buttons")
    public ApiResponse<List<ButtonConfigDTO>> list(@RequestParam String pageKey,
                                                   @RequestParam String tenantType) {
        return ApiResponse.ok(service.adminList(pageKey, tenantType));
    }

    /**
     * 批量保存按钮配置
     *   - scopeLevel=PLATFORM_DEFAULT：admin 调整平台默认（所有租户生效，除非租户覆盖）
     *   - scopeLevel=TENANT_OVERRIDE：tenant_admin 调整本租户覆盖
     */
    @PostMapping("/buttons/batch")
    public ApiResponse<List<ButtonConfigDTO>> batch(@Valid @RequestBody ButtonConfigUpsertRequest request) {
        UUID overrideTenantId = "TENANT_OVERRIDE".equalsIgnoreCase(request.getScopeLevel())
                ? TenantContext.getTenantId()
                : null;
        return ApiResponse.ok(service.upsert(
                request.getPageKey(),
                request.getTenantType(),
                request.getScopeLevel(),
                overrideTenantId,
                request.getButtons()));
    }

    @PostMapping("/buttons/refresh-cache")
    public ApiResponse<Void> refreshCache() {
        service.refreshAll();
        return ApiResponse.ok();
    }
}