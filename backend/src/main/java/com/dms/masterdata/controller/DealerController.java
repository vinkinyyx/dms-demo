package com.dms.masterdata.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.ContentDispositionUtils;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.entity.DealerAddress;
import com.dms.masterdata.entity.DealerContact;
import com.dms.masterdata.service.DealerAddressService;
import com.dms.masterdata.service.DealerContactService;
import com.dms.masterdata.service.DealerService;
import com.dms.report.service.DealerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dealers")
@RequiredArgsConstructor
@Validated
public class DealerController {

    private final DealerService service;
    private final DealerProfileService dealerProfileService;
    private final DealerContactService contactService;
    private final DealerAddressService addressService;

    @GetMapping
    public ApiResponse<PageResult<Dealer>> list(@Valid PageQuery pageQuery,
                                                @RequestParam(required = false) Map<String, String> allParams) {
        return ApiResponse.ok(service.list(pageQuery, allParams));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(@RequestParam Long dealerId) {
        return ApiResponse.ok(dealerProfileService.getBasic(dealerId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Dealer> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("@perm.hasAny('dealer:view','dealer:search')")
    public ApiResponse<Map<String, Object>> getDetail(@PathVariable Long id) {
        Dealer dealer = service.get(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dealer", dealer);
        data.put("contacts", contactService.listByDealer(id));
        data.put("addresses", addressService.listByDealer(id));
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}/contacts")
    @PreAuthorize("@perm.hasAny('dealer_contact:view','dealer:view')")
    public ApiResponse<List<DealerContact>> contacts(@PathVariable Long id) {
        service.get(id);
        return ApiResponse.ok(contactService.listByDealer(id));
    }

    @GetMapping("/{id}/addresses")
    @PreAuthorize("@perm.hasAny('dealer_address:view','dealer:view')")
    public ApiResponse<List<DealerAddress>> addresses(@PathVariable Long id) {
        service.get(id);
        return ApiResponse.ok(addressService.listByDealer(id));
    }

    @PostMapping
    @OperationLog(businessType = "dealer", action = OperationAction.CREATE, remark = "Create dealer")
    public ApiResponse<Dealer> create(@RequestBody Dealer request) {
        validateDealer(request);
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @OperationLog(businessType = "dealer", action = OperationAction.UPDATE, remark = "Update dealer")
    public ApiResponse<Dealer> update(@PathVariable Long id, @RequestBody Dealer request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @OperationLog(businessType = "dealer", action = OperationAction.DELETE, remark = "Delete dealer")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(1);
        pageQuery.setSize(10000);
        java.util.List<Dealer> list = service.list(pageQuery, null).getList();

        String[] headers = {"ID", "Code", "Name", "Contact", "Phone", "Address", "Status"};
        String[] fieldNames = {"id", "code", "name", "contactName", "contactPhone", "regAddress", "status"};
        byte[] excelBytes = ExcelExportUtils.exportToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dealers.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @GetMapping("/actions/export/template")
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        String[] headers = {"Code", "Name", "Level", "Contact", "Phone", "Status"};
        String[] fieldNames = {"code", "name", "level", "contactName", "contactPhone", "status"};
        String[] examples = {"DLR-001", "Sample Dealer", "T1", "Alice", "13800138000", "active"};
        byte[] excelBytes = ExcelExportUtils.exportTemplate(headers, fieldNames, examples);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("dealers-template.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @PostMapping("/batch-import")
    @OperationLog(businessType = "dealer", action = OperationAction.CREATE, remark = "Batch import dealers")
    public ApiResponse<Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail(40001, "Please select a file to import");
        }

        java.util.List<Map<String, Object>> data = ExcelImportUtils.importFromExcel(file.getInputStream(), file.getOriginalFilename());
        if (data.isEmpty()) {
            return ApiResponse.fail(40002, "No data found in Excel file");
        }

        String[] headers = {"Code", "Name", "Level", "Contact", "Phone", "Status"};
        String[] fieldNames = {"code", "name", "level", "contactName", "contactPhone", "status"};

        int success = 0;
        int failed = 0;
        java.util.List<Map<String, Object>> errors = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> row = data.get(i);
            try {
                Dealer entity = new Dealer();
                for (int j = 0; j < headers.length; j++) {
                    Object value = row.get(headers[j]);
                    if (value != null) {
                        setFieldValue(entity, fieldNames[j], value);
                    }
                }
                if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
                    throw new IllegalArgumentException("Code cannot be empty");
                }
                service.upsertByCode(entity);
                success++;
            } catch (Exception e) {
                failed++;
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("row", i + 2);
                error.put("error", e.getMessage());
                errors.add(error);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", data.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        return ApiResponse.ok(result);
    }

    private void validateDealer(Dealer dealer) {
        if (dealer == null || dealer.getCode() == null || dealer.getCode().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "Dealer code cannot be empty");
        }
        if (dealer.getName() == null || dealer.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "Dealer name cannot be empty");
        }
    }

    private void setFieldValue(Dealer entity, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = Dealer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(entity, ExcelImportUtils.coerce(value, field.getType()));
    }
}
