/*
 * RMA 订单关系化明细仓储。
 */
package com.dms.rma.repository;

import com.dms.rma.entity.RmaOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RmaOrderLineRepository extends JpaRepository<RmaOrderLine, Long> {
    List<RmaOrderLine> findByRmaIdOrderBySeqAscIdAsc(Long rmaId);
    List<RmaOrderLine> findByRmaIdInOrderByRmaIdAscSeqAscIdAsc(Collection<Long> rmaIds);
}
