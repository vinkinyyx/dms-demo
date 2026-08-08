/*
 * 医院 REST 控制器。
 */
package com.dms.masterdata.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.ContentDispositionUtils;
import org.springframework.web.multipart.MultipartFile;
import com.dms.masterdata.entity.Hospital;
import com.dms.masterdata.service.HospitalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hospitals")
@RequiredArgsConstructor
@Validated
public class HospitalController {

    private final HospitalService service;

    @GetMapping
    public ApiResponse<PageResult<Hospital>> list(@Valid PageQuery pageQuery,
                                                  @RequestParam(required = false) java.util.Map<String, String> allParams) {
        return ApiResponse.ok(service.list(pageQuery, allParams));
    }

    @GetMapping("/{id}")
    public ApiResponse<Hospital> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @OperationLog(businessType = "hospital", action = OperationAction.CREATE, remark = "医院-创建")
    public ApiResponse<Hospital> create(@RequestBody Hospital request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "hospital", action = OperationAction.UPDATE, remark = "医院-更新")
    public ApiResponse<Hospital> update(@PathVariable Long id, @RequestBody Hospital request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @OperationLog(businessType = "hospital", action = OperationAction.DELETE, remark = "医院-删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        PageQuery pq = new PageQuery();
        pq.setPage(1);
        pq.setSize(10000);
        java.util.List<Hospital> list = service.list(pq, null).getList();

        String[] headers = {"ID", "编码", "名称", "联系人", "电话", "地址", "状态"};
        String[] fieldNames = {"id", "code", "name", "contact", "phone", "address", "status"};

        byte[] excelBytes = ExcelExportUtils.exportToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=hospitals.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @GetMapping("/actions/export/template")
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        String[] headers = {"编码", "名称", "等级", "联系人", "联系电话", "状态"};
        String[] fieldNames = {"code", "name", "level", "contact", "phone", "status"};
        String[] examples = {"HOS-001", "示例医院", "三甲", "张三", "13800138000", "active"};

        byte[] excelBytes = ExcelExportUtils.exportTemplate(headers, fieldNames, examples);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("医院导入模板.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @PostMapping("/batch-import")
    @OperationLog(businessType = "hospital", action = OperationAction.CREATE, remark = "医院-批量导入")
    public ApiResponse<java.util.Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ApiResponse.fail(40001, "请选择要导入的文件");
        }

        java.util.List<java.util.Map<String, Object>> data = ExcelImportUtils.importFromExcel(file.getInputStream(), file.getOriginalFilename());
        if (data.isEmpty()) {
            return ApiResponse.fail(40002, "Excel 文件中没有数据");
        }

        String[] headers = {"编码", "名称", "等级", "联系人", "联系电话", "状态"};
        String[] fieldNames = {"code", "name", "level", "contact", "phone", "status"};

        int success = 0, failed = 0;
        java.util.List<java.util.Map<String, Object>> errors = new java.util.ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            java.util.Map<String, Object> row = data.get(i);
            try {
                Hospital entity = new Hospital();
                for (int j = 0; j < headers.length; j++) {
                    Object value = row.get(headers[j]);
                    if (value != null) {
                        setFieldValue(entity, fieldNames[j], value);
                    }
                }
                if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
                    throw new IllegalArgumentException("编码不能为空");
                }
                service.upsertByCode(entity);
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

    private void setFieldValue(Hospital entity, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = Hospital.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(entity, ExcelImportUtils.coerce(value, field.getType()));
    }
}
