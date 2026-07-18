/*
 * 盘点单仓储。
 */
package com.dms.inventory.repository;

import com.dms.inventory.entity.Stocktake;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {
    Page<Stocktake> findByTenantId(UUID tenantId, Pageable pageable);
}
