package com.dms.masterdata.repository;
import java.util.Optional;

import com.dms.masterdata.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
    Optional<Supplier> findByTenantIdAndCode(UUID tenantId, String code);
}