/*
 * 客户代金券仓储接口。
 */
package com.dms.voucher.repository;

import com.dms.voucher.entity.CustomerVoucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerVoucherRepository
        extends JpaRepository<CustomerVoucher, Long>, JpaSpecificationExecutor<CustomerVoucher> {

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    Optional<CustomerVoucher> findByIdAndTenantId(Long id, UUID tenantId);

    Page<CustomerVoucher> findByTenantId(UUID tenantId, Pageable pageable);

    Page<CustomerVoucher> findByTenantIdAndDealerId(UUID tenantId, Long dealerId, Pageable pageable);

    List<CustomerVoucher> findByTenantIdAndDealerIdAndStatus(UUID tenantId, Long dealerId, String status);
}
