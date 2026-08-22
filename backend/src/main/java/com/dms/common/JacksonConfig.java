package com.dms.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Configuration
public class JacksonConfig {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");


    static class OffsetDateTimeSpaceDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<OffsetDateTime> {
        @Override
        public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws java.io.IOException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) return null;
            text = text.trim().replace(' ', 'T');
            try {
                if (text.length() <= 10) return java.time.LocalDate.parse(text).atStartOfDay(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
                if (text.length() == 16) return java.time.LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
                if (text.length() == 19) return java.time.LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
                return OffsetDateTime.parse(text);
            } catch (Exception e) {
                throw ctxt.weirdStringException(text, OffsetDateTime.class, "Unsupported datetime format");
            }
        }
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .timeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                .simpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME))
                .serializerByType(LocalDate.class, new LocalDateSerializer(DATE))
                .deserializerByType(LocalDate.class, new LocalDateDeserializer(DATE))
                .deserializerByType(OffsetDateTime.class, new OffsetDateTimeSpaceDeserializer())
                .serializers(new com.fasterxml.jackson.databind.ser.std.ToStringSerializerBase(OffsetDateTime.class) {    @Override public String valueToString(Object value) { return DATE_TIME.format(((OffsetDateTime) value).atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime()); }});
    }
}
