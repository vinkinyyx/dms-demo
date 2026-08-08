/*
 * 按钮配置服务。
 * 读路径：业务前台按 (tenantId, pageKey, scope) 合并平台默认 + 租户覆盖。
 * 写路径：admin-vue "按钮配置" Tab 批量 upsert，区分 PLATFORM_DEFAULT / TENANT_OVERRIDE。
 * 缓存：Redis bucket，前缀 dms:cfg:button:。
 */
package com.dms.platform.config.service;

import com.dms.platform.audit.service.PlatformAuditService;
import com.dms.platform.config.dto.ButtonConfigDTO;
import com.dms.platform.config.entity.PlatformButtonConfig;
import com.dms.platform.config.repository.PlatformButtonConfigRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformButtonConfigService {

    private static final String CACHE_PREFIX = "dms:cfg:button:";
    private static final long CACHE_TTL_HOURS = 2;

    private final PlatformButtonConfigRepository repository;
    private final RedissonClient redisson;
    private final PlatformAuditService auditService;

    /**
     * 合并：平台默认 + 租户覆盖。同 (scope, buttonKey) 租户覆盖优先。
     */
    @Transactional(readOnly = true)
    public List<ButtonConfigDTO> mergedForTenant(UUID tenantId, String pageKey, String scope) {
        String cacheKey = CACHE_PREFIX + (tenantId == null ? "default" : tenantId) + ":" + pageKey + ":" + scope;
        RBucket<List<ButtonConfigDTO>> bucket = redisson.getBucket(cacheKey);
        List<ButtonConfigDTO> cached = bucket.get();
        if (cached != null) {
            return cached;
        }

        List<PlatformButtonConfig> defaults = repository
                .findByTenantIdIsNullAndPageKeyAndScopeAndStatusOrderBySortOrderAsc(pageKey, scope, "active");
        List<PlatformButtonConfig> overrides = tenantId == null ? List.of()
                : repository.findByTenantIdAndPageKeyAndScopeAndStatusOrderBySortOrderAsc(tenantId, pageKey, scope, "active");

        // 先用平台默认建表（保留顺序）
        Map<String, ButtonConfigDTO> merged = new LinkedHashMap<>();
        for (PlatformButtonConfig e : defaults) {
            merged.put(e.getScope() + ":" + e.getButtonKey(), toDTO(e, false));
        }
        // 租户覆盖覆盖同名项；额外项也追加进来
        for (PlatformButtonConfig e : overrides) {
            merged.put(e.getScope() + ":" + e.getButtonKey(), toDTO(e, true));
        }
        List<ButtonConfigDTO> result = new ArrayList<>(merged.values());
        bucket.set(result, CACHE_TTL_HOURS, TimeUnit.HOURS);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ButtonConfigDTO> adminList(String pageKey, String tenantType) {
        return repository.adminList(pageKey, tenantType, "active")
                .stream().map(e -> toDTO(e, e.getTenantId() != null)).collect(Collectors.toList());
    }

    /**
     * 批量 upsert：
     *  - scopeLevel=PLATFORM_DEFAULT：覆盖平台默认（tenant_id IS NULL）
     *  - scopeLevel=TENANT_OVERRIDE：覆盖租户覆盖（tenant_id = 当前租户）
     *  - 同一 (scope, buttonKey) 范围下"先全删后全插"，保持幂等
     */
    @Transactional
    public List<ButtonConfigDTO> upsert(String pageKey, String tenantType, String scopeLevel,
                                        UUID overrideTenantId, List<ButtonConfigDTO> buttons) {
        boolean isPlatform = "PLATFORM_DEFAULT".equalsIgnoreCase(scopeLevel);
        UUID ownerTenantId = isPlatform ? null : overrideTenantId;
        if (!isPlatform && overrideTenantId == null) {
            throw new IllegalArgumentException("租户覆盖必须提供 tenantId");
        }

        List<PlatformButtonConfig> existing = new ArrayList<>();
        for (String scope : List.of("toolbar", "row")) {
            if (isPlatform) {
                existing.addAll(repository.findByTenantIdIsNullAndPageKeyAndScopeAndStatusOrderBySortOrderAsc(pageKey, scope, "active"));
            } else {
                existing.addAll(repository.findByTenantIdAndPageKeyAndScopeAndStatusOrderBySortOrderAsc(overrideTenantId, pageKey, scope, "active"));
            }
        }
        Map<String, PlatformButtonConfig> existingByKey = new LinkedHashMap<>();
        for (PlatformButtonConfig config : existing) {
            existingByKey.put(config.getScope() + ":" + config.getButtonKey(), config);
        }

        Map<String, ButtonConfigDTO> requestedByKey = new LinkedHashMap<>();
        if (buttons != null) {
            for (ButtonConfigDTO button : buttons) {
                if (button != null && button.getScope() != null && button.getButtonKey() != null) {
                    requestedByKey.put(button.getScope() + ":" + button.getButtonKey(), button);
                }
            }
        }
        if (!isPlatform) {
            requestedByKey.putIfAbsent("toolbar:search", ButtonConfigDTO.builder()
                    .scope("toolbar").buttonKey("search").label("查询").buttonType("primary")
                    .visible(true).sortOrder(10).rowButtonPosition("common").confirmRequired(false).build());
            requestedByKey.putIfAbsent("toolbar:reset", ButtonConfigDTO.builder()
                    .scope("toolbar").buttonKey("reset").label("重置").buttonType("default")
                    .visible(true).sortOrder(20).rowButtonPosition("common").confirmRequired(false).build());
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (Map.Entry<String, ButtonConfigDTO> entry : requestedByKey.entrySet()) {
            ButtonConfigDTO button = entry.getValue();
            String key = entry.getKey();
            boolean fixedToolbarButton = !isPlatform && "toolbar".equals(button.getScope())
                    && ("search".equals(button.getButtonKey()) || "reset".equals(button.getButtonKey()));
            PlatformButtonConfig config = existingByKey.remove(key);
            if (config == null) {
                config = PlatformButtonConfig.builder()
                        .tenantId(ownerTenantId)
                        .pageKey(pageKey)
                        .tenantType(tenantType)
                        .scope(button.getScope())
                        .buttonKey(button.getButtonKey())
                        .status("active")
                        .createdAt(now)
                        .build();
            }
            config.setTenantId(ownerTenantId);
            config.setPageKey(pageKey);
            config.setTenantType(tenantType);
            config.setScope(button.getScope());
            config.setButtonKey(button.getButtonKey());
            config.setLabel(button.getLabel());
            config.setButtonType(button.getButtonType() == null ? "default" : button.getButtonType());
            config.setIcon(button.getIcon());
            config.setPermissionCode(button.getPermissionCode());
            config.setVisible(fixedToolbarButton || (button.getVisible() == null ? Boolean.TRUE : button.getVisible()));
            config.setSortOrder(button.getSortOrder() == null ? 100 : button.getSortOrder());
            config.setRowButtonPosition(button.getRowButtonPosition() == null ? "common" : button.getRowButtonPosition());
            config.setConfirmRequired(Boolean.TRUE.equals(button.getConfirmRequired()));
            config.setUpdatedAt(now);
            repository.save(config);
        }
        if (!existingByKey.isEmpty()) {
            repository.deleteAll(existingByKey.values());
        }
        evictCache(overrideTenantId, pageKey);
        auditService.log("BUTTON_CONFIG_UPSERT", "platform_button_config",
                pageKey + ":" + scopeLevel, Map.of("count", requestedByKey.size()));
        return adminList(pageKey, tenantType);
    }

    public void evictCache(UUID tenantId, String pageKey) {
        redisson.getKeys().deleteByPattern(CACHE_PREFIX + "*:" + pageKey + ":*");
    }

    public void refreshAll() {
        redisson.getKeys().deleteByPattern(CACHE_PREFIX + "*");
        auditService.log("BUTTON_CONFIG_REFRESH_CACHE", "platform_button_config", null, null);
    }

    private ButtonConfigDTO toDTO(PlatformButtonConfig e, boolean fromTenant) {
        return ButtonConfigDTO.builder()
                .id(e.getId())
                .tenantId(e.getTenantId())
                .pageKey(e.getPageKey())
                .tenantType(e.getTenantType())
                .scope(e.getScope())
                .buttonKey(e.getButtonKey())
                .label(e.getLabel())
                .buttonType(e.getButtonType())
                .icon(e.getIcon())
                .permissionCode(e.getPermissionCode())
                .visible(e.getVisible())
                .sortOrder(e.getSortOrder())
                .rowButtonPosition(e.getRowButtonPosition())
                .confirmRequired(e.getConfirmRequired())
                .status(e.getStatus())
                .fromTenant(fromTenant)
                .build();
    }
}