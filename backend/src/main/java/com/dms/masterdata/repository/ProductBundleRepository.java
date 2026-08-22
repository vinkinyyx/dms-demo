package com.dms.masterdata.repository;

import com.dms.masterdata.entity.ProductBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductBundleRepository extends JpaRepository<ProductBundle, Long>, JpaSpecificationExecutor<ProductBundle> {

    List<ProductBundle> findByTenantIdAndProductId(UUID tenantId, Long productId);

    List<ProductBundle> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<ProductBundle> findByTenantIdAndProductIdAndCode(UUID tenantId, Long productId, String code);

    boolean existsByTenantIdAndProductIdAndCode(UUID tenantId, Long productId, String code);

    boolean existsByTenantIdAndProductIdAndCodeAndIdNot(UUID tenantId, Long productId, String code, Long id);

    java.util.List<ProductBundle> findByTenantIdAndProductIdAndVersionStatus(UUID tenantId, Long productId, String versionStatus);

    @org.springframework.data.jpa.repository.Query("select max(b.bomVersion) from ProductBundle b where b.tenantId=?1 and b.productId=?2 and b.deletedAt is null")
    java.util.Optional<String> findMaxBomVersion(UUID tenantId, Long productId);

}
