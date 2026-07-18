/*
 * 合同申请仓储接口。
 */
package com.dms.contract.repository;

import com.dms.contract.entity.ContractApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContractApplicationRepository extends JpaRepository<ContractApplication, Long> {
    Page<ContractApplication> findByTenantId(UUID tenantId, Pageable pageable);
}
