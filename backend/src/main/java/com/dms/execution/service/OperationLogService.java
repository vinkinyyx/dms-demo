/*
 * v3.4.11 统一操作日志服务
 * 记录所有单据的下单/审核/收发货/取消等操作，供详情页时间轴展示
 */
package com.dms.execution.service;

import com.dms.common.util.TenantContext;
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
public class OperationLogService {

    private final EntityManager em;

    /**
     * 记录一条操作日志（独立事务，不影响主流程）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String resourceType, Long resourceId, String action, String detail) {
        try {
            UUID tid = TenantContext.getTenantId();
            Long uid = TenantContext.getUserId();
            em.createNativeQuery(
                    "INSERT INTO audit_logs (tenant_id, user_id, action, resource_type, resource_id, after, ip, at_time) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5, CAST(?6 AS jsonb), '127.0.0.1', now())")
                .setParameter(1, tid).setParameter(2, uid)
                .setParameter(3, action).setParameter(4, resourceType)
                .setParameter(5, String.valueOf(resourceId))
                .setParameter(6, detail == null ? "{}" : "{\"note\":\"" + detail.replace("\"", "'") + "\"}")
                .executeUpdate();
        } catch (Exception e) {
            log.warn("记录操作日志失败 {}/{} {}: {}", resourceType, resourceId, action, e.getMessage());
        }
    }
}
