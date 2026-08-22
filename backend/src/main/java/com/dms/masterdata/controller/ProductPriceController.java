/*
 * v4.1.0: 产品价格主数据 Controller
 * 单品价与 BOM 子件价分开维护；BOM 母件保留完整价格记录。
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
import com.dms.common.util.PagingUtil;
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

import java.math.BigDecimal;
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
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String priceScope,
            @RequestParam(required = false) String priceType,
            @RequestParam(required = false) String partnerType,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) String priceContext,
            @RequestParam(required = false) Long bomParentProductId,
            @RequestParam(required = false) Boolean includeComponents,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String validFrom,
            @RequestParam(required = false) String validTo) {
        UUID tid = TenantContext.getTenantId();
        int safePage = PagingUtil.normalizePage(page);
        int safeSize = PagingUtil.normalizeSize(size);
        int offset = (safePage - 1) * safeSize;
        String scope = normalizeScope(priceScope, priceType, partnerType);
        String context = normalizeContext(priceContext);
        boolean includeComponentRows = Boolean.TRUE.equals(includeComponents);

        StringBuilder where = new StringBuilder("WHERE pp.tenant_id = ?1 AND pp.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        params.add(tid);
        int idx = 2;
        if (id != null) { where.append(" AND pp.id = ?").append(idx++); params.add(id); }
        List<Long> productIds = parseLongs(productId);
        if (!productIds.isEmpty()) {
            where.append(" AND pp.product_id IN (");
            for (int i = 0; i < productIds.size(); i++) {
                if (i > 0) where.append(",");
                where.append("?").append(idx++);
                params.add(productIds.get(i));
            }
            where.append(")");
        }
        if (scope != null) { where.append(" AND pp.price_scope = ?").append(idx++); params.add(scope); }
        if (partnerId != null) { where.append(" AND pp.partner_id = ?").append(idx++); params.add(partnerId); }
        if (partnerName != null && !partnerName.isBlank()) {
            where.append(" AND (d.name ILIKE ?").append(idx++).append(" OR s.name ILIKE ?").append(idx++).append(")");
            String kw = "%" + partnerName.trim() + "%"; params.add(kw); params.add(kw);
        }
        if (context != null) { where.append(" AND pp.price_context = ?").append(idx++); params.add(context); }
        if (bomParentProductId != null) { where.append(" AND pp.bom_parent_product_id = ?").append(idx++); params.add(bomParentProductId); }
        if (!includeComponentRows) where.append(" AND pp.price_context <> 'BOM_COMPONENT'");
        if (status != null && !status.isBlank()) { where.append(" AND pp.status = ?").append(idx++); params.add(status); }
        if (productCode != null && !productCode.isBlank()) { where.append(" AND p.code ILIKE ?").append(idx++); params.add("%" + productCode.trim() + "%"); }
        if (validFrom != null && !validFrom.isBlank()) { where.append(" AND pp.valid_from >= ?").append(idx++); params.add(java.sql.Date.valueOf(validFrom)); }
        if (validTo != null && !validTo.isBlank()) { where.append(" AND pp.valid_to <= ?").append(idx++); params.add(java.sql.Date.valueOf(validTo)); }
        if (keyword != null && !keyword.isBlank()) {
            String[] tokens = keyword.trim().split("[\\s,，]+");
            where.append(" AND (");
            for (int t = 0; t < tokens.length; t++) {
                if (tokens[t].isBlank()) continue;
                if (t > 0) where.append(" OR ");
                where.append("(p.code ILIKE ?").append(idx).append(" OR p.name_cn ILIKE ?").append(idx+1).append(" OR p.spec ILIKE ?").append(idx+2).append(")");
                String kw = "%" + tokens[t].trim() + "%"; params.add(kw); params.add(kw); params.add(kw); idx += 3;
            }
            where.append(")");
        }
        String fromSql = "FROM product_prices pp LEFT JOIN products p ON p.id = pp.product_id " +
                "LEFT JOIN products bp ON bp.id = pp.bom_parent_product_id " +
                "LEFT JOIN dealers d ON pp.price_scope = 'SALE' AND d.id = pp.partner_id " +
                "LEFT JOIN suppliers s ON pp.price_scope = 'PURCHASE' AND s.id = pp.partner_id ";
        var cntQ = em.createNativeQuery("SELECT COUNT(*) " + fromSql + where);
        for (int i = 0; i < params.size(); i++) cntQ.setParameter(i + 1, params.get(i));
        long total = ((Number) cntQ.getSingleResult()).longValue();

        String sql = "SELECT pp.id, pp.product_id, pp.partner_type, pp.partner_id, pp.price_scope, pp.price_context, pp.bom_parent_product_id, " +
                "pp.purchase_price, pp.purchase_price_excl_tax, pp.sales_price, pp.sales_price_excl_tax, pp.tax_rate, pp.currency, pp.valid_from, pp.valid_to, pp.status, " +
                "p.code AS product_code, p.name_cn AS product_name, p.unit_type AS product_unit, " +
                "bp.code AS bom_parent_code, bp.name_cn AS bom_parent_name, " +
                "CASE WHEN pp.price_scope = 'SALE' THEN COALESCE(d.name,'') WHEN pp.price_scope = 'PURCHASE' THEN COALESCE(s.name,'') ELSE '' END AS partner_name, " +
                "pp.created_at, pp.updated_at " + fromSql + where +
                " ORDER BY pp.updated_at DESC, pp.id DESC LIMIT ?" + idx + " OFFSET ?" + (idx + 1);
        var q = em.createNativeQuery(sql, Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(idx, safeSize); q.setParameter(idx + 1, offset);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toPriceMap(t, includeComponentRows));
        fillBomHeaderAmounts(list);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total); data.put("page", safePage); data.put("size", safeSize); data.put("list", list);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        String sql = "SELECT pp.id, pp.product_id, pp.partner_type, pp.partner_id, pp.price_scope, pp.price_context, pp.bom_parent_product_id, " +
                "pp.purchase_price, pp.purchase_price_excl_tax, pp.sales_price, pp.sales_price_excl_tax, pp.tax_rate, pp.currency, pp.valid_from, pp.valid_to, pp.status, " +
                "p.code AS product_code, p.name_cn AS product_name, p.unit_type AS product_unit, " +
                "bp.code AS bom_parent_code, bp.name_cn AS bom_parent_name, " +
                "CASE WHEN pp.price_scope = 'SALE' THEN COALESCE(d.name,'') WHEN pp.price_scope = 'PURCHASE' THEN COALESCE(s.name,'') ELSE '' END AS partner_name, " +
                "pp.created_at, pp.updated_at " +
                "FROM product_prices pp LEFT JOIN products p ON p.id = pp.product_id " +
                "LEFT JOIN products bp ON bp.id = pp.bom_parent_product_id " +
                "LEFT JOIN dealers d ON pp.price_scope = 'SALE' AND d.id = pp.partner_id " +
                "LEFT JOIN suppliers s ON pp.price_scope = 'PURCHASE' AND s.id = pp.partner_id " +
                "WHERE pp.id=?1 AND pp.tenant_id=?2 AND pp.deleted_at IS NULL";
        var q = em.createNativeQuery(sql, Tuple.class).setParameter(1, id).setParameter(2, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "价格记录不存在");
        Map<String, Object> result = toPriceMap(rows.get(0), true);
        fillBomHeaderAmounts(List.of(result));
        if ("BOM_HEADER".equals(result.get("priceContext"))) result.put("componentPrices", componentPriceMaps(id));
        return ApiResponse.ok(result);
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long productId = toLong(body.get("productId"));
        String priceType = str(body.getOrDefault("priceType", "SALE"));
        if (productId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "SKU不能为空");
        boolean sale = !"PURCHASE".equalsIgnoreCase(priceType);
        if (sale && toLong(body.get("partnerId")) == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "销售价必须选择经销商");
        var bundleRows = em.createNativeQuery("SELECT id,bom_version FROM product_bundles WHERE tenant_id=?1 AND product_id=?2 AND version_status='active' AND deleted_at IS NULL ORDER BY updated_at DESC,id DESC LIMIT 1")
                .setParameter(1, tid).setParameter(2, productId).getResultList();
        boolean bom = sale && !bundleRows.isEmpty();
        if (!sale && bom) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "采购价只能维护单品，不能维护BOM母件");

        String partnerType = sale ? "DEALER" : "SUPPLIER";
        Long partnerId = sale ? toLong(body.get("partnerId")) : 0L;
        ensureNoActive(tid, productId, sale ? "SALE" : "PURCHASE", partnerType, partnerId, bom ? "BOM_HEADER" : "STANDALONE", null);
        softDeleteInactive(tid, productId, sale ? "SALE" : "PURCHASE", partnerType, partnerId, bom ? "BOM_HEADER" : "STANDALONE", null);

        Long headerId = insertPriceRow(tid, productId, body, sale, BigDecimal.ZERO, BigDecimal.ZERO, "BOM_HEADER", null);
        int created = 1;
        if (bom) {
            Object[] bundle = (Object[]) bundleRows.get(0);
            String version = String.valueOf(bundle[1]);
            Object componentPricesRaw = body.get("componentPrices");
            if (!(componentPricesRaw instanceof List<?> componentPrices) || componentPrices.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "BOM母件销售价必须维护子件价格");
            Set<Long> activeChildren = activeBundleChildIds(tid, productId, version);
            Map<Long, Map<String, Object>> requested = new HashMap<>();
            for (Object item : componentPrices) {
                if (!(item instanceof Map<?,?> row)) continue;
                Long childId = toLong(row.get("productId"));
                if (childId != null) requested.put(childId, castMap(row));
            }
            for (Long childId : activeChildren) {
                Map<String,Object> childBody = requested.containsKey(childId) ? requested.get(childId) : new LinkedHashMap<>();
                childBody.putIfAbsent("productId", childId);
                childBody.putIfAbsent("priceType", "SALE");
                childBody.putIfAbsent("partnerId", partnerId);
                childBody.putIfAbsent("taxRate", firstNonNull(body.get("taxRate"), childBody.get("taxRate"), new BigDecimal("0.13")));
                childBody.putIfAbsent("currency", firstNonNull(body.get("currency"), "CNY"));
                childBody.putIfAbsent("validFrom", body.get("validFrom"));
                childBody.putIfAbsent("validTo", body.get("validTo"));
                childBody.putIfAbsent("status", firstNonNull(body.get("status"), "active"));
                BigDecimal incl = toBd(firstNonNull(childBody.get("inclPrice"), childBody.get("salesPrice"), BigDecimal.ZERO));
                BigDecimal rate = toBd(firstNonNull(childBody.get("taxRate"), body.get("taxRate"), new BigDecimal("0.13")));
                if (incl.signum() < 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "子件含税销售价不能小于0");
                if (rate.signum() < 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "税率不能小于0");
                BigDecimal excl = toBd(firstNonNull(childBody.get("exclPrice"), childBody.get("salesPriceExclTax"), BigDecimal.ZERO));
                if (incl.signum() > 0 && excl.signum() == 0) excl = incl.divide(BigDecimal.ONE.add(rate), 4, java.math.RoundingMode.HALF_UP);
                if (excl.signum() > 0 && incl.signum() == 0) incl = excl.multiply(BigDecimal.ONE.add(rate)).setScale(4, java.math.RoundingMode.HALF_UP);
                ensureNoActive(tid, childId, "SALE", "DEALER", partnerId, "BOM_COMPONENT", productId);
                softDeleteInactive(tid, childId, "SALE", "DEALER", partnerId, "BOM_COMPONENT", productId);
                insertPriceRow(tid, childId, childBody, true, incl, excl, "BOM_COMPONENT", productId);
                created++;
            }
        } else {
            BigDecimal taxRate = toBd(firstNonNull(body.get("taxRate"), new BigDecimal("0.13")));
            BigDecimal incl = toBd(firstNonNull(body.get("inclPrice"), body.get("salesPrice"), BigDecimal.ZERO));
            BigDecimal excl = toBd(firstNonNull(body.get("exclPrice"), body.get("salesPriceExclTax"), BigDecimal.ZERO));
            if (incl.signum() < 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "含税价不能小于0");
            if (incl.signum() == 0 && excl.signum() == 0) throw new BusinessException(ErrorCode.PARAM_MISSING, "含税价不能为空");
            if (incl.signum() > 0 && excl.signum() == 0) excl = incl.divide(BigDecimal.ONE.add(taxRate), 4, java.math.RoundingMode.HALF_UP);
            if (excl.signum() > 0 && incl.signum() == 0) incl = excl.multiply(BigDecimal.ONE.add(taxRate)).setScale(4, java.math.RoundingMode.HALF_UP);
            em.createNativeQuery("UPDATE product_prices SET sales_price=?1, sales_price_excl_tax=?2, tax_rate=?3, updated_at=now() WHERE id=?4 AND tenant_id=?5")
                    .setParameter(1, incl).setParameter(2, excl).setParameter(3, taxRate).setParameter(4, headerId).setParameter(5, tid).executeUpdate();
        }
        opLog.log("product_price", headerId, "CREATE", bom ? "创建BOM销售价" : (sale ? "创建单品销售价" : "创建采购价"));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", headerId); r.put("created", created);
        return ApiResponse.ok(r);
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        String status = str(body.get("status"));
        var mutable = new LinkedHashMap<>(body);
        mutable.remove("status");
        if (!mutable.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "价格记录创建后仅允许变更生效/失效状态，不允许编辑其它字段");
        if (status != null && !status.isBlank()) toggleStatus(id, status);
        return ApiResponse.ok(Map.of("id", id));
    }

    @PostMapping("/{id}/activate")
    @Transactional
    public ApiResponse<Map<String, Object>> activate(@PathVariable Long id) { toggleStatus(id, "active"); return ApiResponse.ok(Map.of("id", id)); }

    @PostMapping("/{id}/deactivate")
    @Transactional
    public ApiResponse<Map<String, Object>> deactivate(@PathVariable Long id) { toggleStatus(id, "inactive"); return ApiResponse.ok(Map.of("id", id)); }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "价格记录不允许删除，请使用失效操作");
    }

    @GetMapping("/actions/export")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ResponseEntity<byte[]> export() throws Exception {
        UUID tid = TenantContext.getTenantId();
        String sql = "SELECT pp.id, pp.product_id, pp.partner_type, pp.partner_id, pp.price_scope, pp.price_context, pp.bom_parent_product_id, " +
                "pp.purchase_price, pp.purchase_price_excl_tax, pp.sales_price, pp.sales_price_excl_tax, pp.tax_rate, pp.currency, pp.valid_from, pp.valid_to, pp.status, " +
                "p.code AS product_code, p.name_cn AS product_name, p.unit_type AS product_unit, bp.code AS bom_parent_code, bp.name_cn AS bom_parent_name, " +
                "CASE WHEN pp.price_scope = 'SALE' THEN COALESCE(d.name,'') WHEN pp.price_scope = 'PURCHASE' THEN COALESCE(s.name,'') ELSE '' END AS partner_name, pp.created_at, pp.updated_at " +
                "FROM product_prices pp LEFT JOIN products p ON p.id=pp.product_id LEFT JOIN products bp ON bp.id=pp.bom_parent_product_id " +
                "LEFT JOIN dealers d ON pp.price_scope='SALE' AND d.id=pp.partner_id LEFT JOIN suppliers s ON pp.price_scope='PURCHASE' AND s.id=pp.partner_id " +
                "WHERE pp.tenant_id=?1 AND pp.deleted_at IS NULL AND pp.price_context <> 'BOM_COMPONENT' ORDER BY pp.updated_at DESC, pp.id DESC LIMIT 5000";
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(sql, Tuple.class).setParameter(1, tid).getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toPriceMap(t, false));
        String[] headers = {"ID","SKU编码","SKU名称","价格类型","价格用途","经销商/供应商","BOM母件","币种","含税价","不含税价","税率","状态"};
        String[] fields = {"id","productCode","productName","priceTypeText","priceContextText","partnerName","bomParentLabel","currency","inclPrice","exclPrice","taxRate","status"};
        byte[] bytes = ExcelExportUtils.exportMapToExcel(list, headers, fields);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("product-prices.xlsx")).contentType(MediaType.APPLICATION_OCTET_STREAM).body(bytes);
    }

    @GetMapping("/actions/export/template")
    public ResponseEntity<byte[]> exportTemplate() throws Exception {
        String[] headers = {"SKU","价格类型","含税价","税率","币种","状态"};
        String[] fields = {"productCode","priceType","inclPrice","taxRate","currency","status"};
        String[] examples = {"PRD-001","SALE","120.00","0.13","CNY","active"};
        byte[] bytes = ExcelExportUtils.exportTemplate(headers, fields, examples);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("产品价格导入模板.xlsx")).contentType(MediaType.APPLICATION_OCTET_STREAM).body(bytes);
    }

    @PostMapping("/batch-import")
    public ApiResponse<Map<String, Object>> batchImportUnsupported() {
        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "产品价格按单品价/BOM价逐笔新建，不支持Excel导入");
    }

    @PostMapping("/batch-import-legacy")
    public ApiResponse<Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "产品价格按单品价/BOM价逐笔新建，不支持Excel导入");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> componentPriceMaps(Long headerId) {
        List<Map<String, Object>> out = new ArrayList<>();
        var rows = em.createNativeQuery(
                "SELECT c.id,c.product_id,c.sales_price,c.sales_price_excl_tax,c.tax_rate,p.code,p.name_cn " +
                "FROM product_prices c JOIN products p ON p.id=c.product_id WHERE c.bom_parent_product_id=(SELECT product_id FROM product_prices WHERE id=?1) " +
                "AND c.tenant_id=(SELECT tenant_id FROM product_prices WHERE id=?1) AND c.price_context='BOM_COMPONENT' AND c.deleted_at IS NULL " +
                "AND c.partner_type='DEALER' AND c.partner_id IS NOT DISTINCT FROM (SELECT partner_id FROM product_prices WHERE id=?1) ORDER BY c.id", Tuple.class)
                .setParameter(1, headerId).getResultList();
        for (Object o : rows) {
            Tuple t = (Tuple) o;
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id")); m.put("productId", t.get("product_id")); m.put("productCode", val(t.get("code"))); m.put("productName", val(t.get("name_cn")));
            m.put("inclPrice", t.get("sales_price")); m.put("exclPrice", t.get("sales_price_excl_tax")); m.put("taxRate", t.get("tax_rate"));
            out.add(m);
        }
        return out;
    }

    private void toggleStatus(Long id, String status) {
        UUID tid = TenantContext.getTenantId();
        int aff = em.createNativeQuery("UPDATE product_prices SET status=?1, updated_at=now() WHERE id=?2 AND tenant_id=?3")
                .setParameter(1, status).setParameter(2, id).setParameter(3, tid).executeUpdate();
        if (aff == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "价格记录不存在");
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT product_id, price_context FROM product_prices WHERE id=?1 AND tenant_id=?2")
                .setParameter(1, id).setParameter(2, tid).getResultList();
        if (!rows.isEmpty()) {
            Object[] header = rows.get(0);
            String context = header[1] == null ? "" : String.valueOf(header[1]);
            if ("BOM_HEADER".equals(context)) {
                Long productId = ((Number) header[0]).longValue();
                em.createNativeQuery("UPDATE product_prices SET status=?1, updated_at=now() WHERE tenant_id=?2 AND bom_parent_product_id=?3 AND price_context='BOM_COMPONENT' AND deleted_at IS NULL")
                        .setParameter(1, status).setParameter(2, tid).setParameter(3, productId).executeUpdate();
            }
        }
        opLog.log("product_price", id, "UPDATE", "价格状态变更为 " + status);
    }

    private Long insertPriceRow(UUID tid, Long productId, Map<String,Object> body, boolean sale, BigDecimal incl, BigDecimal excl, String context, Long bomParentProductId) {
        String scope = sale ? "SALE" : "PURCHASE";
        String partnerType = sale ? "DEALER" : "SUPPLIER";
        Long partnerId = sale ? toLong(body.get("partnerId")) : 0L;
        if (sale && partnerId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "销售价必须选择经销商");
        BigDecimal taxRate = toBd(firstNonNull(body.get("taxRate"), new BigDecimal("0.13")));
        if (incl == null) incl = toBd(firstNonNull(body.get("inclPrice"), sale ? body.get("salesPrice") : body.get("purchasePrice"), BigDecimal.ZERO));
        if (excl == null) excl = toBd(firstNonNull(body.get("exclPrice"), sale ? body.get("salesPriceExclTax") : body.get("purchasePriceExclTax"), BigDecimal.ZERO));
        if (incl.signum() > 0 && excl.signum() == 0) excl = incl.divide(BigDecimal.ONE.add(taxRate), 4, java.math.RoundingMode.HALF_UP);
        if (excl.signum() > 0 && incl.signum() == 0) incl = excl.multiply(BigDecimal.ONE.add(taxRate)).setScale(4, java.math.RoundingMode.HALF_UP);
        var q = em.createNativeQuery(
                "INSERT INTO product_prices (tenant_id,product_id,partner_type,partner_id,purchase_price,purchase_price_excl_tax,sales_price,sales_price_excl_tax,tax_rate,currency,valid_from,valid_to,status,price_scope,price_context,bom_parent_product_id) " +
                "VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,CAST(?11 AS TIMESTAMPTZ),CAST(?12 AS TIMESTAMPTZ),?13,?14,?15,?16) RETURNING id");
        q.setParameter(1,tid).setParameter(2,productId).setParameter(3,partnerType).setParameter(4,partnerId)
                .setParameter(5, sale ? BigDecimal.ZERO : incl).setParameter(6, sale ? BigDecimal.ZERO : excl)
                .setParameter(7, sale ? incl : BigDecimal.ZERO).setParameter(8, sale ? excl : BigDecimal.ZERO)
                .setParameter(9,taxRate).setParameter(10,str(body.getOrDefault("currency","CNY")))
                .setParameter(11,blankToNull(body.get("validFrom"))).setParameter(12,blankToNull(body.get("validTo")))
                .setParameter(13,str(firstNonNull(body.get("status"),"active"))).setParameter(14,scope).setParameter(15,context).setParameter(16,bomParentProductId);
        return ((Number) q.getSingleResult()).longValue();
    }

    private Set<Long> activeBundleChildIds(UUID tid, Long bomProductId, String version) {
        String sql = "SELECT pbl.child_product_id FROM product_bundles pb JOIN product_bundle_lines pbl ON pbl.bundle_id=pb.id " +
                "WHERE pb.tenant_id=?1 AND pb.product_id=?2 AND pb.deleted_at IS NULL AND pbl.deleted_at IS NULL AND pb.version_status='active'";
        if (version != null && !version.isBlank()) sql += " AND pb.bom_version=?3";
        var q = em.createNativeQuery(sql).setParameter(1,tid).setParameter(2,bomProductId);
        if (version != null && !version.isBlank()) q.setParameter(3,version);
        Set<Long> ids = new LinkedHashSet<>();
        for (Object o : q.getResultList()) if (o != null) ids.add(((Number)o).longValue());
        return ids;
    }

    private void ensureNoActive(UUID tid, Long productId, String scope, String partnerType, Long partnerId, String context, Long bomParentProductId) {
        var q = em.createNativeQuery("SELECT id FROM product_prices WHERE tenant_id=?1 AND product_id=?2 AND price_scope=?3 AND partner_type=?4 AND partner_id IS NOT DISTINCT FROM ?5 AND price_context=?6 AND bom_parent_product_id IS NOT DISTINCT FROM ?7 AND deleted_at IS NULL AND status='active' LIMIT 1")
                .setParameter(1,tid).setParameter(2,productId).setParameter(3,scope).setParameter(4,partnerType).setParameter(5,partnerId).setParameter(6,context).setParameter(7,bomParentProductId);
        if (!q.getResultList().isEmpty()) throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "该SKU在此对象下已有有效价格，请将原价格失效后再新建");
    }

    private void softDeleteInactive(UUID tid, Long productId, String scope, String partnerType, Long partnerId, String context, Long bomParentProductId) {
        em.createNativeQuery("UPDATE product_prices SET deleted_at=now(), updated_at=now() WHERE tenant_id=?1 AND product_id=?2 AND price_scope=?3 AND partner_type=?4 AND partner_id IS NOT DISTINCT FROM ?5 AND price_context=?6 AND bom_parent_product_id IS NOT DISTINCT FROM ?7 AND deleted_at IS NULL AND status='inactive'")
                .setParameter(1,tid).setParameter(2,productId).setParameter(3,scope).setParameter(4,partnerType).setParameter(5,partnerId).setParameter(6,context).setParameter(7,bomParentProductId).executeUpdate();
    }

    private void fillBomHeaderAmounts(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            if (!"BOM_HEADER".equals(String.valueOf(row.get("priceContext")))) continue;
            var sums = em.createNativeQuery("SELECT COALESCE(SUM(c.sales_price),0), COALESCE(SUM(c.sales_price_excl_tax),0) FROM product_prices c WHERE c.tenant_id=(SELECT h.tenant_id FROM product_prices h WHERE h.id=?1) AND c.bom_parent_product_id=(SELECT h.product_id FROM product_prices h WHERE h.id=?1) AND c.partner_type='DEALER' AND c.partner_id IS NOT DISTINCT FROM (SELECT h.partner_id FROM product_prices h WHERE h.id=?1) AND c.price_context='BOM_COMPONENT' AND c.deleted_at IS NULL AND c.status='active'")
                    .setParameter(1, row.get("id")).getResultList();
            if (sums.isEmpty()) continue;
            Object[] s = (Object[]) sums.get(0);
            row.put("inclPrice", s[0]); row.put("salesPrice", s[0]); row.put("exclPrice", s[1]); row.put("salesPriceExclTax", s[1]);
        }
    }
    private Map<String, Object> toPriceMap(Tuple t, boolean includeComponentMeta) {
        String sc = String.valueOf(val(t.get("price_scope")));
        boolean sale = "SALE".equals(sc);
        String context = t.get("price_context") == null ? "STANDALONE" : String.valueOf(t.get("price_context"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id"));
        m.put("productId", t.get("product_id"));
        m.put("productCode", val(t.get("product_code")));
        m.put("productName", val(t.get("product_name")));
        m.put("productUnit", val(t.get("product_unit")));
        m.put("priceScope", sc);
        m.put("priceType", sc);
        m.put("priceTypeText", sale ? "销售价" : "采购价");
        m.put("priceContext", context);
        m.put("priceContextText", contextText(context));
        m.put("bomParentProductId", t.get("bom_parent_product_id"));
        m.put("bomParentCode", val(t.get("bom_parent_code")));
        m.put("bomParentName", val(t.get("bom_parent_name")));
        m.put("bomParentLabel", (String.valueOf(val(t.get("bom_parent_code"))) + " " + String.valueOf(val(t.get("bom_parent_name")))).trim());
        m.put("partnerType", sale ? "DEALER" : "SUPPLIER");
        m.put("partnerId", t.get("partner_id"));
        m.put("partnerName", val(t.get("partner_name")));
        m.put("currency", val(t.get("currency")));
        m.put("inclPrice", sale ? t.get("sales_price") : t.get("purchase_price"));
        m.put("exclPrice", sale ? t.get("sales_price_excl_tax") : t.get("purchase_price_excl_tax"));
        m.put("salesPrice", t.get("sales_price"));
        m.put("salesPriceExclTax", t.get("sales_price_excl_tax"));
        m.put("purchasePrice", t.get("purchase_price"));
        m.put("purchasePriceExclTax", t.get("purchase_price_excl_tax"));
        m.put("taxRate", t.get("tax_rate"));
        m.put("validFrom", com.dms.common.util.DateFmt.fmt(t.get("valid_from")));
        m.put("validTo", com.dms.common.util.DateFmt.fmt(t.get("valid_to")));
        m.put("status", t.get("status"));
        m.put("createdAt", com.dms.common.util.DateFmt.fmt(t.get("created_at")));
        m.put("updatedAt", com.dms.common.util.DateFmt.fmt(t.get("updated_at")));
        return m;
    }

    private String contextText(String context) {
        return switch (context) {
            case "BOM_HEADER" -> "BOM母件价";
            case "BOM_COMPONENT" -> "BOM子件价";
            default -> "单品价";
        };
    }

    private static List<Long> parseLongs(String value) {
        List<Long> ids = new ArrayList<>();
        if (value == null) return ids;
        for (String part : value.split(",")) {
            try {
                String v = part == null ? "" : part.trim();
                if (!v.isEmpty()) ids.add(Long.valueOf(v));
            } catch (Exception ignored) { }
        }
        return ids;
    }
    private static String blankToNull(Object o) { return o == null || String.valueOf(o).isBlank() || "null".equals(String.valueOf(o)) ? null : String.valueOf(o); }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Object val(Object o) { return o == null ? "" : o; }
    private static Long toLong(Object o) { if (o == null) return null; try { return Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; } }
    private static BigDecimal toBd(Object o) { if (o == null) return BigDecimal.ZERO; try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return BigDecimal.ZERO; } }
    private static Object firstNonNull(Object... values) { for (Object v : values) if (v != null && !String.valueOf(v).isBlank()) return v; return null; }
    @SuppressWarnings("unchecked") private static Map<String,Object> castMap(Object o) { return o instanceof Map ? new LinkedHashMap<>((Map<String,Object>) o) : new LinkedHashMap<>(); }
    private static String normalizeScope(String priceScope, String priceType, String partnerType) {
        if (priceScope != null && !priceScope.isBlank()) return priceScope.trim().toUpperCase();
        if (priceType != null && !priceType.isBlank()) return priceType.trim().equalsIgnoreCase("PURCHASE") ? "PURCHASE" : "SALE";
        if (partnerType != null && !partnerType.isBlank()) return "DEALER".equalsIgnoreCase(partnerType) ? "SALE" : ("SUPPLIER".equalsIgnoreCase(partnerType) ? "PURCHASE" : partnerType.trim().toUpperCase());
        return null;
    }
    private static String normalizeContext(String context) {
        if (context == null || context.isBlank() || "ALL".equalsIgnoreCase(context)) return null;
        return context.trim().toUpperCase();
    }
}
