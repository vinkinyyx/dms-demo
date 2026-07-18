/*
 * 销售出库明细行仓储。
 */
package com.dms.sales.repository;

import com.dms.sales.entity.SalesOutLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesOutLineRepository extends JpaRepository<SalesOutLine, Long> {
    List<SalesOutLine> findBySalesOutId(Long salesOutId);
}
