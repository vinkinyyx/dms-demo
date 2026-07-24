package com.dms.auth.service;

import com.dms.common.util.TenantContext;
import com.dms.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private static final org.slf4j.Logger LOGIN_LOGGER = org.slf4j.LoggerFactory.getLogger("LOGIN_LOGGER");

    private final EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(User user, String loginType, String ip, String userAgent) {
        try {
            var ins = em.createNativeQuery(
                    "INSERT INTO user_login_logs (tenant_id, user_id, login_type, ip, user_agent, success, at_time) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, true, now())");
            ins.setParameter(1, user.getTenantId());
            ins.setParameter(2, user.getId());
            ins.setParameter(3, loginType);
            ins.setParameter(4, ip);
            ins.setParameter(5, userAgent);
            ins.executeUpdate();
            // 同时记录到日志文件
            LOGIN_LOGGER.info("登录成功: tenantId={}, userId={}, loginType={}, ip={}",
                    user.getTenantId(), user.getId(), loginType, ip);
            log.info("登录成功日志已记录: userId={}, loginType={}, ip={}", user.getId(), loginType, ip);
        } catch (Exception e) {
            log.warn("记录登录成功日志失败: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(UUID tenantId, Long userId, String loginType, String ip, String userAgent, String failReason) {
        try {
            var ins = em.createNativeQuery(
                    "INSERT INTO user_login_logs (tenant_id, user_id, login_type, ip, user_agent, success, fail_reason, at_time) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, false, ?6, now())");
            ins.setParameter(1, tenantId);
            ins.setParameter(2, userId);
            ins.setParameter(3, loginType);
            ins.setParameter(4, ip);
            ins.setParameter(5, userAgent);
            ins.setParameter(6, failReason);
            ins.executeUpdate();
            // 同时记录到日志文件
            LOGIN_LOGGER.info("登录失败: tenantId={}, userId={}, loginType={}, ip={}, reason={}",
                    tenantId, userId, loginType, ip, failReason);
            log.info("登录失败日志已记录: userId={}, loginType={}, ip={}, reason={}", userId, loginType, ip, failReason);
        } catch (Exception e) {
            log.warn("记录登录失败日志失败: {}", e.getMessage());
        }
    }
}
