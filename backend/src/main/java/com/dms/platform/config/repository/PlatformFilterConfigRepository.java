package com.dms.platform.config.repository;

import com.dms.platform.config.entity.PlatformFilterConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformFilterConfigRepository extends JpaRepository<PlatformFilterConfig, Long> {
    List<PlatformFilterConfig> findByPageKeyAndTenantTypeAndStatusOrderBySortOrderAsc(
            String pageKey, String tenantType, String status);
}