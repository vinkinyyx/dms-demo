package com.dms.approval.repository;

import com.dms.approval.entity.ApprovalTemplateCc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalTemplateCcRepository extends JpaRepository<ApprovalTemplateCc, Long> {
    List<ApprovalTemplateCc> findByTemplateIdOrderByIdAsc(Long templateId);
    void deleteByTemplateId(Long templateId);
}
