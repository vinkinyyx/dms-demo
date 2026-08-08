/*
 * 平台后台菜单管理接口。
 */
package com.dms.platform.config.controller;

import com.dms.common.ApiResponse;
import com.dms.platform.config.dto.PlatformMenuDTO;
import com.dms.platform.config.dto.PlatformMenuSaveRequest;
import com.dms.platform.config.service.PlatformMenuService;
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

import java.util.List;

@RestController
@RequestMapping("/api/admin/menus")
@RequiredArgsConstructor
public class AdminMenuController {

    private final PlatformMenuService menuService;

    @GetMapping
    public ApiResponse<List<PlatformMenuDTO>> list(@RequestParam(required = false) String tenantType) {
        return ApiResponse.ok(menuService.list(tenantType, false));
    }

    @PostMapping
    public ApiResponse<PlatformMenuDTO> create(@Valid @RequestBody PlatformMenuSaveRequest request) {
        return ApiResponse.ok(menuService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlatformMenuDTO> update(@PathVariable Long id,
                                               @Valid @RequestBody PlatformMenuSaveRequest request) {
        return ApiResponse.ok(menuService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        menuService.setStatus(id, true);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        menuService.setStatus(id, false);
        return ApiResponse.ok();
    }

    @PostMapping("/refresh-cache")
    public ApiResponse<Void> refreshCache() {
        menuService.refreshCache();
        return ApiResponse.ok();
    }
}