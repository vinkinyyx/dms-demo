/*
 * 通知服务：当前只写站内消息表；企微/飞书 Webhook 推送打日志占位。
 */
package com.dms.notification.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.notification.entity.Notification;
import com.dms.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;

    /**
     * 发送通知：INAPP 直接写库；WECHAT_BOT/FEISHU_BOT 打印日志占位。
     */
    @Transactional
    public Notification send(Notification req) {
        if (req.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 userId");
        }
        if (req.getChannel() == null) req.setChannel("INAPP");
        req.setId(null);
        req.setTenantId(TenantContext.getTenantId());
        req.setIsRead(false);
        req.setUpdatedAt(OffsetDateTime.now());
        Notification saved = repository.save(req);
        switch (req.getChannel()) {
            case "WECHAT_BOT" -> log.info("[占位] 企微 Webhook 推送: title={} body={}", req.getTitle(), req.getBody());
            case "FEISHU_BOT" -> log.info("[占位] 飞书 Webhook 推送: title={} body={}", req.getTitle(), req.getBody());
            default -> log.debug("站内消息已写入 id={}", saved.getId());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResult<Notification> list(Boolean isRead, PageQuery pageQuery) {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        Page<Notification> page = isRead == null
                ? repository.findByUserId(userId, pageQuery.toPageable())
                : repository.findByUserIdAndIsRead(userId, isRead, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional
    public void markRead(Long id) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "通知不存在"));
        n.setIsRead(true);
        n.setUpdatedAt(OffsetDateTime.now());
        repository.save(n);
    }

    @Transactional
    public int markAllRead() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return repository.markAllRead(userId);
    }
}
