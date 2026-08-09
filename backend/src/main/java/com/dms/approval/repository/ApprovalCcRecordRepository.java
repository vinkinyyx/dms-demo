package com.dms.approval.repository;

import com.dms.approval.entity.ApprovalCcRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApprovalCcRecordRepository extends JpaRepository<ApprovalCcRecord, Long> {
    Page<ApprovalCcRecord> findByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, Long userId, Pageable pageable);
}
