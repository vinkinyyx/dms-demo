/*
 * 区域 REST 控制器。
 */
package com.dms.masterdata.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.ContentDispositionUtils;
import org.springframework.web.multipart.MultipartFile;
import com.dms.masterdata.entity.Region;
import com.dms.masterdata.service.RegionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
@Validated
public class RegionController {

    private final RegionService service;

    @GetMapping
    public ApiResponse<PageResult<Region>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<Region> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<Region> create(@RequestBody Region request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Region> update(@PathVariable Long id, @RequestBody Region request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        PageQuery pq = new PageQuery();
        pq.setPage(1);
        pq.setSize(10000);
        java.util.List<Region> list = service.list(pq).getList();

        String[] headers = {"ID", "编码", "名称", "父级", "级别", "状态"};
        String[] fieldNames = {"id", "code", "name", "parentId", "level", "status"};

        byte[] excelBytes = ExcelExportUtils.exportToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=regions.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @GetMapping("/actions/export/template")
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        String[] headers = {"编码", "名称", "父级ID", "级别", "排序"};
        String[] fieldNames = {"code", "name", "parentId", "level", "sortOrder"};
        String[] examples = {"REG-001", "示例区域", "0", "1", "1"};

        byte[] excelBytes = ExcelExportUtils.exportTemplate(headers, fieldNames, examples);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("区域导入模板.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @PostMapping("/batch-import")
    public ApiResponse<java.util.Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ApiResponse.fail(40001, "请选择要导入的文件");
        }

        java.util.List<java.util.Map<String, Object>> data = ExcelImportUtils.importFromExcel(file.getInputStream(), file.getOriginalFilename());
        if (data.isEmpty()) {
            return ApiResponse.fail(40002, "Excel 文件中没有数据");
        }

        String[] headers = {"编码", "名称", "父级ID", "级别", "排序"};
        String[] fieldNames = {"code", "name", "parentId", "level", "sortOrder"};

        int success = 0, failed = 0;
        java.util.List<java.util.Map<String, Object>> errors = new java.util.ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            java.util.Map<String, Object> row = data.get(i);
            try {
                Region entity = new Region();
                for (int j = 0; j < headers.length; j++) {
                    Object value = row.get(headers[j]);
                    if (value != null) {
                        setFieldValue(entity, fieldNames[j], value);
                    }
                }
                if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
                    throw new IllegalArgumentException("编码不能为空");
                }
                if (entity.getName() == null || entity.getName().trim().isEmpty()) {
                    throw new IllegalArgumentException("名称不能为空");
                }
                service.create(entity);
                success++;
            } catch (Exception e) {
                failed++;
                java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
                err.put("row", i + 2);
                err.put("error", e.getMessage());
                errors.add(err);
            }
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", data.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        return ApiResponse.ok(result);
    }

    private void setFieldValue(Region entity, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = Region.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<?> type = field.getType();
        if (type == String.class) {
            field.set(entity, String.valueOf(value));
        } else if (type == Long.class || type == long.class) {
            field.set(entity, ((Number) value).longValue());
        } else if (type == Integer.class || type == int.class) {
            field.set(entity, ((Number) value).intValue());
        } else {
            field.set(entity, value);
        }
    }
}
