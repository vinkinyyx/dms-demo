package com.dms.approval.repository;

import com.dms.approval.entity.ApprovalNodeAssignee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalNodeAssigneeRepository extends JpaRepository<ApprovalNodeAssignee, Long> {
    List<ApprovalNodeAssignee> findByNodeIdOrderByIdAsc(Long nodeId);
    List<ApprovalNodeAssignee> findByNodeIdInOrderByIdAsc(List<Long> nodeIds);
    void deleteByNodeId(Long nodeId);
    void deleteByNodeIdIn(List<Long> nodeIds);
}
