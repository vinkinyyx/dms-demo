package com.dms.operationlog.filewriter;

import com.dms.operationlog.entity.OpLogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志文件写入器（v3.6.2 R2）。
 *
 * - 按日滚动文件 /opt/dms/logs/op-YYYYMMDD.log
 * - 启动时清理 >7 天的 .log
 * - 单文件 append，不缓存（每行 flush）
 */
@Slf4j
@Component
public class OpLogFileWriter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int RETENTION_DAYS = 7;

    @Value("${oplog.file.dir:/opt/dms/logs}")
    private String dir;

    @Autowired
    private ObjectMapper objectMapper;

    private Path dirPath;

    @PostConstruct
    public void init() {
        dirPath = Paths.get(dir);
        try {
            Files.createDirectories(dirPath);
            log.info("OpLogFileWriter dir: {}", dirPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("create oplog dir failed: {}", dir, e);
        }
        cleanupOldFiles();
    }

    private void cleanupOldFiles() {
        if (dirPath == null) return;
        LocalDate cutoff = LocalDate.now().minusDays(RETENTION_DAYS);
        try (Stream<Path> paths = Files.list(dirPath)) {
            paths.filter(p -> p.getFileName().toString().startsWith("op-") && p.getFileName().toString().endsWith(".log"))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        try {
                            String dateStr = name.substring("op-".length(), name.length() - ".log".length());
                            LocalDate fileDate = LocalDate.parse(dateStr, DATE_FMT);
                            if (fileDate.isBefore(cutoff)) {
                                Files.deleteIfExists(p);
                                log.info("deleted old op_log file: {}", p);
                            }
                        } catch (Exception ignore) {
                        }
                    });
        } catch (IOException e) {
            log.warn("list op_log dir failed: {}", e.getMessage());
        }
    }

    public void write(OpLogEntry entry) {
        if (entry == null || dirPath == null) return;
        String fileName = "op-" + LocalDate.now().format(DATE_FMT) + ".log";
        Path file = dirPath.resolve(fileName);
        String line = formatLine(entry);
        try {
            Files.writeString(file, line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("write op_log file failed: {}", e.getMessage());
        }
    }

    private String formatLine(OpLogEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ts", e.getCreatedAt() == null ? OffsetDateTime.now().format(TS_FMT) : e.getCreatedAt().format(TS_FMT));
        m.put("layer", e.getLayer());
        m.put("method", e.getMethod());
        m.put("httpMethod", e.getHttpMethod());
        m.put("path", e.getPath());
        m.put("status", e.getStatus());
        m.put("spentMs", e.getSpentMs());
        m.put("user", e.getUsername());
        m.put("userId", e.getUserId());
        m.put("tenantId", e.getTenantId());
        m.put("ip", e.getIp());
        m.put("requestId", e.getRequestId());
        m.put("bizType", e.getBizType());
        m.put("bizId", e.getBizId());
        m.put("action", e.getAction());
        m.put("remark", e.getRemark());
        m.put("requestBody", truncate(e.getRequestBody(), 4096));
        m.put("response", truncate(e.getResponse(), 2048));
        m.put("stack", truncate(e.getStack(), 2048));
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception ex) {
            return m.toString();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    public Path resolveFile(String dateYyyyMMdd) {
        if (dirPath == null) return null;
        return dirPath.resolve("op-" + dateYyyyMMdd + ".log");
    }
}
