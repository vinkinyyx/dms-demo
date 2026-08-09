package com.dms.approval.repository;

import com.dms.approval.entity.ApprovalTemplate;
import com.dms.approval.entity.ApprovalTemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, Long> {
    List<ApprovalTemplate> findByTenantIdAndBusinessTypeOrderByPriorityDescVersionNoDescIdDesc(UUID tenantId, String businessType);
    List<ApprovalTemplate> findByTenantIdAndBusinessTypeAndStatusOrderByPriorityDescVersionNoDescIdDesc(UUID tenantId, String businessType, ApprovalTemplateStatus status);
    Optional<ApprovalTemplate> findFirstByTenantIdAndBusinessTypeAndCodeOrderByVersionNoDescIdDesc(UUID tenantId, String businessType, String code);
    boolean existsByTenantIdAndBusinessTypeAndCodeAndVersionNo(UUID tenantId, String businessType, String code, Integer versionNo);
}
