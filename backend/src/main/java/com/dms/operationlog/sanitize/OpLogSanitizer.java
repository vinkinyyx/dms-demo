package com.dms.operationlog.sanitize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 操作日志脱敏器（v3.6.2 R3）。
 * 递归遍历 JSON，将敏感键名对应的值替换为 ***。
 * 敏感键名（大小写不敏感）：password/pwd/token/access_token/refresh_token/
 * dms_access_token/phone/mobile/email/idCard/id_card/uscNo/bankAccount
 */
@Slf4j
@Component
public class OpLogSanitizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "pwd", "token", "accesstoken", "access_token",
            "refreshtoken", "refresh_token", "dms_access_token",
            "phone", "mobile", "email", "idcard", "id_card", "uscno",
            "bankaccount", "bank_account"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<![0-9])(1[3-9])\\d{4}(\\d{4})(?![0-9])");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String sanitize(String json) {
        if (json == null || json.isEmpty()) return json;
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode sanitized = sanitizeNode(root);
            return MAPPER.writeValueAsString(sanitized);
        } catch (Exception e) {
            return sanitizePlain(json);
        }
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) return node;
        if (node.isObject()) return sanitizeObject((ObjectNode) node);
        if (node.isArray()) return sanitizeArray((ArrayNode) node);
        if (node.isTextual()) {
            return MAPPER.getNodeFactory().textNode(sanitizePlain(node.asText()));
        }
        return node;
    }

    private JsonNode sanitizeObject(ObjectNode obj) {
        ObjectNode out = obj.objectNode();
        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if (isSensitive(key)) {
                out.put(key, "***");
            } else {
                out.set(key, sanitizeNode(e.getValue()));
            }
        }
        return out;
    }

    private JsonNode sanitizeArray(ArrayNode arr) {
        ArrayNode out = arr.arrayNode();
        for (JsonNode el : arr) out.add(sanitizeNode(el));
        return out;
    }

    private boolean isSensitive(String key) {
        if (key == null) return false;
        return SENSITIVE_KEYS.contains(key.toLowerCase());
    }

    private String sanitizePlain(String text) {
        if (text == null) return null;
        String out = PHONE_PATTERN.matcher(text).replaceAll("$1****$2");
        out = EMAIL_PATTERN.matcher(out).replaceAll("***@***.***");
        return out;
    }

    public static void main(String[] args) {
        OpLogSanitizer s = new OpLogSanitizer();
        String[] cases = {
                "{\"username\":\"admin\",\"password\":\"Sh123456\"}",
                "{\"token\":\"abc.def.ghi\",\"refresh_token\":\"xyz\",\"name\":\"test\"}",
                "{\"phone\":\"13800138000\",\"email\":\"a@b.com\",\"idCard\":\"110101199001011234\"}",
                "{\"user\":{\"name\":\"u1\",\"bankAccount\":\"6228480402564890018\"},\"list\":[{\"access_token\":\"t1\"}]}",
                "plain text 13812345678 and a@b.com"
        };
        for (String c : cases) {
            System.out.println("[IN ] " + c);
            System.out.println("[OUT] " + s.sanitize(c));
            System.out.println("----");
        }
    }
}
