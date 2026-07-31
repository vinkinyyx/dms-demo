package com.dms.operationlog.service;

import com.dms.operationlog.entity.OpLogEntry;
import com.dms.operationlog.filewriter.OpLogFileWriter;
import com.dms.operationlog.repository.OpLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * op_log 持久化执行器：DB 入库 + 文件写入（独立事务）。
 * 拆分独立 Bean 以确保 @Transactional 通过 Spring 代理生效。
 */
@Slf4j
@Component
public class OpLogPersistRunner {

    @Autowired
    private OpLogRepository opLogRepository;

    @Autowired
    private OpLogFileWriter opLogFileWriter;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(OpLogEntry entry) {
        try {
            opLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("op_log DB persist failed: {}", e.getMessage());
        }
        try {
            opLogFileWriter.write(entry);
        } catch (Exception e) {
            log.warn("op_log file write failed: {}", e.getMessage());
        }
    }
}
