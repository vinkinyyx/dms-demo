/*
 * 临时授权仓储接口。
 */
package com.dms.authz.repository;

import com.dms.authz.entity.TempAuthorization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TempAuthorizationRepository extends JpaRepository<TempAuthorization, Long> {
    Page<TempAuthorization> findByTenantId(UUID tenantId, Pageable pageable);
}
