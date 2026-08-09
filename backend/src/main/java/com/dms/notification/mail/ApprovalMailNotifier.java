package com.dms.notification.mail;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.entity.ApprovalTask;
import com.dms.notification.entity.EmailLog;
import com.dms.notification.service.EmailLogService;
import com.dms.user.entity.User;
import com.dms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalMailNotifier {
    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final EmailLogService emailLogService;

    @Value("${dms.mail.from:${spring.mail.username:}}")
    private String from;

    @Value("${dms.app.base-url:http://8.133.193.238:8083}")
    private String baseUrl;

    @Value("${dms.mail.enabled:true}")
    private boolean enabled;

    public void sendTaskMail(ApprovalTask task, ApprovalInstance instance) {
        if (!enabled) return;
        userRepository.findById(task.getAssigneeId()).ifPresent(user -> {
            if (isBlank(user.getEmail())) return;
            String subject = "\u3010\u0044\u004d\u0053\u0020\u5ba1\u6279\u5f85\u529e\u3011" + safe(instance.getTitle());
            String body = "\u60a8\u6709\u4e00\u6761\u65b0\u7684\u5ba1\u6279\u5f85\u529e\uff1a" + "\n" +
                    "\u5355\u636e\u6807\u9898\uff1a" + safe(instance.getTitle()) + "\n" +
                    "\u5355\u636e\u7f16\u53f7\uff1a" + safe(instance.getBusinessCode()) + "\n" +
                    "\u5ba1\u6279\u8282\u70b9\uff1a" + safe(task.getNodeName()) + "\n" +
                    "\u53d1\u8d77\u4eba\uff1a" + safe(instance.getSubmitterName()) + "\n" +
                    "\u8bf7\u767b\u5f55" + " DMS " + "\u5904\u7406\uff1a" + baseUrl + "/approval/todo\n";
            sendWithLog(user, subject, body, instance, "TASK");
        });
    }

    public void sendCcMail(Long userId, ApprovalInstance instance) {
        if (!enabled) return;
        userRepository.findById(userId).ifPresent(user -> {
            if (isBlank(user.getEmail())) return;
            String subject = "\u3010\u0044\u004d\u0053\u0020\u5ba1\u6279\u62c4\u9001\u3011" + safe(instance.getTitle());
            String body = "\u5ba1\u6279\u62c4\u9001\u901a\u77e5\uff1a" + "\n" +
                    "\u5355\u636e\u6807\u9898\uff1a" + safe(instance.getTitle()) + "\n" +
                    "\u5355\u636e\u7f16\u53f7\uff1a" + safe(instance.getBusinessCode()) + "\n" +
                    "\u8bf7\u767b\u5f55" + " DMS " + "\u67e5\u770b\uff1a" + baseUrl + "/approval/todo\n";
            sendWithLog(user, subject, body, instance, "CC");
        });
    }

    public void sendResultMail(ApprovalInstance instance) {
        if (!enabled || instance.getSubmitterId() == null) return;
        userRepository.findById(instance.getSubmitterId()).ifPresent(user -> {
            if (isBlank(user.getEmail())) return;
            String subject = "\u3010\u0044\u004d\u0053\u0020\u5ba1\u6279\u7ed3\u679c\u3011" + safe(instance.getTitle());
            String body = "\u5ba1\u6279\u5df2\u5b8c\u6210\uff0c\u7ed3\u679c\uff1a" + instance.getStatus().name() + "\n" +
                    "\u5355\u636e\u6807\u9898\uff1a" + safe(instance.getTitle()) + "\n" +
                    "\u5355\u636e\u7f16\u53f7\uff1a" + safe(instance.getBusinessCode()) + "\n" +
                    "\u8bf7\u767b\u5f55" + " DMS " + "\u67e5\u770b\u8be6\u60c5\uff1a" + baseUrl + "/approval/todo\n";
            sendWithLog(user, subject, body, instance, "RESULT");
        });
    }

    public void sendReminderMail(ApprovalTask task, ApprovalInstance instance) {
        if (!enabled) return;
        userRepository.findById(task.getAssigneeId()).ifPresent(user -> {
            if (isBlank(user.getEmail())) return;
            String subject = "\u3010\u0044\u004d\u0053\u0020\u5ba1\u6279\u8d85\u65f6\u63d0\u9192\u3011" + safe(instance.getTitle());
            String body = "\u60a8\u6709\u4e00\u6761\u5ba1\u6279\u4efb\u52a1\u5df2\u8d85\u8fc7\u5904\u7406\u65f6\u9650\uff0c\u8bf7\u5c3d\u5feb\u5904\u7406\uff1a" + "\n" +
                    "\u5355\u636e\u6807\u9898\uff1a" + safe(instance.getTitle()) + "\n" +
                    "\u5355\u636e\u7f16\u53f7\uff1a" + safe(instance.getBusinessCode()) + "\n" +
                    "\u5ba1\u6279\u8282\u70b9\uff1a" + safe(task.getNodeName()) + "\n" +
                    "\u53d1\u8d77\u4eba\uff1a" + safe(instance.getSubmitterName()) + "\n" +
                    "\u8bf7\u767b\u5f55" + " DMS " + "\u5904\u7406\uff1a" + baseUrl + "/approval/todo\n";
            sendWithLog(user, subject, body, instance, "REMINDER");
        });
    }

    private void sendWithLog(User user, String subject, String text, ApprovalInstance instance, String mailType) {
        boolean success = false;
        String error = null;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(user.getEmail());
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            success = true;
            log.info("approval mail sent: to={}, subject={}", user.getEmail(), subject);
        } catch (Exception e) {
            error = e.getMessage();
            log.warn("approval mail send failed: to={}, subject={}, err={}", user.getEmail(), subject, error);
        }
        try {
            EmailLog saved = emailLogService.log(instance.getTenantId(), user.getId(), from, user.getEmail(), subject, text,
                    "APPROVAL_" + mailType, String.valueOf(instance.getBusinessId()), success, error);
            if (!success) {
                log.warn("approval mail logged as FAILED: logId={}, to={}", saved.getId(), user.getEmail());
            }
        } catch (Exception logEx) {
            log.warn("save approval mail log failed: {}", logEx.getMessage());
        }
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private String safe(String s) { return s == null ? "" : s; }
}
