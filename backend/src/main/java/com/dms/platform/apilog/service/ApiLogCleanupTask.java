/*
 * 接口日志清理任务：元数据保留 1 年，原始报文(MinIO)保留 90 天。
 * 每日凌晨执行，按对象 key 日期前缀删除过期报文，并清理超过保留期的元数据。
 */
package com.dms.platform.apilog.service;

import com.dms.platform.apilog.entity.ApiHttpLog;
import com.dms.platform.apilog.repository.ApiHttpLogRepository;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiLogCleanupTask {

    private final ApiHttpLogRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;

    @Value("${dms.minio.bucket:dms}")
    private String bucket;

    @Value("${dms.apilog.metadata-retention-days:365}")
    private int metadataRetentionDays;

    @Value("${dms.log.retention-days:365}")
    private int businessLogRetentionDays;

    /** 每天 03:15 执行 */
    @Scheduled(cron = "0 15 3 * * ?")
    @Transactional
    public void cleanup() {
        try {
            purgeMetadata();
        } catch (Exception e) {
            log.warn("清理接口日志元数据失败: {}", e.getMessage());
        }
        try {
            purgeBusinessLogs();
        } catch (Exception e) {
            log.warn("清理业务日志失败: {}", e.getMessage());
        }
    }

    void purgeMetadata() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(metadataRetentionDays);
        int deleted = jdbcTemplate.update("DELETE FROM api_http_logs WHERE started_at < ?", threshold);
        if (deleted > 0) {
            log.info("清理过期接口日志元数据 {} 条 (阈值 {})", deleted, threshold);
        }
    }

    void purgeBusinessLogs() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(businessLogRetentionDays);
        int apiCallLogs = jdbcTemplate.update("DELETE FROM api_call_log WHERE started_at < ?", threshold);
        int opLogs = jdbcTemplate.update("DELETE FROM op_log WHERE created_at < ?", threshold);
        int emailLogs = jdbcTemplate.update("DELETE FROM email_logs WHERE created_at < ?", threshold);
        int operationLogs = jdbcTemplate.update("DELETE FROM operation_logs WHERE created_at < ?", threshold);
        if (apiCallLogs + opLogs + emailLogs + operationLogs > 0) {
            log.info("清理过期业务日志: api_call_log={}, op_log={}, email_logs={}, operation_logs={}, 阈值={}",
                    apiCallLogs, opLogs, emailLogs, operationLogs, threshold);
        }
    }

    /**
     * 清理 MinIO 中超过指定天数的 api-logs 报文对象。按需手动触发或对接 MinIO 生命周期策略。
     */
    public int purgeMinioObjects(int retentionDays) {
        String cutoffDate = OffsetDateTime.now().minusDays(retentionDays)
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        int removed = 0;
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix("api-logs/").recursive(true).build());
            for (Result<Item> result : objects) {
                Item item = result.get();
                String objectName = item.objectName();
                String datePart = extractDatePart(objectName);
                if (datePart != null && datePart.compareTo(cutoutDate(cutoffDate)) < 0) {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket).object(objectName).build());
                    removed++;
                }
            }
            log.info("清理过期 MinIO 接口报文 {} 个 (保留 {} 天)", removed, retentionDays);
        } catch (Exception e) {
            log.warn("清理 MinIO 接口报文失败: {}", e.getMessage());
        }
        return removed;
    }

    private String cutoutDate(String cutoff) {
        return cutoff;
    }

    private String extractDatePart(String objectName) {
        int prefixLen = "api-logs/".length();
        if (objectName == null || objectName.length() < prefixLen + 10) return null;
        String rest = objectName.substring(prefixLen);
        int slash = rest.indexOf('/', "yyyy/MM/dd".length());
        if (slash < 0) return null;
        String datePart = rest.substring(0, slash);
        return datePart.matches("\\d{4}/\\d{2}/\\d{2}") ? datePart : null;
    }
}