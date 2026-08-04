/*
 * v3.8.7 通用文件上传/下载（本地存储）。
 * 后续如需 OSS/MinIO 适配，在此处增加策略即可。
 */
package com.dms.common.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    @PersistenceContext
    private EntityManager em;

    @Value("${dms.file.storage-root:/data/dms-files}")
    private String storageRoot;

    @PostMapping("/upload")
    @Transactional
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", defaultValue = "common") String bizType) throws IOException {
        if (file.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "请选择要上传的文件");
        if (file.getSize() > 50L * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "文件不能超过 50MB");
        }
        UUID tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");

        String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date());
        Path dir = Paths.get(storageRoot, tid.toString(), bizType, today);
        Files.createDirectories(dir);
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > 0) ext = original.substring(dot);
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = dir.resolve(stored);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        String rel = Paths.get(tid.toString(), bizType, today, stored).toString().replace("\\", "/");

        Long fileId = ((Number) em.createNativeQuery(
                "INSERT INTO files (tenant_id, biz_type, original_name, stored_name, rel_path, content_type, size_bytes, uploaded_by) "
                + "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8) RETURNING id")
                .setParameter(1, tid)
                .setParameter(2, bizType)
                .setParameter(3, original)
                .setParameter(4, stored)
                .setParameter(5, rel)
                .setParameter(6, file.getContentType())
                .setParameter(7, file.getSize())
                .setParameter(8, uid)
                .getSingleResult()).longValue();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("fileId", fileId);
        res.put("originalName", original);
        res.put("url", "/api/files/" + fileId + "/download");
        res.put("sizeBytes", file.getSize());
        return ApiResponse.ok(res);
    }

    @GetMapping("/{fileId}/download")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> download(@PathVariable Long fileId) throws IOException {
        UUID tid = TenantContext.getTenantId();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT original_name, rel_path, content_type FROM files WHERE id = ?1 AND tenant_id = ?2")
                .setParameter(1, fileId).setParameter(2, tid).getResultList();
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        Object[] r = rows.get(0);
        String name = String.valueOf(r[0]);
        String rel = String.valueOf(r[1]);
        String ct = r[2] == null ? "application/octet-stream" : String.valueOf(r[2]);
        Path p = Paths.get(storageRoot, rel);
        if (!Files.exists(p)) throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        byte[] data = Files.readAllBytes(p);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType(ct))
                .body(data);
    }
}
