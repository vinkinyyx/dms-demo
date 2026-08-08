/*
 * 租户上下文，基于 ThreadLocal<Map> 存储当前请求的 tenantId、userId、username 等信息。
 * 同时承载多租户隔离所需的 tenantType、ownerManufacturerId、userType、authSource。
 */
package com.dms.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TenantContext {

    private static final String KEY_TENANT_ID = "tenantId";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_TENANT_TYPE = "tenantType";
    private static final String KEY_OWNER_MANUFACTURER_ID = "ownerManufacturerId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_AUTH_SOURCE = "authSource";

    /** 业务前台 token */
    public static final String AUTH_SOURCE_TENANT = "TENANT";
    /** 平台后台 token */
    public static final String AUTH_SOURCE_PLATFORM = "PLATFORM";

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

    public static void setTenantType(String tenantType) {
        HOLDER.get().put(KEY_TENANT_TYPE, tenantType);
    }

    public static String getTenantType() {
        return (String) HOLDER.get().get(KEY_TENANT_TYPE);
    }

    public static void setOwnerManufacturerId(UUID ownerManufacturerId) {
        HOLDER.get().put(KEY_OWNER_MANUFACTURER_ID, ownerManufacturerId);
    }

    public static UUID getOwnerManufacturerId() {
        return (UUID) HOLDER.get().get(KEY_OWNER_MANUFACTURER_ID);
    }

    public static void setUserType(String userType) {
        HOLDER.get().put(KEY_USER_TYPE, userType);
    }

    public static String getUserType() {
        return (String) HOLDER.get().get(KEY_USER_TYPE);
    }

    public static void setAuthSource(String authSource) {
        HOLDER.get().put(KEY_AUTH_SOURCE, authSource);
    }

    public static String getAuthSource() {
        return (String) HOLDER.get().get(KEY_AUTH_SOURCE);
    }

    public static boolean isPlatformAdmin() {
        return AUTH_SOURCE_PLATFORM.equals(getAuthSource());
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
