/*
 * 促销仓储接口。
 */
package com.dms.promotion.repository;

import com.dms.promotion.entity.Promotion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Page<Promotion> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT p FROM Promotion p WHERE p.tenantId = :tenantId AND p.status = 'active' " +
            "AND (p.validFrom IS NULL OR p.validFrom <= :now) " +
            "AND (p.validTo IS NULL OR p.validTo >= :now)")
    List<Promotion> findActive(@Param("tenantId") UUID tenantId,
                                @Param("now") OffsetDateTime now);
}
