/*
 * 盘点明细行仓储。
 */
package com.dms.inventory.repository;

import com.dms.inventory.entity.StocktakeLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StocktakeLineRepository extends JpaRepository<StocktakeLine, Long> {
    List<StocktakeLine> findByStocktakeId(Long stocktakeId);
}
