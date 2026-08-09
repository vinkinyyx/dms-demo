package com.dms.approval.repository;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.entity.ApprovalInstanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance, Long> {
    Optional<ApprovalInstance> findFirstByTenantIdAndBusinessTypeAndBusinessIdOrderByIdDesc(UUID tenantId, String businessType, Long businessId);
    Page<ApprovalInstance> findByTenantIdAndBusinessTypeAndBusinessId(UUID tenantId, String businessType, Long businessId, Pageable pageable);
    Page<ApprovalInstance> findByTenantIdAndSubmitterIdOrderByIdDesc(UUID tenantId, Long submitterId, Pageable pageable);
    Page<ApprovalInstance> findByTenantIdAndStatusOrderByIdDesc(UUID tenantId, ApprovalInstanceStatus status, Pageable pageable);
    Page<ApprovalInstance> findByTenantIdOrderByIdDesc(UUID tenantId, Pageable pageable);
}
