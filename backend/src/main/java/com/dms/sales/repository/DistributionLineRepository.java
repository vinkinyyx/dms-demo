/*
 * 分销明细行仓储。
 */
package com.dms.sales.repository;

import com.dms.sales.entity.DistributionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DistributionLineRepository extends JpaRepository<DistributionLine, Long> {
    List<DistributionLine> findByShipmentId(Long shipmentId);
}
