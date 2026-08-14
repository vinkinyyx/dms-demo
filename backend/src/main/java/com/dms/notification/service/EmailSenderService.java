package com.dms.notification.service;

import com.dms.notification.entity.EmailLog;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderService {

    private final JavaMailSender mailSender;
    private final EmailLogService emailLogService;

    @Value("${dms.mail.from:no-reply@dms.local}")
    private String fromAddress;

    @Value("${dms.mail.enabled:true}")
    private boolean enabled;

    public EmailLog sendNow(UUID tenantId, Long userId, String to, String subject, String content,
                            String bizType, String bizId, boolean isHtml) {
        long start = System.currentTimeMillis();
        EmailLog logEntry;
        if (!enabled) {
            String warn = "邮件功能未启用（dms.mail.enabled=false），仅记录日志";
            log.warn(warn);
            logEntry = emailLogService.log(tenantId, userId, fromAddress, to, subject, content,
                    bizType, bizId, false, warn);
            return logEntry;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to.split("[,;\\s]+"));
            helper.setSubject(subject);
            helper.setText(content, isHtml);
            mailSender.send(message);
            long duration = System.currentTimeMillis() - start;
            logEntry = emailLogService.log(tenantId, userId, fromAddress, to, subject, content,
                    bizType, bizId, true, null);
            logEntry.setDurationMs(duration);
            return logEntry;
        } catch (MessagingException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("发送邮件失败 to={} subject={}", to, subject, e);
            logEntry = emailLogService.log(tenantId, userId, fromAddress, to, subject, content,
                    bizType, bizId, false, e.getMessage());
            logEntry.setDurationMs(duration);
            return logEntry;
        }
    }

    @Async
    public void sendAsync(UUID tenantId, Long userId, String to, String subject, String content,
                          String bizType, String bizId, boolean isHtml) {
        sendNow(tenantId, userId, to, subject, content, bizType, bizId, isHtml);
    }

    public EmailLog retry(Long id) {
        EmailLog prev = emailLogService.getById(id);
        if (prev == null) {
            throw new IllegalArgumentException("邮件日志不存在");
        }
        int nextRetry = (prev.getRetries() == null ? 0 : prev.getRetries()) + 1;
        EmailLog resent = sendNow(prev.getTenantId(), prev.getRecipientUserId(),
                prev.getToAddress(), prev.getSubject(), prev.getText(),
                prev.getBizType(), prev.getBizId(), true);
        resent.setRetries(nextRetry);
        return resent;
    }
}
