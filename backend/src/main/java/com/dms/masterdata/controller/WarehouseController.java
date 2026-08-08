/*
 * 仓库 REST 控制器。
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
import com.dms.masterdata.entity.Warehouse;
import com.dms.masterdata.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Validated
public class WarehouseController {

    private final WarehouseService service;

    @GetMapping
    public ApiResponse<PageResult<Warehouse>> list(@Valid PageQuery pageQuery,
                                                   @RequestParam(required = false) java.util.Map<String, String> allParams) {
        return ApiResponse.ok(service.list(pageQuery, allParams));
    }

    @GetMapping("/{id}")
    public ApiResponse<Warehouse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @OperationLog(businessType = "warehouse", action = OperationAction.CREATE, remark = "仓库-创建")
    public ApiResponse<Warehouse> create(@RequestBody Warehouse request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "warehouse", action = OperationAction.UPDATE, remark = "仓库-更新")
    public ApiResponse<Warehouse> update(@PathVariable Long id, @RequestBody Warehouse request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @OperationLog(businessType = "warehouse", action = OperationAction.DELETE, remark = "仓库-删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        PageQuery pq = new PageQuery();
        pq.setPage(1);
        pq.setSize(10000);
        java.util.List<Warehouse> list = service.list(pq, null).getList();

        String[] headers = {"ID", "编码", "名称", "地址", "状态"};
        String[] fieldNames = {"id", "code", "name", "address", "status"};

        byte[] excelBytes = ExcelExportUtils.exportToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=warehouses.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @GetMapping("/actions/export/template")
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        String[] headers = {"编码", "名称", "经销商ID", "类型", "地址", "状态"};
        String[] fieldNames = {"code", "name", "dealerId", "type", "address", "status"};
        String[] examples = {"WH-001", "示例仓库", "1", "main", "北京市朝阳区XX路XX号", "active"};

        byte[] excelBytes = ExcelExportUtils.exportTemplate(headers, fieldNames, examples);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("仓库导入模板.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @PostMapping("/batch-import")
    @OperationLog(businessType = "warehouse", action = OperationAction.CREATE, remark = "仓库-批量导入")
    public ApiResponse<java.util.Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ApiResponse.fail(40001, "请选择要导入的文件");
        }

        java.util.List<java.util.Map<String, Object>> data = ExcelImportUtils.importFromExcel(file.getInputStream(), file.getOriginalFilename());
        if (data.isEmpty()) {
            return ApiResponse.fail(40002, "Excel 文件中没有数据");
        }

        String[] headers = {"编码", "名称", "经销商ID", "类型", "地址", "状态"};
        String[] fieldNames = {"code", "name", "dealerId", "type", "address", "status"};

        int success = 0, failed = 0;
        java.util.List<java.util.Map<String, Object>> errors = new java.util.ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            java.util.Map<String, Object> row = data.get(i);
            try {
                Warehouse entity = new Warehouse();
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

    private void setFieldValue(Warehouse entity, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = Warehouse.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(entity, ExcelImportUtils.coerce(value, field.getType()));
    }
}
