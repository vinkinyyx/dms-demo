package com.dms.asynctask.service;

import com.dms.asynctask.entity.AsyncTask;
import com.dms.asynctask.repository.AsyncTaskRepository;
import com.dms.common.util.TenantContext;
import com.dms.config.MinioStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.Map;

/**
 * 异步任务服务（BIZ-07）：提交后台导入/导出任务，状态可查、结果可下载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskService {

    private final AsyncTaskRepository repository;
    private final MinioStorageService minio;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AsyncTask submit(String taskType, String bizType, Map<String, Object> params) {
        AsyncTask task = new AsyncTask();
        task.setTenantId(TenantContext.getTenantId());
        task.setTaskType(taskType);
        task.setBizType(bizType);
        task.setStatus("PENDING");
        task.setCreatedBy(TenantContext.getUserId());
        task.setCreatedName(TenantContext.getUsername());
        task.setParams(toJson(params));
        return repository.save(task);
    }

    /**
     * 在专用线程池中执行导出任务。租户上下文通过参数快照传入，避免 ThreadLocal 丢失。
     */
    @Async("asyncImportExportExecutor")
    public void runExport(AsyncTask task, String fileName, Supplier<byte[]> producer) {
        applyTenantContext(task);
        try {
            markRunning(task);
            byte[] bytes = producer.get();
            String objectKey = buildObjectKey(task);
            minio.put(objectKey, bytes,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            task.setFileName(fileName);
            task.setObjectKey(objectKey);
            task.setStatus("SUCCESS");
            task.setFinishedAt(OffsetDateTime.now());
            repository.save(task);
            log.info("异步导出完成 taskId={} file={} size={}", task.getId(), fileName, bytes.length);
        } catch (Exception e) {
            log.error("异步导出失败 taskId={}", task.getId(), e);
            markFailed(task, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    @Async("asyncImportExportExecutor")
    public void runImport(AsyncTask task, Supplier<ImportResult> producer) {
        applyTenantContext(task);
        try {
            markRunning(task);
            ImportResult r = producer.get();
            task.setTotalRows(r.totalRows);
            task.setSuccessRows(r.successRows);
            task.setFailedRows(r.failedRows);
            task.setFileName(r.fileName);
            if (r.errorBytes != null && r.errorBytes.length > 0 && r.errorFileName != null) {
                String objectKey = buildObjectKey(task);
                minio.put(objectKey, r.errorBytes,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                task.setObjectKey(objectKey);
                task.setFileName(r.errorFileName);
            }
            task.setStatus(r.failedRows > 0 && r.successRows == 0 ? "FAILED" : "SUCCESS");
            task.setFinishedAt(OffsetDateTime.now());
            repository.save(task);
        } catch (Exception e) {
            log.error("异步导入失败 taskId={}", task.getId(), e);
            markFailed(task, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private void applyTenantContext(AsyncTask task) {
        if (task.getTenantId() != null) TenantContext.setTenantId(task.getTenantId());
        if (task.getCreatedBy() != null) TenantContext.setUserId(task.getCreatedBy());
        if (task.getCreatedName() != null) TenantContext.setUsername(task.getCreatedName());
        TenantContext.setAuthSource(TenantContext.AUTH_SOURCE_TENANT);
    }

    public Page<AsyncTask> list(String taskType, int page, int size) {
        UUID tid = TenantContext.getTenantId();
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.min(size, 100));
        if (taskType != null && !taskType.isBlank()) {
            return repository.findByTenantIdAndTaskTypeOrderByIdDesc(tid, taskType.toUpperCase(), pr);
        }
        return repository.findByTenantIdOrderByIdDesc(tid, pr);
    }

    public AsyncTask get(Long id) {
        return repository.findById(id).orElse(null);
    }

    public InputStream download(AsyncTask task) {
        if (task == null || task.getObjectKey() == null) return null;
        return minio.get(task.getObjectKey());
    }

    private void markRunning(AsyncTask task) {
        task.setStatus("RUNNING");
        task.setStartedAt(OffsetDateTime.now());
        repository.save(task);
    }

    private void markFailed(AsyncTask task, String msg) {
        task.setStatus("FAILED");
        task.setErrorMessage(msg == null ? "unknown error" : msg.substring(0, Math.min(msg.length(), 2000)));
        task.setFinishedAt(OffsetDateTime.now());
        repository.save(task);
    }

    private String buildObjectKey(AsyncTask task) {
        UUID tid = task.getTenantId() != null ? task.getTenantId() : UUID.randomUUID();
        return "async-task/" + tid + "/" + task.getId() + ".xlsx";
    }

    private String toJson(Map<String, Object> params) {
        if (params == null) return null;
        try { return objectMapper.writeValueAsString(params); } catch (Exception e) { return null; }
    }

    public static class ImportResult {
        public int totalRows;
        public int successRows;
        public int failedRows;
        public String fileName;
        public String errorFileName;
        public byte[] errorBytes;
    }
}