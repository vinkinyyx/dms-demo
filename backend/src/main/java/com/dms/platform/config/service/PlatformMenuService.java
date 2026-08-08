/*
 * 平台菜单配置服务：后台 CRUD + 前台按租户类型读取，接入 Redis 缓存。
 */
package com.dms.platform.config.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.platform.audit.service.PlatformAuditService;
import com.dms.platform.config.dto.PlatformMenuDTO;
import com.dms.platform.config.dto.PlatformMenuSaveRequest;
import com.dms.platform.config.entity.PlatformMenu;
import com.dms.platform.config.repository.PlatformMenuRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PlatformMenuService {

    private static final String CACHE_PREFIX = "dms:cfg:menu:";

    private final PlatformMenuRepository menuRepository;
    private final RedissonClient redisson;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public List<PlatformMenuDTO> list(String tenantType, Boolean visibleOnly) {
        List<PlatformMenu> menus;
        if (tenantType != null && !tenantType.isBlank()) {
            menus = menuRepository.findByTenantTypeInAndStatusOrderBySortOrderAsc(
                    List.of("ALL", tenantType), "active");
        } else {
            menus = menuRepository.findByStatusOrderBySortOrderAsc("active");
        }
        return menus.stream()
                .filter(m -> Boolean.FALSE.equals(visibleOnly) ? true : Boolean.TRUE.equals(m.getVisible()))
                .map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformMenuDTO> listForTenant(String tenantType) {
        String key = CACHE_PREFIX + tenantType;
        @SuppressWarnings("unchecked")
        RBucket<List<PlatformMenuDTO>> bucket = redisson.getBucket(key);
        List<PlatformMenuDTO> cached = bucket.get();
        if (cached != null) {
            return cached;
        }
        List<PlatformMenuDTO> result = list(tenantType, true);
        bucket.set(result, 30, TimeUnit.MINUTES);
        return result;
    }

    @Transactional
    public PlatformMenuDTO create(PlatformMenuSaveRequest request) {
        if (menuRepository.existsByMenuKey(request.getMenuKey())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "菜单 key 已存在");
        }
        OffsetDateTime now = OffsetDateTime.now();
        PlatformMenu menu = PlatformMenu.builder()
                .menuKey(request.getMenuKey())
                .parentKey(request.getParentKey())
                .label(request.getLabel())
                .icon(request.getIcon())
                .route(request.getRoute())
                .permissionCode(request.getPermissionCode())
                .tenantType(request.getTenantType())
                .visible(request.getVisible() == null ? true : request.getVisible())
                .sortOrder(request.getSortOrder() == null ? 100 : request.getSortOrder())
                .status("active")
                .updatedAt(now)
                .build();
        menu = menuRepository.save(menu);
        evictAll();
        auditService.log("MENU_CREATE", "platform_menu", String.valueOf(menu.getId()),
                Map.of("menuKey", menu.getMenuKey(), "label", menu.getLabel()));
        return toDTO(menu);
    }

    @Transactional
    public PlatformMenuDTO update(Long id, PlatformMenuSaveRequest request) {
        PlatformMenu menu = menuRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "菜单不存在"));
        menu.setParentKey(request.getParentKey());
        menu.setLabel(request.getLabel());
        menu.setIcon(request.getIcon());
        menu.setRoute(request.getRoute());
        menu.setPermissionCode(request.getPermissionCode());
        menu.setTenantType(request.getTenantType());
        if (request.getVisible() != null) menu.setVisible(request.getVisible());
        if (request.getSortOrder() != null) menu.setSortOrder(request.getSortOrder());
        menu.setUpdatedAt(OffsetDateTime.now());
        menu = menuRepository.save(menu);
        evictAll();
        auditService.log("MENU_UPDATE", "platform_menu", String.valueOf(id),
                Map.of("menuKey", menu.getMenuKey()));
        return toDTO(menu);
    }

    @Transactional
    public void setStatus(Long id, boolean active) {
        PlatformMenu menu = menuRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "菜单不存在"));
        menu.setStatus(active ? "active" : "disabled");
        menu.setUpdatedAt(OffsetDateTime.now());
        menuRepository.save(menu);
        evictAll();
        auditService.log(active ? "MENU_ENABLE" : "MENU_DISABLE", "platform_menu", String.valueOf(id),
                Map.of("status", menu.getStatus()));
    }

    public void refreshCache() {
        evictAll();
        auditService.log("MENU_REFRESH_CACHE", "platform_menu", null, null);
    }

    private void evictAll() {
        redisson.getKeys().deleteByPattern(CACHE_PREFIX + "*");
    }

    private PlatformMenuDTO toDTO(PlatformMenu m) {
        return PlatformMenuDTO.builder()
                .id(m.getId())
                .menuKey(m.getMenuKey())
                .parentKey(m.getParentKey())
                .label(m.getLabel())
                .icon(m.getIcon())
                .route(m.getRoute())
                .permissionCode(m.getPermissionCode())
                .tenantType(m.getTenantType())
                .visible(m.getVisible())
                .sortOrder(m.getSortOrder())
                .status(m.getStatus())
                .build();
    }
}