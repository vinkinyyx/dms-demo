/*
 * 页面字段与筛选项配置服务：后台批量 upsert，前台按 pageKey + 租户类型读取，带 Redis 缓存。
 */
package com.dms.platform.config.service;

import com.dms.platform.audit.service.PlatformAuditService;
import com.dms.platform.config.dto.FilterConfigDTO;
import com.dms.platform.config.dto.PageConfigDTO;
import com.dms.platform.config.entity.PlatformFilterConfig;
import com.dms.platform.config.entity.TenantFilterConfig;
import com.dms.platform.config.entity.PlatformPageConfig;
import com.dms.platform.config.repository.PlatformFilterConfigRepository;
import com.dms.platform.config.repository.TenantFilterConfigRepository;
import com.dms.platform.config.repository.PlatformPageConfigRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UiConfigService {

    private static final String PAGE_CACHE_PREFIX = "dms:cfg:page:";
    private static final String FILTER_CACHE_PREFIX = "dms:cfg:filter:";

    private final PlatformPageConfigRepository pageConfigRepository;
    private final PlatformFilterConfigRepository filterConfigRepository;
    private final TenantFilterConfigRepository tenantFilterConfigRepository;
    private final RedissonClient redisson;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public List<PageConfigDTO> listPageConfigs(String pageKey, String tenantType) {
        return pageConfigRepository
                .findByPageKeyAndTenantTypeAndStatusOrderBySortOrderAsc(pageKey, tenantType, "active")
                .stream().map(this::toPageDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<FilterConfigDTO> listFilterConfigs(String pageKey, String tenantType) {
        return filterConfigRepository
                .findByPageKeyAndTenantTypeAndStatusOrderBySortOrderAsc(pageKey, tenantType, "active")
                .stream().map(this::toFilterDTO).toList();
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<PageConfigDTO> pageForTenant(String pageKey, String tenantType) {
        String cacheKey = PAGE_CACHE_PREFIX + tenantType + ":" + pageKey;
        RBucket<List<PageConfigDTO>> bucket = redisson.getBucket(cacheKey);
        List<PageConfigDTO> cached = bucket.get();
        if (cached != null) {
            return cached;
        }
        List<PageConfigDTO> result = listPageConfigs(pageKey, tenantType);
        bucket.set(result, 30, TimeUnit.MINUTES);
        return result;
    }

    @Transactional(readOnly = true)
    public List<FilterConfigDTO> filtersForTenant(String pageKey, String tenantType, UUID tenantId) {
        List<FilterConfigDTO> base = listFilterConfigs(pageKey, tenantType);
        if (base == null || base.isEmpty()) {
            base = listFilterConfigs(pageKey, "ALL");
        }
        if (tenantId == null) return base;
        List<TenantFilterConfig> overrides = tenantFilterConfigRepository
                .findByTenantIdAndPageKeyAndStatusOrderBySortOrderAsc(tenantId, pageKey, "active");
        if (overrides.isEmpty()) return base;
        Map<String, FilterConfigDTO> merged = new LinkedHashMap<>();
        for (FilterConfigDTO f : base) merged.put(f.getFilterKey(), f);
        for (TenantFilterConfig e : overrides) {
            FilterConfigDTO baseDto = merged.get(e.getFilterKey());
            merged.put(e.getFilterKey(), FilterConfigDTO.builder()
                    .id(e.getId())
                    .pageKey(e.getPageKey())
                    .tenantType(tenantType)
                    .filterKey(e.getFilterKey())
                    .label(e.getLabel())
                    .componentType(e.getComponentType())
                    .dictType(e.getDictType() != null ? e.getDictType() : baseDto == null ? null : baseDto.getDictType())
                    .defaultValue(e.getDefaultValue() != null ? e.getDefaultValue() : baseDto == null ? null : baseDto.getDefaultValue())
                    .multiple(e.getMultiple() != null ? e.getMultiple() : baseDto != null && Boolean.TRUE.equals(baseDto.getMultiple()))
                    .visible(e.getVisible())
                    .sortOrder(e.getSortOrder())
                    .status(e.getStatus())
                    .build());
        }
        return merged.values().stream().sorted((a, b) -> Integer.compare(a.getSortOrder() == null ? 100 : a.getSortOrder(), b.getSortOrder() == null ? 100 : b.getSortOrder())).toList();
    }

    @Transactional
    public List<FilterConfigDTO> upsertTenantFilters(String pageKey, String tenantType, UUID tenantId, List<FilterConfigDTO> filters) {
        List<TenantFilterConfig> existing = tenantFilterConfigRepository
                .findByTenantIdAndPageKeyAndStatusOrderBySortOrderAsc(tenantId, pageKey, "active");
        Map<String, TenantFilterConfig> existingByKey = new LinkedHashMap<>();
        for (TenantFilterConfig config : existing) {
            existingByKey.put(config.getFilterKey(), config);
        }
        Map<String, FilterConfigDTO> requestedByKey = new LinkedHashMap<>();
        if (filters != null) {
            for (FilterConfigDTO filter : filters) {
                if (filter != null && filter.getFilterKey() != null && !filter.getFilterKey().isBlank()) {
                    requestedByKey.put(filter.getFilterKey(), filter);
                }
            }
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (Map.Entry<String, FilterConfigDTO> entry : requestedByKey.entrySet()) {
            FilterConfigDTO filter = entry.getValue();
            TenantFilterConfig config = existingByKey.remove(entry.getKey());
            if (config == null) {
                config = TenantFilterConfig.builder()
                        .tenantId(tenantId)
                        .pageKey(pageKey)
                        .filterKey(entry.getKey())
                        .status("active")
                        .updatedAt(now)
                        .build();
            }
            config.setLabel(filter.getLabel());
            config.setComponentType(filter.getComponentType());
            config.setDictType(filter.getDictType());
            config.setDefaultValue(filter.getDefaultValue());
            config.setMultiple(Boolean.TRUE.equals(filter.getMultiple()));
            config.setVisible(filter.getVisible() == null || filter.getVisible());
            config.setSortOrder(filter.getSortOrder() == null ? 100 : filter.getSortOrder());
            config.setUpdatedAt(now);
            tenantFilterConfigRepository.save(config);
        }
        if (!existingByKey.isEmpty()) {
            tenantFilterConfigRepository.deleteAll(existingByKey.values());
        }
        auditService.log("TENANT_FILTER_CONFIG_UPSERT", "tenant_filter_config", pageKey,
                Map.of("count", requestedByKey.size()));
        return filtersForTenant(pageKey, tenantType, tenantId);
    }

    @Transactional
    public List<PageConfigDTO> upsertPageConfigs(String pageKey, String tenantType, List<PageConfigDTO> fields) {
        List<PlatformPageConfig> existing = pageConfigRepository
                .findByPageKeyAndTenantTypeAndStatusOrderBySortOrderAsc(pageKey, tenantType, "active");
        pageConfigRepository.deleteAll(existing);
        OffsetDateTime now = OffsetDateTime.now();
        if (fields != null) {
            for (PageConfigDTO f : fields) {
                PlatformPageConfig e = PlatformPageConfig.builder()
                        .pageKey(pageKey)
                        .tenantType(tenantType)
                        .fieldKey(f.getFieldKey())
                        .label(f.getLabel())
                        .visible(f.getVisible() == null ? true : f.getVisible())
                        .readonly(Boolean.TRUE.equals(f.getReadonly()))
                        .required(Boolean.TRUE.equals(f.getRequired()))
                        .exportable(f.getExportable() == null ? true : f.getExportable())
                        .sortOrder(f.getSortOrder() == null ? 100 : f.getSortOrder())
                        .width(f.getWidth())
                        .config(f.getConfig() == null ? new HashMap<>() : f.getConfig())
                        .status("active")
                        .updatedAt(now)
                        .build();
                pageConfigRepository.save(e);
            }
        }
        evictPage(pageKey, tenantType);
        auditService.log("PAGE_CONFIG_UPSERT", "platform_page_config", pageKey + ":" + tenantType,
                Map.of("count", fields == null ? 0 : fields.size()));
        return listPageConfigs(pageKey, tenantType);
    }

    @Transactional
    public List<FilterConfigDTO> upsertFilterConfigs(String pageKey, String tenantType, List<FilterConfigDTO> filters) {
        List<PlatformFilterConfig> existing = filterConfigRepository
                .findByPageKeyAndTenantTypeAndStatusOrderBySortOrderAsc(pageKey, tenantType, "active");
        filterConfigRepository.deleteAll(existing);
        OffsetDateTime now = OffsetDateTime.now();
        if (filters != null) {
            for (FilterConfigDTO f : filters) {
                PlatformFilterConfig e = PlatformFilterConfig.builder()
                        .pageKey(pageKey)
                        .tenantType(tenantType)
                        .filterKey(f.getFilterKey())
                        .label(f.getLabel())
                        .componentType(f.getComponentType())
                        .dictType(f.getDictType())
                        .defaultValue(f.getDefaultValue())
                        .multiple(Boolean.TRUE.equals(f.getMultiple()))
                        .visible(f.getVisible() == null ? true : f.getVisible())
                        .sortOrder(f.getSortOrder() == null ? 100 : f.getSortOrder())
                        .status("active")
                        .updatedAt(now)
                        .build();
                filterConfigRepository.save(e);
            }
        }
        evictFilter(pageKey, tenantType);
        auditService.log("FILTER_CONFIG_UPSERT", "platform_filter_config", pageKey + ":" + tenantType,
                Map.of("count", filters == null ? 0 : filters.size()));
        return listFilterConfigs(pageKey, tenantType);
    }

    public void refreshCache() {
        redisson.getKeys().deleteByPattern(PAGE_CACHE_PREFIX + "*");
        redisson.getKeys().deleteByPattern(FILTER_CACHE_PREFIX + "*");
        auditService.log("UI_CONFIG_REFRESH_CACHE", "platform_ui_config", null, null);
    }

    private void evictPage(String pageKey, String tenantType) {
        redisson.getBucket(PAGE_CACHE_PREFIX + tenantType + ":" + pageKey).delete();
    }

    private void evictFilter(String pageKey, String tenantType) {
        redisson.getBucket(FILTER_CACHE_PREFIX + tenantType + ":" + pageKey).delete();
    }

    private PageConfigDTO toPageDTO(PlatformPageConfig e) {
        return PageConfigDTO.builder()
                .id(e.getId())
                .pageKey(e.getPageKey())
                .tenantType(e.getTenantType())
                .fieldKey(e.getFieldKey())
                .label(e.getLabel())
                .visible(e.getVisible())
                .readonly(e.getReadonly())
                .required(e.getRequired())
                .exportable(e.getExportable())
                .sortOrder(e.getSortOrder())
                .width(e.getWidth())
                .config(e.getConfig())
                .status(e.getStatus())
                .build();
    }

    private FilterConfigDTO toFilterDTO(PlatformFilterConfig e) {
        return FilterConfigDTO.builder()
                .id(e.getId())
                .pageKey(e.getPageKey())
                .tenantType(e.getTenantType())
                .filterKey(e.getFilterKey())
                .label(e.getLabel())
                .componentType(e.getComponentType())
                .dictType(e.getDictType())
                .defaultValue(e.getDefaultValue())
                .multiple(e.getMultiple())
                .visible(e.getVisible())
                .sortOrder(e.getSortOrder())
                .status(e.getStatus())
                .build();
    }
}

