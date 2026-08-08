package com.dms.platform.audit.repository;

import com.dms.platform.audit.entity.PlatformAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, Long>, JpaSpecificationExecutor<PlatformAuditLog> {
}
