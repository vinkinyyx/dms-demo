/*
 * 商品 REST 控制器。
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
import java.util.LinkedHashMap;
import com.dms.masterdata.entity.Product;
import com.dms.masterdata.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService service;

    @GetMapping
    public ApiResponse<PageResult<Product>> list(@Valid PageQuery pageQuery,
                                                  @RequestParam(required = false) java.util.Map<String, String> allParams) {
        return ApiResponse.ok(service.list(pageQuery, allParams));
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @OperationLog(businessType = "product", action = OperationAction.CREATE, remark = "产品管理-创建")
    public ApiResponse<Product> create(@RequestBody Product request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "product", action = OperationAction.UPDATE, remark = "产品管理-更新")
    public ApiResponse<Product> update(@PathVariable Long id, @RequestBody Product request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @OperationLog(businessType = "product", action = OperationAction.DELETE, remark = "产品管理-删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        PageQuery pq = new PageQuery();
        pq.setPage(1);
        pq.setSize(10000);
        java.util.List<Product> list = service.list(pq, null).getList();

        String[] headers = {"ID", "编码", "名称", "类型", "分类", "规格", "单位", "成本价", "售价", "状态"};
        String[] fieldNames = {"id", "code", "name", "productType", "categoryId", "spec", "unit", "costPrice", "salePrice", "status"};

        byte[] excelBytes = ExcelExportUtils.exportToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("产品列表.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @GetMapping("/actions/export/template")
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        String[] headers = {"编码", "中文名称", "英文名称", "产品类型", "分类ID", "规格型号", "单位", "参考单价", "税率", "UDI追溯", "状态"};
        String[] fieldNames = {"code", "nameCn", "nameEn", "productType", "categoryId", "spec", "unit", "currentPrice", "taxRate", "udiRequired", "status"};
        String[] examples = {"PROD-001", "示例产品", "Sample Product", "MEDICAL", "1", "100mg/片", "盒", "100.00", "0.13", "true", "active"};

        byte[] excelBytes = ExcelExportUtils.exportTemplate(headers, fieldNames, examples);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("产品导入模板.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @PostMapping("/batch-import")
    @OperationLog(businessType = "product", action = OperationAction.CREATE, remark = "产品管理-批量导入")
    public ApiResponse<java.util.Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ApiResponse.fail(40001, "请选择要导入的文件");
        }

        java.util.List<java.util.Map<String, Object>> data = ExcelImportUtils.importFromExcel(file.getInputStream(), file.getOriginalFilename());
        if (data.isEmpty()) {
            return ApiResponse.fail(40002, "Excel 文件中没有数据");
        }

        String[] headers = {"编码", "中文名称", "英文名称", "产品类型", "分类ID", "规格型号", "单位", "参考单价", "税率", "UDI追溯", "状态"};
        String[] fieldNames = {"code", "nameCn", "nameEn", "productType", "categoryId", "spec", "unit", "currentPrice", "taxRate", "udiRequired", "status"};

        int success = 0, failed = 0;
        java.util.List<java.util.Map<String, Object>> errors = new java.util.ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            java.util.Map<String, Object> row = data.get(i);
            try {
                Product entity = new Product();
                for (int j = 0; j < headers.length; j++) {
                    Object value = row.get(headers[j]);
                    if (value != null) {
                        setFieldValue(entity, fieldNames[j], value);
                    }
                }
                if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
                    throw new IllegalArgumentException("编码不能为空");
                }
                if (entity.getNameCn() == null || entity.getNameCn().trim().isEmpty()) {
                    throw new IllegalArgumentException("中文名称不能为空");
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

    private void setFieldValue(Product entity, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = Product.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Class<?> type = field.getType();
        if (type == String.class) {
            field.set(entity, String.valueOf(value));
        } else if (type == Long.class || type == long.class) {
            field.set(entity, ((Number) value).longValue());
        } else if (type == Double.class || type == double.class) {
            field.set(entity, ((Number) value).doubleValue());
        } else if (type == Boolean.class || type == boolean.class) {
            field.set(entity, "true".equals(String.valueOf(value).toLowerCase()) || "是".equals(value));
        } else {
            field.set(entity, value);
        }
    }
}
