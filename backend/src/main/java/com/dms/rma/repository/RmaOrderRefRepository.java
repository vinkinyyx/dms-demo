/*
 * RMA 订单关联出库单仓储。
 */
package com.dms.rma.repository;

import com.dms.rma.entity.RmaOrderRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RmaOrderRefRepository extends JpaRepository<RmaOrderRef, Long> {
    List<RmaOrderRef> findByRmaIdOrderByIdAsc(Long rmaId);
    List<RmaOrderRef> findByRmaIdInOrderByIdAsc(Collection<Long> rmaIds);
}
