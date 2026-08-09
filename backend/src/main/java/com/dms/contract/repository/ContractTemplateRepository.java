package com.dms.contract.repository;

import com.dms.contract.entity.ContractTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {
    Page<ContractTemplate> findByTenantId(UUID tenantId, Pageable pageable);
    Page<ContractTemplate> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    Page<ContractTemplate> findByTenantIdAndCategory(UUID tenantId, String category, Pageable pageable);
    Optional<ContractTemplate> findByTenantIdAndCategoryAndStatus(UUID tenantId, String category, String status);
    Optional<ContractTemplate> findTopByTenantIdAndCodeOrderByVersionDesc(UUID tenantId, String code);
    boolean existsByTenantIdAndCodeAndVersion(UUID tenantId, String code, Integer version);
}
