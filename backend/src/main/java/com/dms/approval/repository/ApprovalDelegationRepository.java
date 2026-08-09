package com.dms.approval.repository;

import com.dms.approval.entity.ApprovalDelegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, Long> {
    List<ApprovalDelegation> findByTenantIdAndDelegatorIdAndStatusAndStartsAtBeforeAndEndsAtAfter(
            UUID tenantId, Long delegatorId, String status, OffsetDateTime now1, OffsetDateTime now2);
    Optional<ApprovalDelegation> findFirstByTenantIdAndDelegatorIdAndStatusOrderByEndsAtDesc(UUID tenantId, Long delegatorId, String status);
}
