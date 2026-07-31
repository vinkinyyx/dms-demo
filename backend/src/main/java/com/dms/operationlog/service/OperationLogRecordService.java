package com.dms.operationlog.service;

import com.dms.operationlog.entity.OpLogEntry;
import com.dms.operationlog.sanitize.OpLogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;

/**
 * 全链路操作日志记录入口（v3.6.2 R1+R4）。
 *
 * - 同步入队（业务侧不会阻塞）
 * - 后台 worker 异步执行：DB 入库 + 文件写入（委托 OpLogPersistRunner）
 * - DB 失败时 log.warn 不抛
 * - 走独立事务（REQUIRES_NEW）避免影响主业务事务
 */
@Slf4j
@Service
public class OperationLogRecordService {

    @Autowired
    private OpLogSanitizer sanitizer;

    @Autowired
    @Qualifier("opLogExecutor")
    private ExecutorService opLogExecutor;

    @Autowired
    private OpLogPersistRunner persistRunner;

    public void record(OpLogEntry entry) {
        if (entry == null) return;
        try {
            if (entry.getRequestBody() != null) {
                entry.setRequestBody(sanitizer.sanitize(entry.getRequestBody()));
            }
            if (entry.getResponse() != null) {
                entry.setResponse(sanitizer.sanitize(entry.getResponse()));
            }
        } catch (Exception e) {
            log.warn("sanitize op_log failed: {}", e.getMessage());
        }
        try {
            opLogExecutor.submit(() -> persistRunner.persist(entry));
        } catch (Exception e) {
            log.warn("op_log submit failed (queue full?): {}", e.getMessage());
        }
    }
}
