/*
 * 经销商-dealer 绑定仓储。
 */
package com.dms.tenant.repository;

import com.dms.tenant.entity.TenantDealerBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantDealerBindingRepository extends JpaRepository<TenantDealerBinding, Long> {

    Optional<TenantDealerBinding> findByDealerTenantId(UUID dealerTenantId);

    List<TenantDealerBinding> findByManufacturerTenantId(UUID manufacturerTenantId);

    boolean existsByManufacturerTenantIdAndDealerIdAndStatus(UUID manufacturerTenantId, Long dealerId, String status);
}
