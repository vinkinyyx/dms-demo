package com.dms.common.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Converter
public class JsonListConverter implements AttributeConverter<List<Map<String, Object>>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<Map<String, Object>> attribute) {
        if (attribute == null) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            log.error("JsonListConverter serialize failed: {}", e.getMessage(), e);
            return "[]";
        }
    }

    @Override
    public List<Map<String, Object>> convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE_REF);
        } catch (Exception e) {
            log.error("JsonListConverter deserialize failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}
