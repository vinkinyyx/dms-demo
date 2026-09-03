package com.dms.approval.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.entity.ApprovalTask;
import com.dms.approval.entity.ApprovalTaskStatus;
import com.dms.approval.repository.ApprovalInstanceRepository;
import com.dms.approval.repository.ApprovalTaskRepository;
import com.dms.notification.mail.ApprovalMailNotifier;
import com.dms.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTimeoutReminderTask {
    private final ApprovalTaskRepository taskRepository;
    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalMailNotifier mailNotifier;
    private final SystemSettingService systemSettingService;

    @Value("${dms.approval.reminder.interval-hours:24}")
    private int defaultIntervalHours;

    @Value("${dms.approval.reminder.max-count:3}")
    private int defaultMaxCount;

    @Value("${dms.approval.reminder.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${dms.approval.reminder.cron:0 0 9 * * ?}")
    @Transactional
    public void remindOverdueTasks() {
        if (!enabled) return;
        if (!systemSettingService.isApprovalReminderMailEnabled()) {
            log.info("定时审批提醒邮件已被开关关闭，跳过本次发送");
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<ApprovalTask> overdue = taskRepository.findByStatusAndDueAtBefore(ApprovalTaskStatus.PENDING, now);
        int reminded = 0;
        Map<Long, ApprovalInstance> instanceCache = new HashMap<>();
        for (ApprovalTask task : overdue) {
            ApprovalInstance instance = instanceCache.computeIfAbsent(task.getInstanceId(),
                    id -> instanceRepository.findById(id).orElse(null));
            if (instance == null) continue;
            if (!shouldRemind(task, now)) continue;
            try {
                mailNotifier.sendReminderMail(task, instance);
                task.setRemindedCount((task.getRemindedCount() == null ? 0 : task.getRemindedCount()) + 1);
                task.setLastRemindedAt(now);
                task.setUpdatedAt(now);
                taskRepository.save(task);
                reminded++;
            } catch (Exception e) {
                log.warn("approval timeout reminder failed: taskId={}, err={}", task.getId(), e.getMessage());
            }
        }
        if (reminded > 0) log.info("approval timeout reminders sent: {}", reminded);
    }

    private boolean shouldRemind(ApprovalTask task, OffsetDateTime now) {
        int max = defaultMaxCount;
        int interval = defaultIntervalHours;
        if (task.getRemindedCount() != null && task.getRemindedCount() >= max) return false;
        if (task.getLastRemindedAt() != null
                && task.getLastRemindedAt().plusHours(interval).isAfter(now)) {
            return false;
        }
        return true;
    }
}