package com.dms.approval.repository;

import com.dms.approval.entity.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {
    List<ApprovalRecord> findByInstanceIdOrderByCreatedAtAscIdAsc(Long instanceId);
}
