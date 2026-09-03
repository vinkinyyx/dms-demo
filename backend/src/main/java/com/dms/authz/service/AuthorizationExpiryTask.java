package com.dms.authz.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 授权到期任务：每天 00:10 将已过有效期、仍处于 active 的授权置为 expired。
 * not_started（未开始）已过期的情况理论上不存在（valid_from<=valid_to），不处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationExpiryTask {

    private final EntityManager em;

    @Scheduled(cron = "0 10 0 * * ?")
    @Transactional
    public void markExpired() {
        LocalDate today = LocalDate.now();
        int updated = em.createNativeQuery(
                "UPDATE authorizations SET status = 'expired', updated_at = now() " +
                "WHERE deleted_at IS NULL AND status = 'active' AND valid_to < ?1")
                .setParameter(1, today)
                .executeUpdate();
        if (updated > 0) log.info("授权到期任务：共标记 {} 条授权为 expired", updated);
    }
}
