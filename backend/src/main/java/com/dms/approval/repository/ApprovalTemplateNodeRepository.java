package com.dms.approval.repository;

import com.dms.approval.entity.ApprovalTemplateNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalTemplateNodeRepository extends JpaRepository<ApprovalTemplateNode, Long> {
    List<ApprovalTemplateNode> findByTemplateIdOrderByNodeOrderAscIdAsc(Long templateId);
    void deleteByTemplateId(Long templateId);
}
