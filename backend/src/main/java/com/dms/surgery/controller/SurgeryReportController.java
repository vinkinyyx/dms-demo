package com.dms.surgery.controller;

import com.dms.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.enums.OperationAction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import com.dms.surgery.service.SurgeryReportService;

@RequestMapping("/api/surgery-reports")
@RestController
@RequiredArgsConstructor
@Validated
public class SurgeryReportController {

    private final SurgeryReportService service;

    @GetMapping
    public ApiResponse<Map<String, Object>> list( @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort) {
        return service.list(page, size, sort);
    }

    @PostMapping
    @OperationLog(businessType = "surgeryReport", action = OperationAction.CREATE, remark = "手术报台-创建")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return service.create(body);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getDetail(@PathVariable Long id) {
        return service.getDetail(id);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return service.delete(id);
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        return service.export();
    }

    @GetMapping("/actions/export/template")
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        return service.exportTemplate();
    }

    @PostMapping("/batch-import")
    public ApiResponse<java.util.Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        return service.batchImport(file);
    }

}
