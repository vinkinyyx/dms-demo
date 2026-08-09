/*
 * 邮件发送日志服务。
 */
package com.dms.notification.service;

import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.notification.entity.EmailLog;
import com.dms.notification.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailLogService {
    private final EmailLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailLog log(UUID tenantId, Long userId, String from, String to, String subject, String text,
                        String bizType, String bizId, boolean success, String error) {
        EmailLog entry = EmailLog.builder()
                .tenantId(tenantId)
                .recipientUserId(userId)
                .fromAddress(from)
                .toAddress(to)
                .subject(subject)
                .text(text)
                .status(success ? "SUCCESS" : "FAILED")
                .bizType(bizType)
                .bizId(bizId)
                .errorMessage(truncate(error, 2000))
                .sentAt(OffsetDateTime.now())
                .build();
        return repository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResult<EmailLog> list(String status, PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<EmailLog> page = status == null || status.isBlank()
                ? repository.findByTenantIdOrderByIdDesc(tenantId, pageQuery.toPageable())
                : repository.findByTenantIdAndStatusOrderByIdDesc(tenantId, status, pageQuery.toPageable());
        return PageResult.of(page);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}