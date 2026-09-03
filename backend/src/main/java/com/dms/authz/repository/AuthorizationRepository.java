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
import java.util.Collection;

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

    /**
     * 排他校验候选：同租户、状态在给定集合内、有效期与 [from,to] 重叠的授权。
     * 重叠条件：existing.valid_from <= :to AND existing.valid_to >= :from。
     */
    @Query("SELECT a FROM Authorization a WHERE a.tenantId = :tenantId " +
            "AND a.status IN :statuses " +
            "AND a.validFrom <= :validTo AND a.validTo >= :validFrom")
    List<Authorization> findOverlapCandidates(@Param("tenantId") UUID tenantId,
                                               @Param("validFrom") LocalDate validFrom,
                                               @Param("validTo") LocalDate validTo,
                                               @Param("statuses") Collection<String> statuses);
}
