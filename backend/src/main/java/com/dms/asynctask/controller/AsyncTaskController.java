package com.dms.asynctask.controller;

import com.dms.asynctask.entity.AsyncTask;
import com.dms.asynctask.service.AsyncTaskService;
import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 异步任务查询与结果下载（BIZ-07）。
 */
@RestController
@RequestMapping("/api/async-tasks")
@RequiredArgsConstructor
public class AsyncTaskController {

    private final AsyncTaskService service;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String taskType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var p = service.list(taskType, page, size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotalElements());
        data.put("page", p.getNumber() + 1);
        data.put("size", p.getSize());
        data.put("list", p.getContent());
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResponse<AsyncTask> get(@PathVariable Long id) {
        AsyncTask task = owned(service.get(id));
        if (task == null) return ApiResponse.fail(40404, "任务不存在");
        return ApiResponse.ok(task);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        AsyncTask task = owned(service.get(id));
        if (task == null || task.getObjectKey() == null) {
            return ResponseEntity.notFound().build();
        }
        InputStream stream = service.download(task);
        if (stream == null) return ResponseEntity.notFound().build();
        String filename = task.getFileName() != null ? task.getFileName()
                : ("async-task-" + id + ".xlsx");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(stream));
    }

    private AsyncTask owned(AsyncTask task) {
        if (task == null) return null;
        UUID tid = TenantContext.getTenantId();
        if (tid != null && task.getTenantId() != null && !tid.equals(task.getTenantId())) {
            return null;
        }
        return task;
    }
}