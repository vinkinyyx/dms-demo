/*
 * 销售事实表仓储。
 */
package com.dms.sales.repository;

import com.dms.sales.entity.SalesOutFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalesOutFactRepository extends JpaRepository<SalesOutFact, Long> {
}
