/*
 * 区域仓储接口。
 */
package com.dms.masterdata.repository;
import java.util.Optional;

import com.dms.masterdata.entity.Region;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {

    Page<Region> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
    java.util.Optional<Region> findByTenantIdAndCode(UUID tenantId, String code);
}
