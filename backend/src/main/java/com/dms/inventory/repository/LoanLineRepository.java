/*
 * 借货单明细行仓储。
 */
package com.dms.inventory.repository;

import com.dms.inventory.entity.LoanLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanLineRepository extends JpaRepository<LoanLine, Long> {
    List<LoanLine> findByLoanId(Long loanId);
}
