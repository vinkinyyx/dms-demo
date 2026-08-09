package com.dms.approval.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.entity.ApprovalTask;
import com.dms.notification.entity.Notification;
import com.dms.notification.mail.ApprovalMailNotifier;
import com.dms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApprovalNotifier {
    private final NotificationService notificationService;
    private final ApprovalMailNotifier mailNotifier;

    public void notifyTaskCreated(ApprovalTask task, ApprovalInstance instance) {
        inapp(task.getAssigneeId(), "\u5ba1\u6279\u5f85\u529e", "\u60a8\u6709\u65b0\u7684\u5ba1\u6279\u5f85\u529e\uff1a" + instance.getTitle(), instance);
        mailNotifier.sendTaskMail(task, instance);
    }

    public void notifyCc(Long userId, ApprovalInstance instance, String stage) {
        inapp(userId, "\u5ba1\u6279\u62c4\u9001", "\u5ba1\u6279\u62c4\u9001\uff1a" + instance.getTitle(), instance);
        mailNotifier.sendCcMail(userId, instance);
    }

    public void notifyFinished(ApprovalInstance instance) {
        if (instance.getSubmitterId() != null) {
            inapp(instance.getSubmitterId(), "\u5ba1\u6279\u7ed3\u679c\u901a\u77e5", "\u5ba1\u6279" + instance.getStatus().name() + "\uff1a" + instance.getTitle(), instance);
            mailNotifier.sendResultMail(instance);
        }
    }

    private void inapp(Long userId, String title, String body, ApprovalInstance instance) {
        try {
            notificationService.send(Notification.builder()
                    .tenantId(instance.getTenantId())
                    .userId(userId)
                    .channel("INAPP")
                    .title(title)
                    .body(body)
                    .refType(instance.getBusinessType())
                    .refId(String.valueOf(instance.getBusinessId()))
                    .isRead(false)
                    .build());
        } catch (Exception ignored) {
        }
    }
}
