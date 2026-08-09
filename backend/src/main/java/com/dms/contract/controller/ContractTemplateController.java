package com.dms.contract.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.contract.dto.TemplateRequest;
import com.dms.contract.entity.ContractTemplate;
import com.dms.contract.service.ContractTemplateService;
import com.dms.contract.service.TemplateDocxParser;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/contract-templates")
@RequiredArgsConstructor
public class ContractTemplateController {

    private final ContractTemplateService service;
    private final TemplateDocxParser parser;
    private final EntityManager em;

    @Value("${dms.file.storage-root:/data/dms-files}")
    private String storageRoot;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(page, size, category, status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractTemplate> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/match")
    public ApiResponse<ContractTemplate> match(@RequestParam String category) {
        return ApiResponse.ok(service.matchPublished(category));
    }

    @PostMapping
    public ApiResponse<ContractTemplate> create(@RequestBody TemplateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ContractTemplate> update(@PathVariable Long id, @RequestBody TemplateRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ContractTemplate> publish(@PathVariable Long id) {
        return ApiResponse.ok(service.publish(id));
    }

    @PostMapping("/{id}/new-version")
    public ApiResponse<ContractTemplate> newVersion(@PathVariable Long id) {
        return ApiResponse.ok(service.newVersion(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        service.disable(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/parse-docx")
    public ApiResponse<List<Map<String, Object>>> parseDocx(@RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(parser.parse(file.getInputStream()));
    }

    @PostMapping("/upload-and-parse")
    public ApiResponse<Map<String, Object>> uploadAndParse(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "请选择文件");
        if (file.getSize() > 50L * 1024 * 1024) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "文件不能超过 50MB");
        UUID tid = TenantContext.getTenantId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");

        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        Path dir = Paths.get(storageRoot, tid.toString(), "contract-template", today);
        Files.createDirectories(dir);
        String original = file.getOriginalFilename() == null ? "template.docx" : file.getOriginalFilename();
        String ext = original.lastIndexOf('.') > 0 ? original.substring(original.lastIndexOf('.')) : ".docx";
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = dir.resolve(stored);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String rel = Paths.get(tid.toString(), "contract-template", today, stored).toString().replace("\\", "/");

        List<Map<String, Object>> fields;
        try (InputStream in = Files.newInputStream(target)) {
            fields = parser.parse(in);
        }

        Number fileId = (Number) em.createNativeQuery(
                "INSERT INTO files (tenant_id, biz_type, original_name, stored_name, rel_path, content_type, size_bytes, uploaded_by) " +
                "VALUES (?1, 'contract-template', ?2, ?3, ?4, ?5, ?6, ?7) RETURNING id")
                .setParameter(1, tid)
                .setParameter(2, original)
                .setParameter(3, stored)
                .setParameter(4, rel)
                .setParameter(5, file.getContentType())
                .setParameter(6, file.getSize())
                .setParameter(7, TenantContext.getUserId())
                .getSingleResult();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("fileId", fileId.longValue());
        res.put("originalName", original);
        res.put("fields", fields);
        return ApiResponse.ok(res);
    }
}