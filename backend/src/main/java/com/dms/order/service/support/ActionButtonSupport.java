package com.dms.order.service.support;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ActionButtonSupport {
    private ActionButtonSupport() {}

    public static Map<String, Object> action(String key, String label, String type, String method, String path) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("key", key);
        action.put("label", label);
        action.put("type", type);
        action.put("method", method);
        action.put("path", path);
        return action;
    }
}