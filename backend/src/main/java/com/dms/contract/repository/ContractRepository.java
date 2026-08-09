package com.dms.contract.repository;

import com.dms.contract.entity.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    Page<Contract> findByTenantId(UUID tenantId, Pageable pageable);
    Page<Contract> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    List<Contract> findByTenantIdAndStatusAndValidToBefore(UUID tenantId, String status, LocalDate date);
}
