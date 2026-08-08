/*
 * 接口日志服务：写 api_http_logs 元数据，请求/响应报文脱敏后写 MinIO，标记慢接口。
 */
package com.dms.platform.apilog.service;

import com.dms.config.MinioStorageService;
import com.dms.platform.apilog.entity.ApiHttpLog;
import com.dms.platform.apilog.repository.ApiHttpLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiHttpLogService {

    private final ApiHttpLogRepository repository;
    private final MinioStorageService minioStorage;

    @Value("${dms.apilog.slow-threshold-ms:3000}")
    private long slowThresholdMs;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Async
    public void recordAsync(ApiHttpLog entry, String requestBody, String responseBody) {
        try {
            record(entry, requestBody, responseBody);
        } catch (Exception e) {
            log.warn("write api http log failed: {}", e.getMessage());
        }
    }

    public ApiHttpLog record(ApiHttpLog entry, String requestBody, String responseBody) {
        boolean slow = entry.getSpentMs() != null && entry.getSpentMs() > slowThresholdMs;
        entry.setSlow(slow);

        String datePath = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        if (requestBody != null && !requestBody.isBlank()) {
            String sanitized = LogSanitizer.sanitize(requestBody);
            String key = objectKey("req", datePath, entry.getRequestId());
            try {
                minioStorage.put(key, sanitized.getBytes(StandardCharsets.UTF_8), "application/json");
                entry.setRequestObjectKey(key);
                entry.setRequestSize((long) sanitized.getBytes(StandardCharsets.UTF_8).length);
            } catch (Exception e) {
                log.warn("upload request body to minio failed: {}", e.getMessage());
            }
        }
        if (responseBody != null && !responseBody.isBlank()) {
            String sanitized = LogSanitizer.sanitize(responseBody);
            String key = objectKey("resp", datePath, entry.getRequestId());
            try {
                minioStorage.put(key, sanitized.getBytes(StandardCharsets.UTF_8), "application/json");
                entry.setResponseObjectKey(key);
                entry.setResponseSize((long) sanitized.getBytes(StandardCharsets.UTF_8).length);
            } catch (Exception e) {
                log.warn("upload response body to minio failed: {}", e.getMessage());
            }
        }
        return repository.save(entry);
    }

    public byte[] downloadRequest(Long id) {
        ApiHttpLog logEntry = repository.findById(id)
                .orElseThrow(() -> new com.dms.common.BusinessException(
                        com.dms.common.ErrorCode.NOT_FOUND, "API log not found"));
        if (logEntry.getRequestObjectKey() == null) {
            return new byte[0];
        }
        try (var in = minioStorage.get(logEntry.getRequestObjectKey());
             var out = new java.io.ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("download request payload failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    public byte[] downloadResponse(Long id) {
        ApiHttpLog logEntry = repository.findById(id)
                .orElseThrow(() -> new com.dms.common.BusinessException(
                        com.dms.common.ErrorCode.NOT_FOUND, "API log not found"));
        if (logEntry.getResponseObjectKey() == null) {
            return new byte[0];
        }
        try (var in = minioStorage.get(logEntry.getResponseObjectKey());
             var out = new java.io.ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("download response payload failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    private String objectKey(String kind, String datePath, String requestId) {
        String rid = (requestId == null || requestId.isBlank())
                ? java.util.UUID.randomUUID().toString() : requestId;
        return "api-logs/" + datePath + "/" + rid + "-" + kind + ".json";
    }
}
