/*
 * 租户上下文，基于 ThreadLocal<Map> 存储当前请求的 tenantId、userId、username 等信息。
 */
package com.dms.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TenantContext {

    private static final String KEY_TENANT_ID = "tenantId";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USERNAME = "username";

    private static final ThreadLocal<Map<String, Object>> HOLDER = ThreadLocal.withInitial(HashMap::new);

    private TenantContext() {
    }

    public static void setTenantId(UUID tenantId) {
        HOLDER.get().put(KEY_TENANT_ID, tenantId);
    }

    public static UUID getTenantId() {
        return (UUID) HOLDER.get().get(KEY_TENANT_ID);
    }

    public static void setUserId(Long userId) {
        HOLDER.get().put(KEY_USER_ID, userId);
    }

    public static Long getUserId() {
        return (Long) HOLDER.get().get(KEY_USER_ID);
    }

    public static void setUsername(String username) {
        HOLDER.get().put(KEY_USERNAME, username);
    }

    public static String getUsername() {
        return (String) HOLDER.get().get(KEY_USERNAME);
    }

    public static void set(String key, Object value) {
        HOLDER.get().put(key, value);
    }

    public static Object get(String key) {
        return HOLDER.get().get(key);
    }

    public static Map<String, Object> snapshot() {
        return new HashMap<>(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
