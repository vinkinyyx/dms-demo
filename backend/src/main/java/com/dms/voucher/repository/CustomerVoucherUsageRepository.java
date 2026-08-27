/*
 * 客户代金券使用记录仓储接口。
 */
package com.dms.voucher.repository;

import com.dms.voucher.entity.CustomerVoucherUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerVoucherUsageRepository extends JpaRepository<CustomerVoucherUsage, Long> {

    Optional<CustomerVoucherUsage> findFirstByVoucherIdAndStatusOrderByIdDesc(Long voucherId, String status);

    List<CustomerVoucherUsage> findByVoucherIdOrderByIdDesc(Long voucherId);

    List<CustomerVoucherUsage> findByOrderId(Long orderId);

    long countByTenantIdAndVoucherIdAndStatus(UUID tenantId, Long voucherId, String status);
}
