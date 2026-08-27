package com.dms.masterdata.repository;

import com.dms.masterdata.entity.DealerContact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DealerContactRepository extends JpaRepository<DealerContact, Long> {

    Page<DealerContact> findByTenantId(UUID tenantId, Pageable pageable);

    List<DealerContact> findByTenantIdAndDealerIdOrderByIsDefaultDescIdAsc(UUID tenantId, Long dealerId);

    Optional<DealerContact> findByIdAndTenantIdAndDealerId(Long id, UUID tenantId, Long dealerId);

    long countByTenantIdAndDealerId(UUID tenantId, Long dealerId);
}
