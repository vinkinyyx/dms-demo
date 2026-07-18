/*
 * 借货单仓储。
 */
package com.dms.inventory.repository;

import com.dms.inventory.entity.Loan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
    Page<Loan> findByTenantId(UUID tenantId, Pageable pageable);
}
