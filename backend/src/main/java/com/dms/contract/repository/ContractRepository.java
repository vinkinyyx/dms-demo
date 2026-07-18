/*
 * 合同仓储接口。
 */
package com.dms.contract.repository;

import com.dms.contract.entity.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    Page<Contract> findByTenantId(UUID tenantId, Pageable pageable);
}
