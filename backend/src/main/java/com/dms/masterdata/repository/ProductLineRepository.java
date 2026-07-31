package com.dms.masterdata.repository;

import com.dms.masterdata.entity.ProductLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductLineRepository extends JpaRepository<ProductLine, Long>, JpaSpecificationExecutor<ProductLine> {

    Page<ProductLine> findByTenantId(UUID tenantId, Pageable pageable);

    List<ProductLine> findByTenantIdAndStatus(UUID tenantId, String status);

    List<ProductLine> findByTenantIdAndParentId(UUID tenantId, Long parentId);

    List<ProductLine> findByTenantIdAndLevel(UUID tenantId, Integer level);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(UUID tenantId, String code, Long id);
}
