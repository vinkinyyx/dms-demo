from pathlib import Path
p=Path('backend/src/main/java/com/dms/platform/config/controller/TenantUiConfigController.java')
p.write_text('''package com.dms.platform.config.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.platform.config.dto.ButtonConfigDTO;
import com.dms.platform.config.dto.ButtonConfigUpsertRequest;
import com.dms.platform.config.dto.FilterConfigDTO;
import com.dms.platform.config.service.PlatformButtonConfigService;
import com.dms.platform.config.service.UiConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenant-ui")
@RequiredArgsConstructor
public class TenantUiConfigController {

    private final UiConfigService uiConfigService;
    private final PlatformButtonConfigService buttonConfigService;

    @GetMapping("/pages/{pageKey}/filters")
    public ApiResponse<List<FilterConfigDTO>> filters(@PathVariable String pageKey) {
        return ApiResponse.ok(uiConfigService.filtersForTenant(pageKey, currentTenantType(), currentTenantId()));
    }

    @PostMapping("/pages/{pageKey}/filters")
    public ApiResponse<List<FilterConfigDTO>> saveFilters(@PathVariable String pageKey,
                                                          @RequestBody List<FilterConfigDTO> filters) {
        return ApiResponse.ok(uiConfigService.upsertTenantFilters(pageKey, currentTenantType(), currentTenantId(), filters));
    }

    @GetMapping("/pages/{pageKey}/buttons")
    public ApiResponse<List<ButtonConfigDTO>> buttons(@PathVariable String pageKey) {
        return ApiResponse.ok(buttonConfigService.mergedForTenant(currentTenantId(), pageKey, "toolbar").stream()
                .peek(b -> b.setFromTenant(Boolean.TRUE.equals(b.getFromTenant())))
                .toList());
    }

    @PostMapping("/pages/{pageKey}/buttons")
    public ApiResponse<List<ButtonConfigDTO>> saveButtons(@PathVariable String pageKey,
                                                          @Valid @RequestBody ButtonConfigUpsertRequest request) {
        request.setPageKey(pageKey);
        request.setTenantType(currentTenantType());
        request.setScopeLevel("TENANT_OVERRIDE");
        return ApiResponse.ok(buttonConfigService.upsert(pageKey, currentTenantType(), "TENANT_OVERRIDE", currentTenantId(), request.getButtons()));
    }

    private UUID currentTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无法识别租户");
        }
        return tenantId;
    }

    private String currentTenantType() {
        String tenantType = TenantContext.getTenantType();
        if (tenantType == null || tenantType.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无法识别租户类型");
        }
        return tenantType;
    }
}
''', encoding='utf-8', newline='\n')
