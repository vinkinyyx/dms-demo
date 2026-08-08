package com.dms.platform.config.repository;

import com.dms.platform.config.entity.PlatformMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformMenuRepository extends JpaRepository<PlatformMenu, Long> {

    Optional<PlatformMenu> findByMenuKey(String menuKey);

    List<PlatformMenu> findByStatusOrderBySortOrderAsc(String status);

    List<PlatformMenu> findByTenantTypeInAndStatusOrderBySortOrderAsc(List<String> tenantTypes, String status);

    boolean existsByMenuKey(String menuKey);
}