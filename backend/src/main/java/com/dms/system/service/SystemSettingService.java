/*
 * 系统动态配置服务：读写 system_settings 表。
 * - scope='global'（tenant_id=NULL）：全局默认，如定时邮件开关；
 * - scope='tenant'（tenant_id=租户）：租户级配置，如「授权-下单挂钩」开关。
 * 数据库值优先，未配置时回退默认值；Redis 短缓存（缓存 key 带作用域），写入时立即失效。
 * 当前承载：定时邮件发送总开关 + 报表订阅/审批提醒子开关 + 授权下单强制开关。
 */
package com.dms.system.service;

import com.dms.common.util.TenantContext;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    public static final String KEY_MAIL_MASTER = "mail.schedule.enabled";
    public static final String KEY_MAIL_REPORT = "mail.schedule.report.enabled";
    public static final String KEY_MAIL_APPROVAL = "mail.schedule.approval.enabled";
    /** 授权-下单挂钩开关（租户级）：true=无有效授权禁止下单/出库；false=解耦可直接下单 */
    public static final String KEY_ORDER_AUTHZ_ENFORCE = "order.authz.enforce";

    private static final String SCOPE_GLOBAL = "global";
    private static final String SCOPE_TENANT = "tenant";
    private static final String CACHE_PREFIX = "dms:cfg:setting:";

    private final EntityManager em;
    private final RedissonClient redisson;

    @Value("${dms.mail.enabled:true}")
    private boolean ymlMailEnabled;

    /** 定时邮件总开关：DB 优先，缺失时回退 yml dms.mail.enabled */
    @Transactional(readOnly = true)
    public boolean isScheduledMailEnabled() {
        return getBoolean(KEY_MAIL_MASTER, ymlMailEnabled);
    }

    /** 报表订阅定时邮件（总开关 && 子开关） */
    @Transactional(readOnly = true)
    public boolean isReportMailEnabled() {
        return isScheduledMailEnabled() && getBoolean(KEY_MAIL_REPORT, true);
    }

    /** 审批超时提醒定时邮件（总开关 && 子开关） */
    @Transactional(readOnly = true)
    public boolean isApprovalReminderMailEnabled() {
        return isScheduledMailEnabled() && getBoolean(KEY_MAIL_APPROVAL, true);
    }
    // ==================== 租户级配置 ====================

    /**
     * 授权-下单挂钩开关（当前租户）：默认 false（不强制），即授权与下单解耦。
     * 开启后，下单/销售出库若无有效授权将被拦截。
     */
    @Transactional(readOnly = true)
    public boolean isOrderAuthzEnforced() {
        return isOrderAuthzEnforced(TenantContext.getTenantId());
    }

    @Transactional(readOnly = true)
    public boolean isOrderAuthzEnforced(UUID tenantId) {
        if (tenantId == null) return false;
        return getTenantBoolean(tenantId, KEY_ORDER_AUTHZ_ENFORCE, false);
    }

    /** 更新当前租户的授权-下单挂钩开关，返回生效值 */
    @Transactional
    public boolean setOrderAuthzEnforced(boolean enabled) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new com.dms.common.BusinessException(com.dms.common.ErrorCode.PARAM_MISSING, "缺少租户上下文");
        }
        upsertTenantBoolean(tenantId, KEY_ORDER_AUTHZ_ENFORCE, enabled,
                "授权与下单挂钩：true=无授权禁止下单/出库；false=解耦可直接下单");
        evictTenant(tenantId, KEY_ORDER_AUTHZ_ENFORCE);
        log.info("授权-下单挂钩开关已更新 tenant={} = {}", tenantId, enabled);
        return enabled;
    }

    @Transactional(readOnly = true)
    public boolean getTenantBoolean(UUID tenantId, String key, boolean defaultValue) {
        Boolean v = getStoredTenantBoolean(tenantId, key);
        return v == null ? defaultValue : v;
    }

    private Boolean getStoredTenantBoolean(UUID tenantId, String key) {
        String cacheKey = tenantCacheKey(tenantId, key);
        try {
            Object cached = redisson.getBucket(cacheKey).get();
            if (cached != null) return parseBoolean(cached);
        } catch (Exception e) {
            log.debug("读取租户配置缓存失败 key={}: {}", cacheKey, e.getMessage());
        }
        Boolean value = readTenantFromDb(tenantId, key);
        if (value != null) {
            try {
                redisson.getBucket(cacheKey).set(value, 30, java.util.concurrent.TimeUnit.MINUTES);
            } catch (Exception e) {
                log.debug("写入租户配置缓存失败 key={}: {}", cacheKey, e.getMessage());
            }
        }
        return value;
    }

    private Boolean readTenantFromDb(UUID tenantId, String key) {
        try {
            List<?> rs = em.createNativeQuery(
                    "SELECT value_json FROM system_settings WHERE scope = ?1 AND tenant_id = ?2 AND key = ?3 LIMIT 1")
                    .setParameter(1, SCOPE_TENANT)
                    .setParameter(2, tenantId)
                    .setParameter(3, key)
                    .getResultList();
            if (rs.isEmpty()) return null;
            return parseBoolean(String.valueOf(rs.get(0)));
        } catch (Exception e) {
            log.warn("读取租户配置失败 tenant={} key={}: {}", tenantId, key, e.getMessage());
            return null;
        }
    }

    private void upsertTenantBoolean(UUID tenantId, String key, boolean value, String description) {
        String json = Boolean.toString(value);
        int updated = em.createNativeQuery(
                "UPDATE system_settings SET value_json = CAST(?2 AS jsonb), updated_at = now() " +
                "WHERE scope = ?3 AND tenant_id = ?4 AND key = ?1")
                .setParameter(1, key)
                .setParameter(2, json)
                .setParameter(3, SCOPE_TENANT)
                .setParameter(4, tenantId)
                .executeUpdate();
        if (updated == 0) {
            em.createNativeQuery(
                    "INSERT INTO system_settings (scope, tenant_id, key, value_json, description) " +
                    "VALUES (?3, ?4, ?1, CAST(?2 AS jsonb), ?5)")
                    .setParameter(1, key)
                    .setParameter(2, json)
                    .setParameter(3, SCOPE_TENANT)
                    .setParameter(4, tenantId)
                    .setParameter(5, description)
                    .executeUpdate();
        }
    }

    private String tenantCacheKey(UUID tenantId, String key) {
        return CACHE_PREFIX + "tenant:" + (tenantId == null ? "none" : tenantId.toString()) + ":" + key;
    }

    private void evictTenant(UUID tenantId, String key) {
        try {
            redisson.getBucket(tenantCacheKey(tenantId, key)).delete();
        } catch (Exception e) {
            log.debug("清除租户配置缓存失败 tenant={} key={}: {}", tenantId, key, e.getMessage());
        }
    }

    /** 读取三个开关的展示视图（含 description / 当前生效值） */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMailSwitches() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(switchView(KEY_MAIL_MASTER, "定时邮件总开关",
                "关闭后系统将停止所有定时自动发送的邮件（报表订阅、审批提醒），业务操作触发的即时邮件不受影响。",
                isScheduledMailEnabled(), ymlMailEnabled));
        list.add(switchView(KEY_MAIL_REPORT, "报表订阅邮件",
                "每日 08:00 自动向订阅人发送订阅报表（CSV 附件）。",
                isReportMailEnabled(), true));
        list.add(switchView(KEY_MAIL_APPROVAL, "审批超时提醒",
                "每日 09:00 自动向待办审批人发送超时提醒邮件。",
                isApprovalReminderMailEnabled(), true));
        return list;
    }

    private Map<String, Object> switchView(String key, String label, String description, boolean effective, boolean defaultValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("description", description);
        Boolean stored = getStoredBoolean(key);
        m.put("enabled", effective);
        m.put("value", stored != null ? stored : effective);
        m.put("configured", stored != null);
        m.put("defaultValue", defaultValue);
        return m;
    }

    /** 更新某个开关，返回刷新后的开关视图 */
    @Transactional
    public List<Map<String, Object>> updateMailSwitch(String key, boolean value) {
        if (!KEY_MAIL_MASTER.equals(key) && !KEY_MAIL_REPORT.equals(key) && !KEY_MAIL_APPROVAL.equals(key)) {
            throw new IllegalArgumentException("不支持的配置项: " + key);
        }
        upsertBoolean(key, value);
        evict(key);
        log.info("定时邮件开关已更新: {} = {}", key, value);
        return getMailSwitches();
    }

    private void upsertBoolean(String key, boolean value) {
        String json = Boolean.toString(value);
        int updated = em.createNativeQuery(
                "UPDATE system_settings SET value_json = CAST(?2 AS jsonb), updated_at = now() " +
                "WHERE scope = ?3 AND tenant_id IS NULL AND key = ?1")
                .setParameter(1, key)
                .setParameter(2, json)
                .setParameter(3, SCOPE_GLOBAL)
                .executeUpdate();
        if (updated == 0) {
            em.createNativeQuery(
                    "INSERT INTO system_settings (scope, tenant_id, key, value_json, description) " +
                    "VALUES (?3, NULL, ?1, CAST(?2 AS jsonb), ?4)")
                    .setParameter(1, key)
                    .setParameter(2, json)
                    .setParameter(3, SCOPE_GLOBAL)
                    .setParameter(4, "定时邮件开关")
                    .executeUpdate();
        }
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        Boolean v = getStoredBoolean(key);
        return v == null ? defaultValue : v;
    }

    private Boolean getStoredBoolean(String key) {
        try {
            Object cached = redisson.getBucket(CACHE_PREFIX + key).get();
            if (cached != null) {
                return parseBoolean(cached);
            }
        } catch (Exception e) {
            log.debug("读取系统配置缓存失败 key={}: {}", key, e.getMessage());
        }
        Boolean value = readFromDb(key);
        if (value != null) {
            try {
                redisson.getBucket(CACHE_PREFIX + key).set(value, 30, java.util.concurrent.TimeUnit.MINUTES);
            } catch (Exception e) {
                log.debug("写入系统配置缓存失败 key={}: {}", key, e.getMessage());
            }
        }
        return value;
    }

    private Boolean readFromDb(String key) {
        try {
            List<?> rs = em.createNativeQuery(
                    "SELECT value_json FROM system_settings WHERE scope = ?1 AND tenant_id IS NULL AND key = ?2 LIMIT 1")
                    .setParameter(1, SCOPE_GLOBAL)
                    .setParameter(2, key)
                    .getResultList();
            if (rs.isEmpty()) return null;
            return parseBoolean(String.valueOf(rs.get(0)));
        } catch (Exception e) {
            log.warn("读取系统配置失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private Boolean parseBoolean(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).replace("\"", "").trim().toLowerCase();
        if (s.isEmpty() || "null".equals(s)) return null;
        if ("true".equals(s) || "1".equals(s)) return Boolean.TRUE;
        if ("false".equals(s) || "0".equals(s)) return Boolean.FALSE;
        return null;
    }

    private void evict(String key) {
        try {
            redisson.getBucket(CACHE_PREFIX + key).delete();
        } catch (Exception e) {
            log.debug("清除系统配置缓存失败 key={}: {}", key, e.getMessage());
        }
    }
}
