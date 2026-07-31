package com.dms.masterdata.repository;

import com.dms.masterdata.entity.ProductPackageLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductPackageLevelRepository extends JpaRepository<ProductPackageLevel, Long>, JpaSpecificationExecutor<ProductPackageLevel> {

    List<ProductPackageLevel> findByTenantIdAndProductId(UUID tenantId, Long productId);

    List<ProductPackageLevel> findByTenantIdAndProductIdAndParentIdIsNull(UUID tenantId, Long productId);

    List<ProductPackageLevel> findByTenantIdAndProductIdAndLevel(UUID tenantId, Long productId, Integer level);

    List<ProductPackageLevel> findByTenantIdAndParentId(UUID tenantId, Long parentId);

    boolean existsByTenantIdAndProductIdAndCode(UUID tenantId, Long productId, String code);

    boolean existsByTenantIdAndProductIdAndCodeAndIdNot(UUID tenantId, Long productId, String code, Long id);
}
