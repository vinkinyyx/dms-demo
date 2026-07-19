/*
 * 仓库仓储接口。
 */
package com.dms.masterdata.repository;

import com.dms.masterdata.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long>, JpaSpecificationExecutor<Warehouse> {

    Page<Warehouse> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndDealerIdAndCode(UUID tenantId, Long dealerId, String code);
}
