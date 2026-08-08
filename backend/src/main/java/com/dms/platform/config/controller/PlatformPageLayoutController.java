/*
 * 骞冲彴椤甸潰甯冨眬鑱氬悎涓嬪彂鎺ュ彛銆? * 涓€娆¤皟鐢ㄨ繑鍥?filter + page + button 涓夊閰嶇疆锛堝凡鍚堝苟骞冲彴榛樿 + 绉熸埛瑕嗙洊锛夈€? * 涓氬姟鍓嶇鐢?ListPageLayout.vue 鍔犺浇缁勪欢鏃惰皟涓€娆°€? */
package com.dms.platform.config.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.platform.config.dto.ButtonConfigDTO;
import com.dms.platform.config.dto.FilterConfigDTO;
import com.dms.platform.config.dto.PageConfigDTO;
import com.dms.platform.config.service.PlatformButtonConfigService;
import com.dms.platform.config.service.UiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui/layout")
@RequiredArgsConstructor
public class PlatformPageLayoutController {

    private final UiConfigService uiConfigService;
    private final PlatformButtonConfigService buttonConfigService;

    @GetMapping("/{pageKey}")
    public ApiResponse<LayoutPayload> layout(@PathVariable String pageKey) {
        String tenantType = TenantContext.getTenantType();
        if (tenantType == null || tenantType.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "鏃犳硶璇嗗埆绉熸埛绫诲瀷");
        }
        List<FilterConfigDTO> filters = uiConfigService.filtersForTenant(pageKey, tenantType, TenantContext.getTenantId());
        List<PageConfigDTO> columns = uiConfigService.pageForTenant(pageKey, tenantType);
        List<ButtonConfigDTO> toolbar = buttonConfigService.mergedForTenant(TenantContext.getTenantId(), pageKey, "toolbar");
        List<ButtonConfigDTO> rowButtons = enforceRequiredRowButtons(
                pageKey,
                buttonConfigService.mergedForTenant(TenantContext.getTenantId(), pageKey, "row"));
        return ApiResponse.ok(new LayoutPayload(pageKey, tenantType, filters, columns, toolbar, rowButtons));
    }

    private List<ButtonConfigDTO> enforceRequiredRowButtons(String pageKey, List<ButtonConfigDTO> rowButtons) {
        if (!"dealer-profile".equals(pageKey)) return rowButtons;
        List<ButtonConfigDTO> result = new ArrayList<>(rowButtons);
        ButtonConfigDTO profileEntry = result.stream()
                .filter(b -> "view".equals(b.getButtonKey()))
                .findFirst()
                .orElseGet(() -> {
                    ButtonConfigDTO b = new ButtonConfigDTO();
                    b.setButtonKey("view");
                    result.add(0, b);
                    return b;
                });
        profileEntry.setLabel("\u67e5\u770b\u753b\u50cf");
        profileEntry.setButtonType("primary");
        profileEntry.setPermissionCode("dealer:view");
        profileEntry.setVisible(true);
        profileEntry.setStatus("active");
        profileEntry.setSortOrder(10);
        profileEntry.setRowButtonPosition("common");
        profileEntry.setConfirmRequired(false);
        result.sort((a, b) -> Integer.compare(a.getSortOrder() == null ? 100 : a.getSortOrder(),
                b.getSortOrder() == null ? 100 : b.getSortOrder()));
        return result;
    }

    public record LayoutPayload(
            String pageKey,
            String tenantType,
            List<FilterConfigDTO> filters,
            List<PageConfigDTO> columns,
            List<ButtonConfigDTO> toolbar,
            List<ButtonConfigDTO> rowButtons
    ) {}
}
