/*
 * JSONB 与 Map<String,Object> 之间的 JPA 属性转换器，基于 Jackson 序列化。
 */
package com.dms.common.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用 Map<String,Object> 与 JSON 字符串互转的 JPA Converter，用于映射 PostgreSQL JSONB 字段。
 */
@Slf4j
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            log.error("JsonMapConverter 序列化失败: {}", e.getMessage(), e);
            return "{}";
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE_REF);
        } catch (Exception e) {
            log.error("JsonMapConverter 反序列化失败: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }
}
