package com.dms.platform.config.repository;

import com.dms.platform.config.entity.TenantFilterConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantFilterConfigRepository extends JpaRepository<TenantFilterConfig, Long> {
    List<TenantFilterConfig> findByTenantIdAndPageKeyAndStatusOrderBySortOrderAsc(UUID tenantId, String pageKey, String status);
    TenantFilterConfig findByTenantIdAndPageKeyAndFilterKeyAndStatus(UUID tenantId, String pageKey, String filterKey, String status);
}
