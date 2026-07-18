/*
 * 库存调整明细行仓储。
 */
package com.dms.inventory.repository;

import com.dms.inventory.entity.AdjustmentLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdjustmentLineRepository extends JpaRepository<AdjustmentLine, Long> {
    List<AdjustmentLine> findByAdjustmentId(Long adjustmentId);
}
