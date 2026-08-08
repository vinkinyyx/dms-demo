package com.dms.platform.apilog.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    @DisplayName("JSON 中密码/token/手机号字段被脱敏")
    void should_maskSensitiveJsonFields() {
        String input = "{\"username\":\"alice\",\"password\":\"secret123\",\"accessToken\":\"abc.def\",\"phone\":\"13800000000\"}";
        String out = LogSanitizer.sanitize(input);
        assertThat(out).contains("alice");
        assertThat(out).doesNotContain("secret123");
        assertThat(out).doesNotContain("abc.def");
        assertThat(out).doesNotContain("13800000000");
        assertThat(out).contains("***");
    }

    @Test
    @DisplayName("嵌套对象中的敏感字段也被脱敏")
    void should_maskNestedFields() {
        String input = "{\"data\":{\"newPassword\":\"pw\",\"idCard\":\"110101199001011234\"}}";
        String out = LogSanitizer.sanitize(input);
        assertThat(out).doesNotContain("pw");
        assertThat(out).doesNotContain("110101199001011234");
    }

    @Test
    @DisplayName("非 JSON 文本中的 key=value 敏感字段被替换")
    void should_maskKeyValuePairsInPlainText() {
        String input = "Authorization=Bearer xyz password=hunter2 other=keep";
        String out = LogSanitizer.sanitize(input);
        assertThat(out).doesNotContain("hunter2");
        assertThat(out).doesNotContain("Bearer xyz");
        assertThat(out).contains("other=keep");
    }

    @Test
    @DisplayName("超长输入被截断到 32KB")
    void should_truncateLongInput() {
        String input = "{\"data\":\"" + "x".repeat(40000) + "\"}";
        String out = LogSanitizer.sanitize(input);
        assertThat(out.length()).isLessThanOrEqualTo(32 * 1024 + 10);
    }
}