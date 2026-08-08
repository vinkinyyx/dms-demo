package com.dms.platform.config.repository;

import com.dms.platform.config.entity.PlatformPageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformPageConfigRepository extends JpaRepository<PlatformPageConfig, Long> {
    List<PlatformPageConfig> findByPageKeyAndTenantTypeAndStatusOrderBySortOrderAsc(
            String pageKey, String tenantType, String status);
}