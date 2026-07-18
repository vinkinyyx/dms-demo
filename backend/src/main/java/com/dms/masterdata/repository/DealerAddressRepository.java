/*
 * 经销商地址仓储接口。
 */
package com.dms.masterdata.repository;

import com.dms.masterdata.entity.DealerAddress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DealerAddressRepository extends JpaRepository<DealerAddress, Long> {

    Page<DealerAddress> findByTenantId(UUID tenantId, Pageable pageable);

    List<DealerAddress> findByTenantIdAndDealerId(UUID tenantId, Long dealerId);
}
