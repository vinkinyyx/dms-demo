/*
 * 平台后台默认角色模板管理接口。
 */
package com.dms.platform.rbac.controller;

import com.dms.common.ApiResponse;
import com.dms.platform.config.entity.PlatformMenu;
import com.dms.platform.config.repository.PlatformMenuRepository;
import com.dms.platform.rbac.dto.RoleTemplateDTO;
import com.dms.platform.rbac.dto.RoleTemplatePermissionsRequest;
import com.dms.platform.rbac.dto.RoleTemplateSaveRequest;
import com.dms.platform.rbac.service.RoleTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/role-templates")
@RequiredArgsConstructor
public class RoleTemplateController {

    private final RoleTemplateService roleTemplateService;
    private final PlatformMenuRepository menuRepository;

    @GetMapping
    public ApiResponse<List<RoleTemplateDTO>> list(@RequestParam(required = false) String tenantType) {
        return ApiResponse.ok(roleTemplateService.list(tenantType));
    }

    @PostMapping
    public ApiResponse<RoleTemplateDTO> create(@Valid @RequestBody RoleTemplateSaveRequest request) {
        return ApiResponse.ok(roleTemplateService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleTemplateDTO> update(@PathVariable Long id,
                                               @Valid @RequestBody RoleTemplateSaveRequest request) {
        return ApiResponse.ok(roleTemplateService.update(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleTemplateDTO> get(@PathVariable Long id) {
        return ApiResponse.ok(roleTemplateService.get(id));
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<List<String>> permissions(@PathVariable Long id) {
        return ApiResponse.ok(roleTemplateService.getPermissions(id));
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<Void> setPermissions(@PathVariable Long id,
                                            @RequestBody RoleTemplatePermissionsRequest request) {
        roleTemplateService.setPermissions(id, request);
        return ApiResponse.ok();
    }

    @GetMapping("/resources")
    public ApiResponse<List<Map<String, Object>>> resources(@RequestParam String tenantType) {
        List<PlatformMenu> menus = menuRepository
                .findByTenantTypeInAndStatusOrderBySortOrderAsc(List.of("ALL", tenantType), "active");
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlatformMenu m : menus) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", m.getPermissionCode() != null ? m.getPermissionCode() : m.getMenuKey() + ":menu");
            item.put("name", m.getLabel());
            item.put("menuKey", m.getMenuKey());
            result.add(item);
        }
        return ApiResponse.ok(result);
    }
}