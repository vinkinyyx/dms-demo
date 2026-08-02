/*
 * v3.4.9: 产品价格主数据 Controller
 * 支持多维度价格：GLOBAL / DEALER / SUPPLIER
 */
package com.dms.masterdata.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.ContentDispositionUtils;
import org.springframework.web.multipart.MultipartFile;
import com.dms.common.util.TenantContext;
import com.dms.execution.service.AuditLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/product-prices")
@RequiredArgsConstructor
public class ProductPriceController {

    private final EntityManager em;
    private final AuditLogService opLog;

    @GetMapping
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String partnerType) {
        UUID tid = TenantContext.getTenantId();
        int offset = (page - 1) * size;

        StringBuilder where = new StringBuilder("WHERE pp.tenant_id = ?1");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (id != null) { where.append(" AND pp.id = ?").append(idx++); params.add(id); }
        if (productId != null) { where.append(" AND pp.product_id = ?").append(idx++); params.add(productId); }
        if (partnerType != null && !partnerType.isBlank()) {
            where.append(" AND pp.partner_type = ?").append(idx++); params.add(partnerType);
        }

        var cntQ = em.createNativeQuery("SELECT COUNT(*) FROM product_prices pp " + where);
        for (int i = 0; i < params.size(); i++) cntQ.setParameter(i + 1, params.get(i));
        long total = ((Number) cntQ.getSingleResult()).longValue();

        String sql = "SELECT pp.id, pp.product_id, pp.partner_type, pp.partner_id, " +
                "pp.purchase_price, pp.sales_price, pp.currency, pp.effective_date, pp.expire_date, pp.status, " +
                "p.code AS product_code, p.name_cn AS product_name, p.unit_type AS product_unit, " +
                "CASE WHEN pp.partner_type = 'DEALER' THEN d.name WHEN pp.partner_type = 'SUPPLIER' THEN s.name " +
                "WHEN pp.partner_type = 'GLOBAL' THEN '全局' ELSE NULL END AS partner_name, " +
                "pp.created_at, pp.updated_at " +
                "FROM product_prices pp " +
                "LEFT JOIN products p ON p.id = pp.product_id " +
                "LEFT JOIN dealers d ON pp.partner_type = 'DEALER' AND d.id = pp.partner_id " +
                "LEFT JOIN suppliers s ON pp.partner_type = 'SUPPLIER' AND s.id = pp.partner_id " +
                where + " ORDER BY pp.product_id, pp.partner_type LIMIT ?" + idx + " OFFSET ?" + (idx + 1);
        var q = em.createNativeQuery(sql, Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(idx, size);
        q.setParameter(idx + 1, offset);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("productId", t.get("product_id"));
            m.put("productCode", val(t.get("product_code")));
            m.put("productName", val(t.get("product_name")));
            m.put("productUnit", val(t.get("product_unit")));
            m.put("partnerType", t.get("partner_type"));
            m.put("partnerId", t.get("partner_id"));
            m.put("partnerName", val(t.get("partner_name")));
            m.put("purchasePrice", t.get("purchase_price"));
            m.put("salesPrice", t.get("sales_price"));
            m.put("currency", t.get("currency"));
            m.put("effectiveDate", com.dms.common.util.DateFmt.fmt(t.get("effective_date")));
            m.put("expireDate", com.dms.common.util.DateFmt.fmt(t.get("expire_date")));
            m.put("status", t.get("status"));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total); data.put("page", page); data.put("size", size); data.put("list", list);
        return ApiResponse.ok(data);
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long productId = toLong(body.get("productId"));
        String partnerType = str(body.getOrDefault("partnerType", "GLOBAL"));
        Long partnerId = toLong(body.get("partnerId"));
        if (partnerId == null) partnerId = 0L;
        if (productId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "productId 必填");

        var q = em.createNativeQuery(
                "INSERT INTO product_prices (tenant_id, product_id, partner_type, partner_id, purchase_price, sales_price, currency, effective_date, expire_date, status) " +
                "VALUES (?1,?2,?3,?4,?5,?6,?7,CAST(?8 AS DATE),CAST(?9 AS DATE),?10) " +
                "ON CONFLICT (tenant_id, product_id, partner_type, partner_id) " +
                "DO UPDATE SET purchase_price = EXCLUDED.purchase_price, sales_price = EXCLUDED.sales_price, updated_at = now() " +
                "RETURNING id");
        q.setParameter(1, tid).setParameter(2, productId).setParameter(3, partnerType).setParameter(4, partnerId)
         .setParameter(5, toBd(body.get("purchasePrice")))
         .setParameter(6, toBd(body.get("salesPrice")))
         .setParameter(7, str(body.getOrDefault("currency", "CNY")))
         .setParameter(8, blankToNull(body.get("effectiveDate")))
         .setParameter(9, blankToNull(body.get("expireDate")))
         .setParameter(10, str(body.getOrDefault("status", "active")));
        Long id = ((Number) q.getSingleResult()).longValue();
        opLog.log("product_price", id, "CREATE", "创建产品价格 product=" + productId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        return ApiResponse.ok(r);
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        // v3.4.12: 补齐 currency/effective_date/expire_date 字段；价格用 COALESCE 避免误清零
        int aff = em.createNativeQuery(
                "UPDATE product_prices SET purchase_price = COALESCE(?1, purchase_price), " +
                "sales_price = COALESCE(?2, sales_price), status = COALESCE(?3, status), " +
                "currency = COALESCE(?4, currency), " +
                "effective_date = COALESCE(CAST(?5 AS DATE), effective_date), " +
                "expire_date = COALESCE(CAST(?6 AS DATE), expire_date), updated_at = now() " +
                "WHERE id = ?7 AND tenant_id = ?8")
                .setParameter(1, toBdOrNull(body.get("purchasePrice")))
                .setParameter(2, toBdOrNull(body.get("salesPrice")))
                .setParameter(3, str(body.get("status")))
                .setParameter(4, str(body.get("currency")))
                .setParameter(5, blankToNull(body.get("effectiveDate")))
                .setParameter(6, blankToNull(body.get("expireDate")))
                .setParameter(7, id).setParameter(8, tid).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "价格记录不存在");
        opLog.log("product_price", id, "UPDATE", "编辑产品价格");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        return ApiResponse.ok(r);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        int aff = em.createNativeQuery("DELETE FROM product_prices WHERE id = ?1 AND tenant_id = ?2")
                .setParameter(1, id).setParameter(2, tid).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "价格记录不存在");
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ResponseEntity<byte[]> export() throws Exception {
        UUID tid = TenantContext.getTenantId();
        String sql = "SELECT pp.id, pp.product_id, pp.partner_type, pp.partner_id, " +
                "pp.purchase_price, pp.sales_price, pp.currency, pp.effective_date, pp.expire_date, pp.status, " +
                "p.code AS product_code, p.name_cn AS product_name, p.unit_type AS product_unit, " +
                "CASE WHEN pp.partner_type = 'DEALER' THEN d.name WHEN pp.partner_type = 'SUPPLIER' THEN s.name " +
                "WHEN pp.partner_type = 'GLOBAL' THEN '全局' ELSE NULL END AS partner_name, " +
                "pp.created_at, pp.updated_at " +
                "FROM product_prices pp " +
                "LEFT JOIN products p ON p.id = pp.product_id " +
                "LEFT JOIN dealers d ON pp.partner_type = 'DEALER' AND d.id = pp.partner_id " +
                "LEFT JOIN suppliers s ON pp.partner_type = 'SUPPLIER' AND s.id = pp.partner_id " +
                "WHERE pp.tenant_id = ?1 ORDER BY pp.product_id, pp.partner_type";
        var q = em.createNativeQuery(sql, Tuple.class);
        q.setParameter(1, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("productId", t.get("product_id"));
            m.put("productCode", val(t.get("product_code")));
            m.put("productName", val(t.get("product_name")));
            m.put("productUnit", val(t.get("product_unit")));
            m.put("partnerType", t.get("partner_type"));
            m.put("partnerId", t.get("partner_id"));
            m.put("partnerName", val(t.get("partner_name")));
            m.put("purchasePrice", t.get("purchase_price"));
            m.put("salesPrice", t.get("sales_price"));
            m.put("currency", t.get("currency"));
            m.put("effectiveDate", com.dms.common.util.DateFmt.fmt(t.get("effective_date")));
            m.put("expireDate", com.dms.common.util.DateFmt.fmt(t.get("expire_date")));
            m.put("status", t.get("status"));
            m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }

        String[] headers = {"ID", "产品ID", "产品编码", "产品名称", "产品单位", "合作方类型", "合作方ID", "合作方名称", "采购价", "销售价", "币种", "生效日期", "失效日期", "状态", "创建时间", "更新时间"};
        String[] fieldNames = {"id", "productId", "productCode", "productName", "productUnit", "partnerType", "partnerId", "partnerName", "purchasePrice", "salesPrice", "currency", "effectiveDate", "expireDate", "status", "createdAt", "updatedAt"};

        byte[] excelBytes = ExcelExportUtils.exportMapToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=product-prices.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @GetMapping("/actions/export/template")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        String[] headers = {"产品ID", "合作方类型", "合作方ID", "采购价", "销售价", "币种", "状态"};
        String[] fieldNames = {"productId", "partnerType", "partnerId", "purchasePrice", "salesPrice", "currency", "status"};
        String[] examples = {"1", "GLOBAL", "0", "100.00", "120.00", "CNY", "active"};

        byte[] excelBytes = ExcelExportUtils.exportTemplate(headers, fieldNames, examples);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("产品价格导入模板.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    private static String blankToNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return (s.isBlank() || "null".equals(s)) ? null : s;
    }
    private static java.math.BigDecimal toBdOrNull(Object o) {
        if (o == null) return null;
        try { return new java.math.BigDecimal(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    @PostMapping("/batch-import")
    @Transactional
    public ApiResponse<java.util.Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ApiResponse.fail(40001, "请选择要导入的文件");
        }

        java.util.List<java.util.Map<String, Object>> data = ExcelImportUtils.importFromExcel(file.getInputStream(), file.getOriginalFilename());
        if (data.isEmpty()) {
            return ApiResponse.fail(40002, "Excel 文件中没有数据");
        }

        int success = 0, failed = 0;
        java.util.List<java.util.Map<String, Object>> errors = new java.util.ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            java.util.Map<String, Object> row = data.get(i);
            try {
                Long productId = toLong(row.get("产品ID"));
                String partnerType = str(row.get("合作方类型"));
                Long partnerId = toLong(row.get("合作方ID"));
                java.math.BigDecimal purchasePrice = toBd(row.get("采购价"));
                java.math.BigDecimal salesPrice = toBd(row.get("销售价"));
                String currency = str(row.get("币种"));
                String status = str(row.get("状态"));

                if (productId == null) {
                    throw new IllegalArgumentException("产品ID不能为空");
                }
                if (partnerType == null || partnerType.trim().isEmpty()) {
                    throw new IllegalArgumentException("合作方类型不能为空");
                }

                UUID tid = TenantContext.getTenantId();
                Long partnerIdVal = partnerId == null ? 0L : partnerId;
                String currencyVal = currency != null ? currency : "CNY";
                String statusVal = status != null ? status : "active";
                Object existing = em.createNativeQuery(
                        "SELECT id FROM product_prices WHERE tenant_id=?1 AND product_id=?2 AND partner_type=?3 AND partner_id=?4")
                        .setParameter(1, tid).setParameter(2, productId)
                        .setParameter(3, partnerType).setParameter(4, partnerIdVal)
                        .getResultList().stream().findFirst().orElse(null);
                if (existing != null) {
                    em.createNativeQuery("UPDATE product_prices SET purchase_price=?1, sales_price=?2, currency=?3, status=?4, updated_at=now() WHERE id=?5")
                            .setParameter(1, purchasePrice).setParameter(2, salesPrice)
                            .setParameter(3, currencyVal).setParameter(4, statusVal)
                            .setParameter(5, ((Number) existing).longValue())
                            .executeUpdate();
                } else {
                    em.createNativeQuery("INSERT INTO product_prices (product_id, partner_type, partner_id, purchase_price, sales_price, currency, status, tenant_id) " +
                            "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)")
                            .setParameter(1, productId).setParameter(2, partnerType).setParameter(3, partnerIdVal)
                            .setParameter(4, purchasePrice).setParameter(5, salesPrice)
                            .setParameter(6, currencyVal).setParameter(7, statusVal).setParameter(8, tid)
                            .executeUpdate();
                }
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

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Object val(Object o) { return o == null ? "" : o; }
    private static Long toLong(Object o) { if (o == null) return null; try { return Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; } }
    private static java.math.BigDecimal toBd(Object o) {
        if (o == null) return java.math.BigDecimal.ZERO;
        try { return new java.math.BigDecimal(String.valueOf(o)); } catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }
}
