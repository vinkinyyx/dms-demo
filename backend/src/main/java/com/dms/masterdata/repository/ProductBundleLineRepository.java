package com.dms.masterdata.repository;

import com.dms.masterdata.entity.ProductBundleLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductBundleLineRepository extends JpaRepository<ProductBundleLine, Long>, JpaSpecificationExecutor<ProductBundleLine> {

    List<ProductBundleLine> findByTenantIdAndBundleId(UUID tenantId, Long bundleId);

    List<ProductBundleLine> findByTenantIdAndBundleIdAndLineType(UUID tenantId, Long bundleId, String lineType);

    Optional<ProductBundleLine> findByTenantIdAndBundleIdAndChildProductId(UUID tenantId, Long bundleId, Long childProductId);

    long countByTenantIdAndBundleIdAndLineType(UUID tenantId, Long bundleId, String lineType);
}
