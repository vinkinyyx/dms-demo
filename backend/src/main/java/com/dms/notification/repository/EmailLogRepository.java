/*
 * 邮件发送日志仓储。
 */
package com.dms.notification.repository;

import com.dms.notification.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    Page<EmailLog> findByTenantIdOrderByIdDesc(UUID tenantId, Pageable pageable);
    Page<EmailLog> findByTenantIdAndStatusOrderByIdDesc(UUID tenantId, String status, Pageable pageable);
}