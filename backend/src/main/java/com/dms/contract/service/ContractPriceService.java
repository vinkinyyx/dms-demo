package com.dms.contract.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DateFmt;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ExcelImportUtils;
import com.dms.common.util.TenantContext;
import com.dms.contract.entity.Contract;
import com.dms.contract.repository.ContractRepository;
import com.dms.execution.service.AuditLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractPriceService {

    private final EntityManager em;
    private final ContractRepository contractRepository;
    private final AuditLogService opLog;

    @Transactional(readOnly = true)
    public Map<String, Object> list(Long contractId, int page, int size, String status, String productCode) {
        UUID tid = TenantContext.getTenantId();
        Contract contract = getContract(contractId, tid);
        int safePage = Math.max(page, 1);
        int safeSize = size < 1 ? 20 : Math.min(size, 1000);
        int offset = (safePage - 1) * safeSize;

        StringBuilder where = new StringBuilder("WHERE cp.tenant_id=:tid AND cp.deleted_at IS NULL AND cp.contract_id=:contractId");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tid", tid);
        params.put("contractId", contractId);
        if (status != null && !status.isBlank()) { where.append(" AND cp.status=:status"); params.put("status", status); }
        if (productCode != null && !productCode.isBlank()) { where.append(" AND p.code ILIKE :productCode"); params.put("productCode", "%" + productCode.trim() + "%"); }

        var countQ = em.createNativeQuery(
                "SELECT count(1) FROM contract_prices cp LEFT JOIN products p ON p.id=cp.product_id " + where);
        params.forEach(countQ::setParameter);
        Long total = ((Number) countQ.getSingleResult()).longValue();

        var listQ = em.createNativeQuery(
                "SELECT cp.id, cp.contract_id, cp.dealer_id, cp.product_id, cp.price_incl_tax, cp.price_excl_tax, "
                        + "cp.tax_rate, cp.valid_from, cp.valid_to, cp.status, cp.created_at, cp.updated_at, "
                        + "p.code AS product_code, p.name_cn AS product_name, p.spec AS product_spec, p.unit AS product_unit "
                        + "FROM contract_prices cp LEFT JOIN products p ON p.id=cp.product_id "
                        + where + " ORDER BY cp.id DESC LIMIT :limit OFFSET :offset", Tuple.class);
        params.forEach(listQ::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = listQ.setParameter("limit", safeSize).setParameter("offset", offset).getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toMap(t));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("contractId", contractId);
        result.put("dealerId", contract.getDealerId());
        result.put("contractCode", contract.getCode());
        result.put("list", list);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long contractId, Long id) {
        UUID tid = TenantContext.getTenantId();
        getContract(contractId, tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery(baseSelect()
                        + "WHERE cp.tenant_id=:tid AND cp.deleted_at IS NULL AND cp.contract_id=:contractId AND cp.id=:id", Tuple.class)
                .setParameter("tid", tid).setParameter("contractId", contractId).setParameter("id", id)
                .getResultList();
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "合同价记录不存在");
        return toMap(rows.get(0));
    }

    @Transactional
    public Map<String, Object> create(Long contractId, Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Contract contract = getContract(contractId, tid);
        Long productId = toLong(body.get("productId"));
        BigDecimal inclTax = toPrice(body.get("priceInclTax"));
        BigDecimal taxRate = toTaxRate(body.get("taxRate"));
        LocalDate validFrom = toDate(body.get("validFrom"));
        LocalDate validTo = toDate(body.get("validTo"));
        String status = strOr(body.get("status"), "active");
        validateRange(validFrom, validTo);
        assertStatus(status);
        assertProduct(tid, productId);
        assertProductUnique(tid, contractId, productId, null);

        BigDecimal exclTax = inclTax.divide(BigDecimal.ONE.add(taxRate), 4, RoundingMode.HALF_UP);
        em.createNativeQuery("INSERT INTO contract_prices "
                        + "(tenant_id, contract_id, dealer_id, product_id, price_incl_tax, price_excl_tax, tax_rate, "
                        + "valid_from, valid_to, status, created_at, updated_at) "
                        + "VALUES (:tid,:contractId,:dealerId,:productId,:inclTax,:exclTax,:taxRate,:validFrom,:validTo,:status, now(), now())")
                .setParameter("tid", tid)
                .setParameter("contractId", contractId)
                .setParameter("dealerId", contract.getDealerId())
                .setParameter("productId", productId)
                .setParameter("inclTax", inclTax)
                .setParameter("exclTax", exclTax)
                .setParameter("taxRate", taxRate)
                .setParameter("validFrom", sqlDate(validFrom))
                .setParameter("validTo", sqlDate(validTo))
                .setParameter("status", status)
                .executeUpdate();
        Number newId = (Number) em.createNativeQuery(
                "SELECT currval(pg_get_serial_sequence('contract_prices','id'))").getSingleResult();
        opLog.log("contract_price", newId.longValue(), "CREATE",
                "合同[" + contract.getCode() + "]新增合同价 productId=" + productId + " 含税价=" + inclTax);
        return detail(contractId, newId.longValue());
    }

    @Transactional
    public Map<String, Object> batchSave(Long contractId, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "价格清单不能为空");
        }
        UUID tid = TenantContext.getTenantId();
        Contract contract = getContract(contractId, tid);
        List<String> errors = new ArrayList<>();
        int success = 0;
        List<Long> touchedIds = new ArrayList<>();
        List<Long> touchedProductIds = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            try {
                Long id = toLong(row.get("id"));
                Long productId = toLong(row.get("productId"));
                if (id == null) {
                    Map<String, Object> created = create(contractId, row);
                    touchedIds.add(toLong(created.get("id")));
                    touchedProductIds.add(productId);
                } else {
                    Map<String, Object> updated = update(contractId, id, row);
                    touchedIds.add(id);
                    touchedProductIds.add(productId != null ? productId : toLong(updated.get("productId")));
                }
                success++;
            } catch (BusinessException e) {
                errors.add("第" + (i + 1) + "行：" + e.getMessage());
            }
        }
        if (!touchedProductIds.isEmpty()) {
            assertBatchNoDuplicate(tid, contractId, touchedIds);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("success", success);
        result.put("failed", errors.size());
        result.put("errors", errors);
        result.put("contractId", contractId);
        opLog.log("contract", contractId, "UPDATE", "合同[" + contract.getCode() + "]批量保存价格清单 成功" + success + "条 失败" + errors.size() + "条");
        return result;
    }

    @Transactional
    public Map<String, Object> update(Long contractId, Long id, Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        getContract(contractId, tid);
        Object[] existing = loadRow(tid, contractId, id);
        Long productId = body.get("productId") != null ? toLong(body.get("productId")) : (Long) existing[1];
        BigDecimal inclTax = body.get("priceInclTax") != null ? toPrice(body.get("priceInclTax")) : (BigDecimal) existing[2];
        BigDecimal taxRate = body.get("taxRate") != null ? toTaxRate(body.get("taxRate")) : (BigDecimal) existing[3];
        LocalDate validFrom = body.containsKey("validFrom") ? toDate(body.get("validFrom")) : (LocalDate) existing[4];
        LocalDate validTo = body.containsKey("validTo") ? toDate(body.get("validTo")) : (LocalDate) existing[5];
        String status = body.get("status") != null ? str(body.get("status")) : String.valueOf(existing[6]);
        validateRange(validFrom, validTo);
        assertStatus(status);
        assertProduct(tid, productId);
        assertProductUnique(tid, contractId, productId, id);

        BigDecimal exclTax = inclTax.divide(BigDecimal.ONE.add(taxRate), 4, RoundingMode.HALF_UP);
        em.createNativeQuery("UPDATE contract_prices SET product_id=:productId, price_incl_tax=:inclTax, "
                        + "price_excl_tax=:exclTax, tax_rate=:taxRate, valid_from=:validFrom, valid_to=:validTo, "
                        + "status=:status, updated_at=now() "
                        + "WHERE id=:id AND tenant_id=:tid AND contract_id=:contractId AND deleted_at IS NULL")
                .setParameter("productId", productId)
                .setParameter("inclTax", inclTax)
                .setParameter("exclTax", exclTax)
                .setParameter("taxRate", taxRate)
                .setParameter("validFrom", sqlDate(validFrom))
                .setParameter("validTo", sqlDate(validTo))
                .setParameter("status", status)
                .setParameter("id", id)
                .setParameter("tid", tid)
                .setParameter("contractId", contractId)
                .executeUpdate();
        opLog.log("contract_price", id, "UPDATE", "合同价 id=" + id + " 更新 productId=" + productId + " 含税价=" + inclTax);
        return detail(contractId, id);
    }

    @Transactional
    public void delete(Long contractId, Long id) {
        UUID tid = TenantContext.getTenantId();
        getContract(contractId, tid);
        loadRow(tid, contractId, id);
        em.createNativeQuery("UPDATE contract_prices SET deleted_at=now(), updated_at=now() WHERE id=:id AND tenant_id=:tid AND contract_id=:contractId")
                .setParameter("id", id).setParameter("tid", tid).setParameter("contractId", contractId).executeUpdate();
        opLog.log("contract_price", id, "DELETE", "删除合同价 id=" + id);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(Long contractId) throws java.io.IOException {
        Map<String, Object> result = list(contractId, 1, 10000, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("list");
        String[] headers = {"ID", "产品编码", "产品名称", "规格", "单位", "含税单价", "不含税单价", "税率", "生效日期", "失效日期", "状态"};
        String[] fields = {"id", "productCode", "productName", "productSpec", "productUnit", "priceInclTax", "priceExclTax", "taxRate", "validFrom", "validTo", "status"};
        byte[] bytes = ExcelExportUtils.exportMapToExcel(data, headers, fields);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDispositionFormData("attachment", java.net.URLEncoder.encode(
                "contract-" + contractId + "-prices.xlsx", java.nio.charset.StandardCharsets.UTF_8));
        return new ResponseEntity<>(bytes, h, org.springframework.http.HttpStatus.OK);
    }
public ResponseEntity<byte[]> exportTemplate() throws java.io.IOException {
        String[] headers = {"产品编码", "含税单价", "税率", "生效日期", "失效日期", "状态"};
        String[] fields = {"productCode", "priceInclTax", "taxRate", "validFrom", "validTo", "status"};
        String[] examples = {"PRD-001", "100.00", "0.13", "2026-09-01", "2026-12-31", "active"};
        byte[] bytes = ExcelExportUtils.exportTemplate(headers, fields, examples);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDispositionFormData("attachment", "contract-prices-template.xlsx");
        return new ResponseEntity<>(bytes, h, org.springframework.http.HttpStatus.OK);
    }

    @Transactional
    public Map<String, Object> importExcel(Long contractId, MultipartFile file) throws Exception {
        UUID tid = TenantContext.getTenantId();
        Contract contract = getContract(contractId, tid);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "请选择要导入的文件");
        }
        List<Map<String, Object>> data = ExcelImportUtils.importFromExcel(file.getInputStream(), file.getOriginalFilename());
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> raw = data.get(i);
            try {
                String productCode = str(raw.get("产品编码") != null ? raw.get("产品编码") : raw.get("productCode"));
                if (productCode == null || productCode.isBlank()) {
                    throw new BusinessException(ErrorCode.PARAM_MISSING, "产品编码不能为空");
                }
                Long productId = findProductIdByCode(tid, productCode.trim());
                if (productId == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "产品编码不存在: " + productCode);
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("productId", productId);
                row.put("priceInclTax", raw.get("含税单价") != null ? raw.get("含税单价") : raw.get("priceInclTax"));
                Object taxRate = raw.get("税率") != null ? raw.get("税率") : raw.get("taxRate");
                row.put("taxRate", taxRate == null || String.valueOf(taxRate).isBlank() ? new BigDecimal("0.13") : taxRate);
                row.put("validFrom", raw.get("生效日期") != null ? raw.get("生效日期") : raw.get("validFrom"));
                row.put("validTo", raw.get("失效日期") != null ? raw.get("失效日期") : raw.get("validTo"));
                row.put("status", raw.get("状态") != null ? raw.get("状态") : raw.get("status"));
                rows.add(row);
            } catch (BusinessException e) {
                errors.add("第" + (i + 2) + "行：" + e.getMessage());
            }
        }
        Map<String, Object> saveResult = rows.isEmpty()
                ? Map.of("total", 0, "success", 0, "failed", 0, "errors", errors, "contractId", contractId)
                : batchSave(contractId, rows);
        @SuppressWarnings("unchecked")
        List<String> saveErrors = new ArrayList<>((List<String>) saveResult.getOrDefault("errors", List.of()));
        saveErrors.addAll(errors);
        Map<String, Object> result = new LinkedHashMap<>(saveResult);
        result.put("failed", (Integer) saveResult.get("failed") + errors.size());
        result.put("errors", saveErrors);
        opLog.log("contract", contractId, "UPDATE", "合同[" + contract.getCode() + "]导入合同价 共" + data.size() + "行");
        return result;
    }

    private String baseSelect() {
        return "SELECT cp.id, cp.contract_id, cp.dealer_id, cp.product_id, cp.price_incl_tax, cp.price_excl_tax, "
                + "cp.tax_rate, cp.valid_from, cp.valid_to, cp.status, cp.created_at, cp.updated_at, "
                + "p.code AS product_code, p.name_cn AS product_name, p.spec AS product_spec, p.unit AS product_unit "
                + "FROM contract_prices cp LEFT JOIN products p ON p.id=cp.product_id ";
    }

    private Contract getContract(Long contractId, UUID tid) {
        if (contractId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "contractId 不能为空");
        }
        return contractRepository.findById(contractId)
                .filter(c -> tid == null || tid.equals(c.getTenantId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "合同不存在"));
    }

    private Object[] loadRow(UUID tid, Long contractId, Long id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT id, product_id, price_incl_tax, tax_rate, valid_from, valid_to, status FROM contract_prices "
                        + "WHERE tenant_id=:tid AND contract_id=:contractId AND id=:id AND deleted_at IS NULL")
                .setParameter("tid", tid).setParameter("contractId", contractId).setParameter("id", id)
                .getResultList();
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "合同价记录不存在");
        Object[] row = rows.get(0);
        LocalDate from = row[4] == null ? null : ((java.sql.Date) row[4]).toLocalDate();
        LocalDate to = row[5] == null ? null : ((java.sql.Date) row[5]).toLocalDate();
        return new Object[]{row[0], row[1], row[2], row[3], from, to, row[6]};
    }

    private void assertProduct(UUID tid, Long productId) {
        if (productId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "产品不能为空");
        long cnt = ((Number) em.createNativeQuery(
                "SELECT count(1) FROM products WHERE id=:id AND tenant_id=:tid AND deleted_at IS NULL")
                .setParameter("id", productId).setParameter("tid", tid).getSingleResult()).longValue();
        if (cnt == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "产品不存在: id=" + productId);
    }

    private Long findProductIdByCode(UUID tid, String code) {
        @SuppressWarnings("unchecked")
        List<Number> ids = em.createNativeQuery(
                "SELECT id FROM products WHERE code=:code AND tenant_id=:tid AND deleted_at IS NULL LIMIT 1")
                .setParameter("code", code).setParameter("tid", tid).getResultList();
        return ids.isEmpty() ? null : ids.get(0).longValue();
    }

    @SuppressWarnings("unchecked")
    private void assertProductUnique(UUID tid, Long contractId, Long productId, Long excludeId) {
        var q = em.createNativeQuery(
                "SELECT id FROM contract_prices WHERE tenant_id=:tid AND contract_id=:contractId "
                        + "AND product_id=:productId AND deleted_at IS NULL AND (:excludeId IS NULL OR id <> :excludeId)")
                .setParameter("tid", tid).setParameter("contractId", contractId)
                .setParameter("productId", productId)
                .setParameter("excludeId", excludeId);
        List<Number> ids = q.getResultList();
        if (!ids.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "该合同已存在此产品的价格记录（记录ID=" + ids.get(0) + "），请直接编辑");
        }
    }

    @SuppressWarnings("unchecked")
    private void assertBatchNoDuplicate(UUID tid, Long contractId, List<Long> excludeIds) {
        List<Long> productIds = em.createNativeQuery(
                "SELECT product_id FROM contract_prices WHERE tenant_id=:tid AND contract_id=:contractId AND deleted_at IS NULL")
                .setParameter("tid", tid).setParameter("contractId", contractId).getResultList();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Object pid : productIds) {
            Long p = ((Number) pid).longValue();
            if (!seen.add(p)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "合同内产品必须唯一，检测到重复产品 ID=" + p);
            }
        }
    }

    private Map<String, Object> toMap(Tuple t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id"));
        m.put("contractId", t.get("contract_id"));
        m.put("dealerId", t.get("dealer_id"));
        m.put("productId", t.get("product_id"));
        m.put("productCode", val(t.get("product_code")));
        m.put("productName", val(t.get("product_name")));
        m.put("productSpec", val(t.get("product_spec")));
        m.put("productUnit", val(t.get("product_unit")));
        m.put("priceInclTax", t.get("price_incl_tax"));
        m.put("priceExclTax", t.get("price_excl_tax"));
        m.put("taxRate", t.get("tax_rate"));
        m.put("validFrom", DateFmt.fmt(t.get("valid_from")));
        m.put("validTo", DateFmt.fmt(t.get("valid_to")));
        m.put("status", t.get("status"));
        m.put("createdAt", DateFmt.fmt(t.get("created_at")));
        m.put("updatedAt", DateFmt.fmt(t.get("updated_at")));
        return m;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "生效日期不能晚于失效日期");
        }
    }

    private void assertStatus(String status) {
        if (!"active".equals(status) && !"inactive".equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "状态只能为 active/inactive");
        }
    }

    private BigDecimal toPrice(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "含税单价不能为空");
        }
        BigDecimal price;
        try {
            price = new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "价格格式不正确：" + o);
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "价格不能为负数");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toTaxRate(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return new BigDecimal("0.13");
        BigDecimal rate;
        try {
            rate = new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "税率格式不正确：" + o);
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "税率必须在 0 ~ 1 之间");
        }
        return rate;
    }

    private static LocalDate toDate(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        String datePart = s.length() >= 10 ? s.substring(0, 10) : s;
        try {
            return LocalDate.parse(datePart);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "日期格式不正确，应为 YYYY-MM-DD：" + o);
        }
    }

    private static java.sql.Date sqlDate(LocalDate d) {
        return d == null ? null : java.sql.Date.valueOf(d);
    }

    private static Long toLong(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try { return Long.valueOf(String.valueOf(o).trim()); }
        catch (NumberFormatException e) { throw new BusinessException(ErrorCode.PARAM_INVALID, "ID 格式不正确：" + o); }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String strOr(Object o, String def) {
        return o == null || String.valueOf(o).isBlank() ? def : String.valueOf(o).trim();
    }

    private static String val(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
