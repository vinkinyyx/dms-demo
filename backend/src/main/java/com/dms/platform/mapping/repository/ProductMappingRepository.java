/*
 * 产品对码仓储。
 */
package com.dms.platform.mapping.repository;

import com.dms.platform.mapping.entity.ProductMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductMappingRepository extends JpaRepository<ProductMapping, Long>,
        JpaSpecificationExecutor<ProductMapping> {

    Page<ProductMapping> findByManufacturerTenantId(UUID manufacturerTenantId, Pageable pageable);

    Optional<ProductMapping> findByManufacturerTenantIdAndManufacturerProductId(
            UUID manufacturerTenantId, Long manufacturerProductId);

    Optional<ProductMapping> findByManufacturerTenantIdAndDealerProductId(
            UUID manufacturerTenantId, Long dealerProductId);

    boolean existsByManufacturerTenantIdAndManufacturerProductId(UUID manufacturerTenantId, Long manufacturerProductId);

    boolean existsByManufacturerTenantIdAndDealerProductId(UUID manufacturerTenantId, Long dealerProductId);
}