/*
 * 移库明细行仓储。
 */
package com.dms.inventory.repository;

import com.dms.inventory.entity.StockMoveLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMoveLineRepository extends JpaRepository<StockMoveLine, Long> {
    List<StockMoveLine> findByMoveId(Long moveId);
}
