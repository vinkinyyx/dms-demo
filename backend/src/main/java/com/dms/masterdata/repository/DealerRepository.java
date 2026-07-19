/*
 * 经销商仓储接口。
 */
package com.dms.masterdata.repository;

import com.dms.masterdata.entity.Dealer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DealerRepository extends JpaRepository<Dealer, Long>, JpaSpecificationExecutor<Dealer> {

    Page<Dealer> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
