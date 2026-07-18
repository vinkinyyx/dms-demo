/*
 * 测试目标：验证 JsonMapConverter 序列化/反序列化行为，含 null 与非法输入。
 * 覆盖用户故事：US-1.5（JSONB 属性存储）、US-4.x 促销规则 rule_detail 存储。
 */
package com.dms.common.jpa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonMapConverterTest {

    private final JsonMapConverter converter = new JsonMapConverter();

    @Test
    @DisplayName("正常流程：Map 序列化为 JSON 字符串再反序列化保持等价")
    void should_roundTripSuccessfully_when_normalMap() {
        Map<String, Object> src = new HashMap<>();
        src.put("mode", "BLOCK");
        src.put("minQty", 10);
        src.put("tiers", List.of(Map.of("amount", 1000, "reduce", 100)));

        String json = converter.convertToDatabaseColumn(src);
        Map<String, Object> back = converter.convertToEntityAttribute(json);

        assertThat(json).contains("BLOCK").contains("minQty");
        assertThat(back).containsEntry("mode", "BLOCK");
        assertThat(back).containsKey("tiers");
    }

    @Test
    @DisplayName("边界：null 输入序列化为 '{}'")
    void should_returnEmptyJson_when_serializeNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("{}");
    }

    @Test
    @DisplayName("边界：空字符串反序列化返回空 Map")
    void should_returnEmptyMap_when_dbDataBlank() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    @DisplayName("异常分支：非法 JSON 反序列化返回空 Map，不抛出异常")
    void should_returnEmptyMap_when_dbDataInvalid() {
        Map<String, Object> result = converter.convertToEntityAttribute("not-a-json{}");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("正常流程：空 Map 序列化仍为 '{}'")
    void should_returnEmptyBraces_when_emptyMap() {
        String json = converter.convertToDatabaseColumn(new HashMap<>());
        assertThat(json).isEqualTo("{}");
    }
}
