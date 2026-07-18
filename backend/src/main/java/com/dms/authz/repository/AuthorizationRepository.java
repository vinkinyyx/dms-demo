/*
 * 授权仓储接口。
 */
package com.dms.authz.repository;

import com.dms.authz.entity.Authorization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuthorizationRepository extends JpaRepository<Authorization, Long> {

    Page<Authorization> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT a FROM Authorization a WHERE a.tenantId = :tenantId AND a.dealerId = :dealerId " +
            "AND a.authType = :authType AND a.status = 'active' " +
            "AND a.validFrom <= :at AND a.validTo >= :at")
    List<Authorization> findActive(@Param("tenantId") UUID tenantId,
                                    @Param("dealerId") Long dealerId,
                                    @Param("authType") String authType,
                                    @Param("at") LocalDate at);
}
