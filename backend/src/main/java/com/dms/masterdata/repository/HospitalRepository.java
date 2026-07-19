/*
 * 医院仓储接口。
 */
package com.dms.masterdata.repository;

import com.dms.masterdata.entity.Hospital;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, Long>, JpaSpecificationExecutor<Hospital> {

    Page<Hospital> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
