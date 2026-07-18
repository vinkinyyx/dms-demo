/*
 * RMA 授权仓储。
 */
package com.dms.rma.repository;

import com.dms.rma.entity.RmaAuthorization;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RmaAuthorizationRepository extends JpaRepository<RmaAuthorization, Long> {

    Page<RmaAuthorization> findByTenantId(UUID tenantId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM RmaAuthorization a WHERE a.id = :id")
    Optional<RmaAuthorization> lockById(@Param("id") Long id);
}
