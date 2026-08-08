/*
 * 产品对码服务：厂家租户前台使用。
 * 强制：当前租户类型为厂家；经销商租户 owner_manufacturer_id = currentTenantId；
 * 厂家产品属于当前租户；经销商产品属于所选经销商租户。
 */
package com.dms.platform.mapping.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.TenantContext;
import com.dms.config.MinioStorageService;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.entity.Product;
import com.dms.masterdata.repository.DealerRepository;
import com.dms.masterdata.repository.ProductRepository;
import com.dms.platform.mapping.dto.DealerTenantSimpleDTO;
import com.dms.platform.mapping.dto.MappingImportError;
import com.dms.platform.mapping.dto.MappingImportPreviewResponse;
import com.dms.platform.mapping.dto.MappingImportRow;
import com.dms.platform.mapping.dto.ProductMappingCreateRequest;
import com.dms.platform.mapping.dto.ProductMappingDTO;
import com.dms.platform.mapping.dto.ProductMappingUpdateRequest;
import com.dms.platform.mapping.entity.ProductMapping;
import com.dms.platform.mapping.entity.ProductMappingImportBatch;
import com.dms.platform.mapping.repository.ProductMappingImportBatchRepository;
import com.dms.platform.mapping.repository.ProductMappingRepository;
import com.dms.tenant.entity.Tenant;
import com.dms.tenant.entity.TenantDealerBinding;
import com.dms.tenant.repository.TenantDealerBindingRepository;
import com.dms.tenant.repository.TenantRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMappingService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_DISABLED = "disabled";
    private static final String TYPE_MANUFACTURER = "MANUFACTURER";
    private static final String BATCH_PREVIEW = "preview";
    private static final String BATCH_CONFIRMED = "confirmed";

    private final ProductMappingRepository mappingRepository;
    private final ProductMappingImportBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final TenantDealerBindingRepository bindingRepository;
    private final DealerRepository dealerRepository;
    private final MinioStorageService minioStorage;

    @Transactional(readOnly = true)
    public PageResult<ProductMappingDTO> list(PageQuery pageQuery, UUID dealerTenantId,
                                              String keyword, String status) {
        UUID manufacturerId = requireManufacturerTenant();
        Specification<ProductMapping> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("manufacturerTenantId"), manufacturerId));
            if (dealerTenantId != null) {
                validateDealerTenant(manufacturerId, dealerTenantId);
                ps.add(cb.equal(root.get("dealerTenantId"), dealerTenantId));
            }
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                ps.add(cb.or(
                        cb.like(root.get("manufacturerProductCode"), like),
                        cb.like(root.get("dealerProductCode"), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<ProductMapping> page = mappingRepository.findAll(spec, pageQuery.toPageable());
        return PageResult.of(page.map(this::toDTO));
    }

    @Transactional(readOnly = true)
    public ProductMappingDTO get(Long id) {
        UUID manufacturerId = requireManufacturerTenant();
        ProductMapping mapping = mappingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "对码记录不存在"));
        if (!manufacturerId.equals(mapping.getManufacturerTenantId())) {
            throw new BusinessException(ErrorCode.INVALID_MANUFACTURER_SCOPE);
        }
        return toDTO(mapping);
    }

    @Transactional
    public ProductMappingDTO create(ProductMappingCreateRequest request) {
        UUID manufacturerId = requireManufacturerTenant();
        validateDealerTenant(manufacturerId, request.getDealerTenantId());

        Product manufacturerProduct = loadManufacturerProduct(manufacturerId, request.getManufacturerProductId());
        Product dealerProduct = loadDealerProduct(request.getDealerTenantId(), request.getDealerProductId());

        if (mappingRepository.existsByManufacturerTenantIdAndManufacturerProductId(
                manufacturerId, request.getManufacturerProductId())) {
            throw new BusinessException(ErrorCode.PRODUCT_MAPPING_CONFLICT, "该厂家产品已存在对码");
        }
        if (mappingRepository.existsByManufacturerTenantIdAndDealerProductId(
                manufacturerId, request.getDealerProductId())) {
            throw new BusinessException(ErrorCode.PRODUCT_MAPPING_CONFLICT, "该经销商产品已被对码");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ProductMapping mapping = ProductMapping.builder()
                .manufacturerTenantId(manufacturerId)
                .dealerTenantId(request.getDealerTenantId())
                .manufacturerProductId(manufacturerProduct.getId())
                .dealerProductId(dealerProduct.getId())
                .manufacturerProductCode(manufacturerProduct.getCode())
                .dealerProductCode(dealerProduct.getCode())
                .packageUnit(request.getPackageUnit())
                .conversionRate(request.getConversionRate() == null ? BigDecimal.ONE : request.getConversionRate())
                .status(STATUS_ACTIVE)
                .remark(request.getRemark())
                .createdBy(TenantContext.getUserId())
                .updatedBy(TenantContext.getUserId())
                .updatedAt(now)
                .build();
        mapping = mappingRepository.save(mapping);
        log.info("厂家 {} 新增产品对码: manufacturerProduct={}, dealerProduct={}",
                manufacturerId, manufacturerProduct.getId(), dealerProduct.getId());
        return toDTO(mapping);
    }

    @Transactional
    public ProductMappingDTO update(Long id, ProductMappingUpdateRequest request) {
        UUID manufacturerId = requireManufacturerTenant();
        ProductMapping mapping = mappingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "对码记录不存在"));
        if (!manufacturerId.equals(mapping.getManufacturerTenantId())) {
            throw new BusinessException(ErrorCode.INVALID_MANUFACTURER_SCOPE);
        }
        if (request.getPackageUnit() != null) mapping.setPackageUnit(request.getPackageUnit());
        if (request.getConversionRate() != null) mapping.setConversionRate(request.getConversionRate());
        if (request.getRemark() != null) mapping.setRemark(request.getRemark());
        mapping.setUpdatedBy(TenantContext.getUserId());
        mapping.setUpdatedAt(OffsetDateTime.now());
        return toDTO(mappingRepository.save(mapping));
    }

    @Transactional
    public void setStatus(Long id, boolean active) {
        UUID manufacturerId = requireManufacturerTenant();
        ProductMapping mapping = mappingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "对码记录不存在"));
        if (!manufacturerId.equals(mapping.getManufacturerTenantId())) {
            throw new BusinessException(ErrorCode.INVALID_MANUFACTURER_SCOPE);
        }
        mapping.setStatus(active ? STATUS_ACTIVE : STATUS_DISABLED);
        mapping.setUpdatedBy(TenantContext.getUserId());
        mapping.setUpdatedAt(OffsetDateTime.now());
        mappingRepository.save(mapping);
    }

    @Transactional(readOnly = true)
    public List<DealerTenantSimpleDTO> myDealerTenants() {
        UUID manufacturerId = requireManufacturerTenant();
        List<TenantDealerBinding> bindings = bindingRepository.findByManufacturerTenantId(manufacturerId);
        List<DealerTenantSimpleDTO> result = new ArrayList<>();
        for (TenantDealerBinding b : bindings) {
            Tenant tenant = tenantRepository.findById(b.getDealerTenantId()).orElse(null);
            if (tenant == null) {
                continue;
            }
            String dealerName = dealerRepository.findById(b.getDealerId())
                    .map(Dealer::getName).orElse(null);
            result.add(DealerTenantSimpleDTO.builder()
                    .tenantId(tenant.getId())
                    .code(tenant.getCode())
                    .name(tenant.getName())
                    .dealerId(b.getDealerId())
                    .dealerName(dealerName)
                    .status(tenant.getStatus())
                    .build());
        }
        return result;
    }
    // ==================== Excel import ====================

    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("product-mapping");
            Row header = sheet.createRow(0);
            String[] cols = {"dealerCode","manufacturerProductCode","dealerProductCode","packageUnit","conversionRate"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
                sheet.setColumnWidth(i, 20 * 256);
            }
            Row ex = sheet.createRow(1);
            ex.createCell(0).setCellValue("DEALER_A");
            ex.createCell(1).setCellValue("MFR-PROD-001");
            ex.createCell(2).setCellValue("DEALER-PROD-001");
            ex.createCell(3).setCellValue("box");
            ex.createCell(4).setCellValue("1");
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("template build failed: " + e.getMessage(), e);
        }
    }
    @Transactional
    public MappingImportPreviewResponse importPreview(MultipartFile file) {
        UUID manufacturerId = requireManufacturerTenant();
        List<MappingImportRow> rows;
        try {
            rows = parseRows(file.getInputStream(), file.getOriginalFilename());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "parse excel failed: " + e.getMessage());
        }
        List<MappingImportError> errors = new ArrayList<>();
        for (MappingImportRow row : rows) {
            validateRow(manufacturerId, row, errors);
        }
        String objectKey = buildObjectKey(manufacturerId, "input") + ".xlsx";
        try {
            minioStorage.put(objectKey, file.getBytes(),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception e) {
            log.warn("upload import file to minio failed: {}", e.getMessage());
        }
        ProductMappingImportBatch batch = ProductMappingImportBatch.builder()
                .manufacturerTenantId(manufacturerId)
                .fileName(file.getOriginalFilename())
                .objectKey(objectKey)
                .totalCount(rows.size())
                .successCount(rows.size() - errors.size())
                .failCount(errors.size())
                .status(BATCH_PREVIEW)
                .createdBy(TenantContext.getUserId())
                .build();
        batch = batchRepository.save(batch);
        return MappingImportPreviewResponse.builder()
                .batchId(batch.getId())
                .batchNo(batch.getId().toString())
                .totalCount(rows.size())
                .validCount(rows.size() - errors.size())
                .errorCount(errors.size())
                .rows(rows)
                .errors(errors)
                .build();
    }

    @Transactional
    public MappingImportPreviewResponse confirmImport(Long batchId) {
        UUID manufacturerId = requireManufacturerTenant();
        ProductMappingImportBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "import batch not found"));
        if (!manufacturerId.equals(batch.getManufacturerTenantId())) {
            throw new BusinessException(ErrorCode.INVALID_MANUFACTURER_SCOPE);
        }
        if (!BATCH_PREVIEW.equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "batch already confirmed");
        }
        List<MappingImportRow> rows;
        try (InputStream in = minioStorage.get(batch.getObjectKey())) {
            rows = parseRows(in, batch.getFileName());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read import file failed: " + e.getMessage());
        }
        List<ProductMapping> toSave = new ArrayList<>();
        List<MappingImportError> errors = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (MappingImportRow row : rows) {
            try {
                ProductMapping mapping = buildMappingFromRow(manufacturerId, row, now);
                if (mapping != null) {
                    toSave.add(mapping);
                }
            } catch (BusinessException be) {
                errors.add(MappingImportError.builder()
                        .rowNumber(row.getRowNumber()).field("row").message(be.getMessage()).build());
            }
        }
        int saved = 0;
        for (ProductMapping m : toSave) {
            try {
                mappingRepository.save(m);
                saved++;
            } catch (Exception e) {
                errors.add(MappingImportError.builder().rowNumber(-1).field("persist").message(e.getMessage()).build());
            }
        }
        String errorKey = null;
        if (!errors.isEmpty()) {
            byte[] report = buildErrorWorkbook(errors);
            errorKey = buildObjectKey(manufacturerId, "errors") + "-" + batch.getId() + ".xlsx";
            try {
                minioStorage.put(errorKey, report,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            } catch (Exception e) {
                log.warn("upload error report failed: {}", e.getMessage());
            }
        }
        batch.setStatus(BATCH_CONFIRMED);
        batch.setSuccessCount(saved);
        batch.setFailCount(errors.size());
        batch.setErrorObjectKey(errorKey);
        batch.setFinishedAt(OffsetDateTime.now());
        batchRepository.save(batch);
        return MappingImportPreviewResponse.builder()
                .batchId(batch.getId())
                .batchNo(batch.getId().toString())
                .totalCount(toSave.size() + errors.size())
                .validCount(saved)
                .errorCount(errors.size())
                .errors(errors)
                .build();
    }

    public byte[] errorReport(Long batchId) {
        UUID manufacturerId = requireManufacturerTenant();
        ProductMappingImportBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "import batch not found"));
        if (!manufacturerId.equals(batch.getManufacturerTenantId())) {
            throw new BusinessException(ErrorCode.INVALID_MANUFACTURER_SCOPE);
        }
        if (batch.getErrorObjectKey() != null) {
            try (InputStream in = minioStorage.get(batch.getErrorObjectKey());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            } catch (Exception e) {
                log.warn("read cached error report failed, regenerate: {}", e.getMessage());
            }
        }
        List<MappingImportError> errors = new ArrayList<>();
        try (InputStream in = minioStorage.get(batch.getObjectKey())) {
            List<MappingImportRow> rows = parseRows(in, batch.getFileName());
            for (MappingImportRow row : rows) {
                validateRow(manufacturerId, row, errors);
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read import file failed: " + e.getMessage());
        }
        return buildErrorWorkbook(errors);
    }
    private void validateDealerTenant(UUID manufacturerId, UUID dealerTenantId) {
        Tenant dealer = tenantRepository.findById(dealerTenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND, "dealer tenant not found"));
        if (!"DEALER".equals(dealer.getTenantType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "target tenant is not a dealer");
        }
        if (!manufacturerId.equals(dealer.getOwnerManufacturerId())) {
            throw new BusinessException(ErrorCode.INVALID_MANUFACTURER_SCOPE, "dealer tenant does not belong to current manufacturer");
        }
    }

    private Product loadManufacturerProduct(UUID manufacturerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "manufacturer product not found"));
        if (!manufacturerId.equals(product.getTenantId())) {
            throw new BusinessException(ErrorCode.INVALID_MANUFACTURER_SCOPE, "product does not belong to current tenant");
        }
        return product;
    }

    private Product loadDealerProduct(UUID dealerTenantId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "dealer product not found"));
        if (!dealerTenantId.equals(product.getTenantId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "product does not belong to dealer tenant");
        }
        return product;
    }

    private void validateRow(UUID manufacturerId, MappingImportRow row, List<MappingImportError> errors) {
        if (isBlank(row.getDealerCode())) {
            errors.add(error(row, "dealerCode", "dealer code required"));
            return;
        }
        Tenant dealerTenant = tenantRepository.findByCode(row.getDealerCode()).orElse(null);
        if (dealerTenant == null || !"DEALER".equals(dealerTenant.getTenantType())) {
            errors.add(error(row, "dealerCode", "dealer tenant code not found"));
            return;
        }
        if (!manufacturerId.equals(dealerTenant.getOwnerManufacturerId())) {
            errors.add(error(row, "dealerCode", "dealer does not belong to current manufacturer"));
            return;
        }
        if (isBlank(row.getManufacturerProductCode())) {
            errors.add(error(row, "manufacturerProductCode", "manufacturer product code required"));
        } else if (productRepository.findByTenantIdAndCode(manufacturerId, row.getManufacturerProductCode()).isEmpty()) {
            errors.add(error(row, "manufacturerProductCode", "manufacturer product code not found"));
        }
        if (isBlank(row.getDealerProductCode())) {
            errors.add(error(row, "dealerProductCode", "dealer product code required"));
        } else if (productRepository.findByTenantIdAndCode(dealerTenant.getId(), row.getDealerProductCode()).isEmpty()) {
            errors.add(error(row, "dealerProductCode", "dealer product code not found"));
        }
    }

    private ProductMapping buildMappingFromRow(UUID manufacturerId, MappingImportRow row, OffsetDateTime now) {
        Tenant dealerTenant = tenantRepository.findByCode(row.getDealerCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND, "dealer tenant not found"));
        validateDealerTenant(manufacturerId, dealerTenant.getId());
        Product mfrProduct = productRepository.findByTenantIdAndCode(manufacturerId, row.getManufacturerProductCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "manufacturer product not found"));
        Product dealerProduct = productRepository.findByTenantIdAndCode(dealerTenant.getId(), row.getDealerProductCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "dealer product not found"));
        if (mappingRepository.existsByManufacturerTenantIdAndManufacturerProductId(manufacturerId, mfrProduct.getId())
                || mappingRepository.existsByManufacturerTenantIdAndDealerProductId(manufacturerId, dealerProduct.getId())) {
            throw new BusinessException(ErrorCode.PRODUCT_MAPPING_CONFLICT, "mapping already exists");
        }
        BigDecimal rate = BigDecimal.ONE;
        if (!isBlank(row.getConversionRate())) {
            try {
                rate = new BigDecimal(row.getConversionRate().trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "invalid conversion rate");
            }
        }
        return ProductMapping.builder()
                .manufacturerTenantId(manufacturerId)
                .dealerTenantId(dealerTenant.getId())
                .manufacturerProductId(mfrProduct.getId())
                .dealerProductId(dealerProduct.getId())
                .manufacturerProductCode(mfrProduct.getCode())
                .dealerProductCode(dealerProduct.getCode())
                .packageUnit(row.getPackageUnit())
                .conversionRate(rate)
                .status(STATUS_ACTIVE)
                .createdBy(TenantContext.getUserId())
                .updatedBy(TenantContext.getUserId())
                .updatedAt(now)
                .build();
    }

    private MappingImportError error(MappingImportRow row, String field, String message) {
        return MappingImportError.builder().rowNumber(row.getRowNumber()).field(field).message(message).build();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String buildObjectKey(UUID manufacturerId, String kind) {
        return "product-mapping-imports/" + manufacturerId + "/" + kind + "/" + System.currentTimeMillis();
    }

    private List<MappingImportRow> parseRows(InputStream inputStream, String fileName) throws Exception {
        List<Map<String, Object>> data = ExcelImportUtils.importFromExcel(inputStream, fileName);
        List<MappingImportRow> rows = new ArrayList<>();
        int rowNum = 1;
        for (Map<String, Object> d : data) {
            rowNum++;
            rows.add(MappingImportRow.builder()
                    .rowNumber(rowNum)
                    .dealerCode(asString(d, "dealerCode", "经销商编码"))
                    .manufacturerProductCode(asString(d, "manufacturerProductCode", "厂家产品编码"))
                    .dealerProductCode(asString(d, "dealerProductCode", "经销商产品编码"))
                    .packageUnit(asString(d, "packageUnit", "包装单位"))
                    .conversionRate(asString(d, "conversionRate", "换算率"))
                    .build());
        }
        return rows;
    }

    private String asString(Map<String, Object> data, String enKey, String cnKey) {
        Object v = data.get(enKey);
        if (v == null) v = data.get(cnKey);
        return v == null ? null : v.toString().trim();
    }

    private byte[] buildErrorWorkbook(List<MappingImportError> errors) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("errors");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("row");
            header.createCell(1).setCellValue("field");
            header.createCell(2).setCellValue("message");
            int r = 1;
            for (MappingImportError e : errors) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.getRowNumber());
                row.createCell(1).setCellValue(e.getField() == null ? "" : e.getField());
                row.createCell(2).setCellValue(e.getMessage() == null ? "" : e.getMessage());
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("build error report failed: " + e.getMessage(), e);
        }
    }

    private UUID requireManufacturerTenant() {
        if (!TenantContext.AUTH_SOURCE_TENANT.equals(TenantContext.getAuthSource())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "product mapping is for manufacturer tenants");
        }
        if (!TYPE_MANUFACTURER.equals(TenantContext.getTenantType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only manufacturer tenant can use product mapping");
        }
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return tenantId;
    }

    private ProductMappingDTO toDTO(ProductMapping m) {
        String mfrName = productRepository.findById(m.getManufacturerProductId()).map(Product::getNameCn).orElse(null);
        String dealerName = productRepository.findById(m.getDealerProductId()).map(Product::getNameCn).orElse(null);
        String dealerTenantName = tenantRepository.findById(m.getDealerTenantId()).map(Tenant::getName).orElse(null);
        return ProductMappingDTO.builder()
                .id(m.getId())
                .manufacturerTenantId(m.getManufacturerTenantId())
                .dealerTenantId(m.getDealerTenantId())
                .dealerTenantName(dealerTenantName)
                .manufacturerProductId(m.getManufacturerProductId())
                .dealerProductId(m.getDealerProductId())
                .manufacturerProductCode(m.getManufacturerProductCode())
                .dealerProductCode(m.getDealerProductCode())
                .manufacturerProductName(mfrName)
                .dealerProductName(dealerName)
                .packageUnit(m.getPackageUnit())
                .conversionRate(m.getConversionRate())
                .status(m.getStatus())
                .remark(m.getRemark())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}