/*
 * 租户仓储接口，基于 Spring Data JPA 提供基础 CRUD 与租户唯一码查询。
 */
package com.dms.tenant.repository;

import com.dms.tenant.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {

    Optional<Tenant> findByCode(String code);

    boolean existsByCode(String code);

    Page<Tenant> findByTenantType(String tenantType, Pageable pageable);

    Page<Tenant> findByTenantTypeAndStatus(String tenantType, String status, Pageable pageable);

    Page<Tenant> findByOwnerManufacturerId(UUID ownerManufacturerId, Pageable pageable);
}
