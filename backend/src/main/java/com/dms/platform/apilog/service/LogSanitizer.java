/*
 * 接口日志脱敏：密码、token、Authorization、secret、手机号、身份证等。
 * 对 JSON 字符串按键名递归脱敏；对非 JSON 做关键字正则替换。
 */
package com.dms.platform.apilog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
public final class LogSanitizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_LEN = 32 * 1024;
    private static final String MASK = "***";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "oldPassword", "newPassword", "confirmPassword",
            "token", "accessToken", "refreshToken", "authorization",
            "secret", "appSecret", "clientSecret",
            "phone", "mobile", "idCard", "idCardNo", "idNumber", "bankCard"
    );

    private static final Pattern KV_PATTERN = Pattern.compile(
            "(?i)(password|token|authorization|secret|phone|mobile|idcard|idnumber)\"?\\s*[:=]\\s*\"?[^&,\"}\\s]+");

    private LogSanitizer() {
    }

    public static String sanitize(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        String value = input.length() > MAX_LEN ? input.substring(0, MAX_LEN) : input;
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode node = MAPPER.readTree(value);
                mask(node);
                return MAPPER.writeValueAsString(node);
            } catch (Exception e) {
                return maskByRegex(value);
            }
        }
        return maskByRegex(value);
    }

    private static void mask(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (isSensitiveKey(entry.getKey())) {
                    obj.put(entry.getKey(), MASK);
                } else {
                    mask(entry.getValue());
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode child : arr) {
                mask(child);
            }
        }
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        for (String s : SENSITIVE_KEYS) {
            if (lower.contains(s.toLowerCase())) return true;
        }
        return false;
    }

    private static String maskByRegex(String value) {
        return KV_PATTERN.matcher(value).replaceAll(mr -> {
            String group = mr.group();
            int idx = indexOfKeyValueSeparator(group);
            if (idx < 0) return group;
            return group.substring(0, idx + 1) + "\"" + MASK + "\"";
        });
    }

    private static int indexOfKeyValueSeparator(String s) {
        int colon = s.indexOf(':');
        int eq = s.indexOf('=');
        if (colon < 0) return eq;
        if (eq < 0) return colon;
        return Math.min(colon, eq);
    }
}