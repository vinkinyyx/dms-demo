from pathlib import Path
p=Path('backend/src/main/java/com/dms/platform/config/service/UiConfigService.java')
s=p.read_text(encoding='utf-8')
s=s.replace('import com.dms.platform.config.entity.PlatformFilterConfig;', 'import com.dms.platform.config.entity.PlatformFilterConfig;\nimport com.dms.platform.config.entity.TenantFilterConfig;')
s=s.replace('import com.dms.platform.config.repository.PlatformFilterConfigRepository;', 'import com.dms.platform.config.repository.PlatformFilterConfigRepository;\nimport com.dms.platform.config.repository.TenantFilterConfigRepository;')
s=s.replace('import java.util.Map;', 'import java.util.Map;\nimport java.util.UUID;')
s=s.replace('private static final String FILTER_CACHE_PREFIX = "dms:cfg:filter:";', 'private static final String FILTER_CACHE_PREFIX = "dms:cfg:filter:";\n    private static final String TENANT_FILTER_CACHE_PREFIX = "dms:cfg:tenant-filter:";')
s=s.replace('private final PlatformFilterConfigRepository filterConfigRepository;', 'private final PlatformFilterConfigRepository filterConfigRepository;\n    private final TenantFilterConfigRepository tenantFilterConfigRepository;')
old='''    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<FilterConfigDTO> filtersForTenant(String pageKey, String tenantType) {
        String cacheKey = FILTER_CACHE_PREFIX + tenantType + ":" + pageKey;
        RBucket<List<FilterConfigDTO>> bucket = redisson.getBucket(cacheKey);
        List<FilterConfigDTO> cached = bucket.get();
        if (cached != null) {
            return cached;
        }
        // D13: 鍏堟寜绉熸埛绫诲瀷鏌ワ紝鏃犳暟鎹椂 fallback 鍒?ALL
        List<FilterConfigDTO> result = listFilterConfigs(pageKey, tenantType);
        if (result == null || result.isEmpty()) {
            result = listFilterConfigs(pageKey, "ALL");
        }
        bucket.set(result, 30, TimeUnit.MINUTES);
        return result;
    }
'''
new='''    @Transactional(readOnly = true)
    public List<FilterConfigDTO> filtersForTenant(String pageKey, String tenantType, UUID tenantId) {
        List<FilterConfigDTO> base = listFilterConfigs(pageKey, tenantType);
        if (base == null || base.isEmpty()) {
            base = listFilterConfigs(pageKey, "ALL");
        }
        if (tenantId == null) {
            return base;
        }
        List<TenantFilterConfig> overrides = tenantFilterConfigRepository
                .findByTenantIdAndPageKeyAndStatusOrderBySortOrderAsc(tenantId, pageKey, "active");
        if (overrides.isEmpty()) {
            return base;
        }
        Map<String, FilterConfigDTO> merged = new LinkedHashMap<>();
        for (FilterConfigDTO f : base) {
            merged.put(f.getFilterKey(), f);
        }
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
        return merged.values().stream().sorted((a, b) -> {
            int sa = a.getSortOrder() == null ? 100 : a.getSortOrder();
            int sb = b.getSortOrder() == null ? 100 : b.getSortOrder();
            return Integer.compare(sa, sb);
        }).toList();
    }

    @Transactional
    public List<FilterConfigDTO> upsertTenantFilters(String pageKey, String tenantType, UUID tenantId, List<FilterConfigDTO> filters) {
        List<TenantFilterConfig> existing = tenantFilterConfigRepository
                .findByTenantIdAndPageKeyAndStatusOrderBySortOrderAsc(tenantId, pageKey, "active");
        if (!existing.isEmpty()) {
            tenantFilterConfigRepository.deleteAll(existing);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (filters != null) {
            for (FilterConfigDTO f : filters) {
                tenantFilterConfigRepository.save(TenantFilterConfig.builder()
                        .tenantId(tenantId)
                        .pageKey(pageKey)
                        .filterKey(f.getFilterKey())
                        .label(f.getLabel())
                        .componentType(f.getComponentType())
                        .dictType(f.getDictType())
                        .defaultValue(f.getDefaultValue())
                        .multiple(Boolean.TRUE.equals(f.getMultiple()))
                        .visible(f.getVisible() == null || f.getVisible())
                        .sortOrder(f.getSortOrder() == null ? 100 : f.getSortOrder())
                        .status("active")
                        .updatedAt(now)
                        .build());
            }
        }
        redisson.getBucket(TENANT_FILTER_CACHE_PREFIX + tenantId + ":" + pageKey).delete();
        auditService.log("TENANT_FILTER_CONFIG_UPSERT", "tenant_filter_config", pageKey,
                Map.of("count", filters == null ? 0 : filters.size()));
        return filtersForTenant(pageKey, tenantType, tenantId);
    }
'''
if old not in s:
    raise SystemExit('old filters method not found')
s=s.replace(old,new)
p.write_text(s, encoding='utf-8', newline='\n')
