package com.dms.operationlog.controller;

import com.dms.common.util.TenantContext;
import com.dms.operationlog.filewriter.OpLogFileWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 操作日志管理控制器（v3.6.2 R5）。
 * 仅 admin（SUPER_ADMIN）角色可访问，下载指定日期的 op_log 文件。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/op-logs")
@RequiredArgsConstructor
public class OpLogAdminController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int RETENTION_DAYS = 7;

    @Autowired
    private OpLogFileWriter opLogFileWriter;

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam("date") String date) throws IOException {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(403).body("Forbidden".getBytes());
        }
        
        String username = TenantContext.getUsername();
        log.info("OpLogAdminController - download requested by user: {}, date: {}", username, date);
        
        LocalDate ld;
        try {
            ld = LocalDate.parse(date, DATE_FMT);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("invalid date format, expected yyyy-MM-dd".getBytes());
        }
        long daysAgo = ChronoUnit.DAYS.between(ld, LocalDate.now());
        if (daysAgo < 0 || daysAgo > RETENTION_DAYS) {
            return ResponseEntity.badRequest()
                    .body(("date must be within last " + RETENTION_DAYS + " days").getBytes());
        }

        Path file = opLogFileWriter.resolveFile(ld.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        log.info("OpLogAdminController - resolved file: {}, exists: {}", 
                file != null ? file.toAbsolutePath() : null, 
                file != null && Files.exists(file));
        
        if (file == null || !Files.exists(file)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=op-log-" + date + ".txt")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("No log file found for date: " + date).getBytes());
        }
        byte[] content = Files.readAllBytes(file);
        log.info("OpLogAdminController - file size: {} bytes", content.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=op-log-" + date + ".txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(content);
    }

    private boolean isSuperAdmin() {
        String username = TenantContext.getUsername();
        log.info("OpLogAdminController - username from TenantContext: {}", username);
        return "admin".equalsIgnoreCase(username);
    }
}
