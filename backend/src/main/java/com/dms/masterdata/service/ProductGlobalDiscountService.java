package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DateFmt;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.PagingUtil;
import com.dms.common.util.TenantContext;
import com.dms.execution.service.AuditLogService;
import com.dms.masterdata.entity.ProductGlobalDiscount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductGlobalDiscountService {

    private final EntityManager em;
    private final AuditLogService opLog;

    @Transactional(readOnly = true)
    public Map<String, Object> list(int page, int size, Long productId, String productCode,
                                    String status, String validFrom, String validTo) {
        UUID tid = TenantContext.getTenantId();
        int safePage = PagingUtil.normalizePage(page);
        int safeSize = PagingUtil.normalizeSize(size);
        int offset = (safePage - 1) * safeSize;

        StringBuilder where = new StringBuilder("WHERE d.tenant_id = :tid AND d.deleted_at IS NULL");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tid", tid);
        if (productId != null) { where.append(" AND d.product_id = :productId"); params.put("productId", productId); }
        if (productCode != null && !productCode.isBlank()) {
            where.append(" AND p.code ILIKE :productCode"); params.put("productCode", "%" + productCode.trim() + "%");
        }
        if (status != null && !status.isBlank()) { where.append(" AND d.status = :status"); params.put("status", status); }
        if (validFrom != null && !validFrom.isBlank()) { where.append(" AND d.valid_from >= :validFrom"); params.put("validFrom", java.sql.Date.valueOf(validFrom)); }
        if (validTo != null && !validTo.isBlank()) { where.append(" AND d.valid_to <= :validTo"); params.put("validTo", java.sql.Date.valueOf(validTo)); }

        var countQ = em.createNativeQuery(
                "SELECT count(1) FROM product_global_discounts d LEFT JOIN products p ON p.id = d.product_id " + where);
        params.forEach(countQ::setParameter);
        Long total = ((Number) countQ.getSingleResult()).longValue();

        var listQ = em.createNativeQuery(
                "SELECT d.id, d.product_id, d.discount_rate, d.valid_from, d.valid_to, d.status, d.remark, "
                        + "d.created_at, d.updated_at, p.code AS product_code, p.name_cn AS product_name, p.unit AS product_unit "
                        + "FROM product_global_discounts d LEFT JOIN products p ON p.id = d.product_id "
                        + where + " ORDER BY d.id DESC LIMIT :limit OFFSET :offset", Tuple.class);
        params.forEach(listQ::setParameter);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = listQ.setParameter("limit", safeSize).setParameter("offset", offset).getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) list.add(toMap(t));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("list", list);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT d.id, d.product_id, d.discount_rate, d.valid_from, d.valid_to, d.status, d.remark, "
                        + "d.created_at, d.updated_at, p.code AS product_code, p.name_cn AS product_name, p.unit AS product_unit "
                        + "FROM product_global_discounts d LEFT JOIN products p ON p.id = d.product_id "
                        + "WHERE d.tenant_id = :tid AND d.deleted_at IS NULL AND d.id = :id", Tuple.class)
                .setParameter("tid", tid).setParameter("id", id);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "产品全局折扣不存在");
        return toMap(rows.get(0));
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        Long productId = toLong(body.get("productId"));
        BigDecimal rate = DiscountSupport.toRate(body.get("discountRate"), "折扣率");
        LocalDate validFrom = DiscountSupport.toDate(body.get("validFrom"));
        LocalDate validTo = DiscountSupport.toDate(body.get("validTo"));
        String status = DiscountSupport.strOr(body.get("status"), "active");
        String remark = DiscountSupport.str(body.get("remark"));
        DiscountSupport.validateRange(validFrom, validTo);
        DiscountSupport.assertStatus(status);
        assertProduct(tid, productId);
        assertNoOverlap(tid, productId, null, validFrom, validTo);

        em.createNativeQuery("INSERT INTO product_global_discounts "
                        + "(tenant_id, product_id, discount_rate, valid_from, valid_to, status, remark, created_at, updated_at) "
                        + "VALUES (:tid,:productId,:rate,:validFrom,:validTo,:status,:remark, now(), now())")
                .setParameter("tid", tid)
                .setParameter("productId", productId)
                .setParameter("rate", rate)
                .setParameter("validFrom", DiscountSupport.sqlDate(validFrom))
                .setParameter("validTo", DiscountSupport.sqlDate(validTo))
                .setParameter("status", status)
                .setParameter("remark", remark)
                .executeUpdate();
        Number newId = (Number) em.createNativeQuery(
                "SELECT currval(pg_get_serial_sequence('product_global_discounts','id'))").getSingleResult();
        opLog.log("product_global_discount", newId.longValue(), "CREATE", "新建产品全局折扣 productId=" + productId + " 折扣率=" + rate);
        return detail(newId.longValue());
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        ProductGlobalDiscount existing = loadEntity(tid, id);
        Long productId = body.get("productId") != null ? toLong(body.get("productId")) : existing.getProductId();
        BigDecimal rate = body.get("discountRate") != null ? DiscountSupport.toRate(body.get("discountRate"), "折扣率") : existing.getDiscountRate();
        LocalDate validFrom = body.containsKey("validFrom") ? DiscountSupport.toDate(body.get("validFrom")) : existing.getValidFrom();
        LocalDate validTo = body.containsKey("validTo") ? DiscountSupport.toDate(body.get("validTo")) : existing.getValidTo();
        String status = body.get("status") != null ? DiscountSupport.str(body.get("status")) : existing.getStatus();
        String remark = body.containsKey("remark") ? DiscountSupport.str(body.get("remark")) : existing.getRemark();
        DiscountSupport.validateRange(validFrom, validTo);
        DiscountSupport.assertStatus(status);
        assertProduct(tid, productId);
        assertNoOverlap(tid, productId, id, validFrom, validTo);

        em.createNativeQuery("UPDATE product_global_discounts SET product_id=:productId, discount_rate=:rate, "
                        + "valid_from=:validFrom, valid_to=:validTo, status=:status, remark=:remark, updated_at=now() "
                        + "WHERE id=:id AND tenant_id=:tid AND deleted_at IS NULL")
                .setParameter("productId", productId)
                .setParameter("rate", rate)
                .setParameter("validFrom", DiscountSupport.sqlDate(validFrom))
                .setParameter("validTo", DiscountSupport.sqlDate(validTo))
                .setParameter("status", status)
                .setParameter("remark", remark)
                .setParameter("id", id)
                .setParameter("tid", tid)
                .executeUpdate();
        opLog.log("product_global_discount", id, "UPDATE", "编辑产品全局折扣 id=" + id);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        UUID tid = TenantContext.getTenantId();
        loadEntity(tid, id);
        em.createNativeQuery("UPDATE product_global_discounts SET deleted_at=now(), updated_at=now() WHERE id=:id AND tenant_id=:tid")
                .setParameter("id", id).setParameter("tid", tid).executeUpdate();
        opLog.log("product_global_discount", id, "DELETE", "删除产品全局折扣 id=" + id);
    }

    @Transactional
    public Map<String, Object> setStatus(Long id, String status) {
        UUID tid = TenantContext.getTenantId();
        ProductGlobalDiscount existing = loadEntity(tid, id);
        DiscountSupport.assertStatus(status);
        if ("active".equals(status)) {
            assertNoOverlap(tid, existing.getProductId(), id, existing.getValidFrom(), existing.getValidTo());
        }
        em.createNativeQuery("UPDATE product_global_discounts SET status=:status, updated_at=now() WHERE id=:id AND tenant_id=:tid")
                .setParameter("status", status).setParameter("id", id).setParameter("tid", tid).executeUpdate();
        opLog.log("product_global_discount", id, "UPDATE", ("active".equals(status) ? "启用" : "停用") + "产品全局折扣 id=" + id);
        return detail(id);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(Long productId, String status) throws java.io.IOException {
        Map<String, Object> result = list(1, 10000, productId, null, status, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("list");
        String[] headers = {"ID", "产品编码", "产品名称", "单位", "折扣率", "生效日期", "失效日期", "状态", "备注"};
        String[] fields = {"id", "productCode", "productName", "productUnit", "discountRateText", "validFrom", "validTo", "status", "remark"};
        byte[] bytes = ExcelExportUtils.exportMapToExcel(data, headers, fields);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDispositionFormData("attachment", java.net.URLEncoder.encode(
                "product-global-discounts.xlsx", java.nio.charset.StandardCharsets.UTF_8));
        return new ResponseEntity<>(bytes, h, org.springframework.http.HttpStatus.OK);
    }

    private void assertProduct(UUID tid, Long productId) {
        if (productId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "产品不能为空");
        long cnt = ((Number) em.createNativeQuery(
                "SELECT count(1) FROM products WHERE id=:id AND tenant_id=:tid AND deleted_at IS NULL")
                .setParameter("id", productId).setParameter("tid", tid).getSingleResult()).longValue();
        if (cnt == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "产品不存在: id=" + productId);
    }

    @SuppressWarnings("unchecked")
    private void assertNoOverlap(UUID tid, Long productId, Long excludeId, LocalDate validFrom, LocalDate validTo) {
        StringBuilder sql = new StringBuilder(
                "SELECT d.id, d.valid_from, d.valid_to, d.discount_rate, p.code AS product_code, p.name_cn AS product_name "
                        + "FROM product_global_discounts d LEFT JOIN products p ON p.id = d.product_id "
                        + "WHERE d.tenant_id=:tid AND d.deleted_at IS NULL AND d.status='active' AND d.product_id=:productId");
        if (excludeId != null) sql.append(" AND d.id <> :excludeId");
        if (validFrom != null) sql.append(" AND (d.valid_to IS NULL OR d.valid_to >= :validFrom)");
        if (validTo != null) sql.append(" AND (d.valid_from IS NULL OR d.valid_from <= :validTo)");
        var query = em.createNativeQuery(sql.toString(), Tuple.class)
                .setParameter("tid", tid).setParameter("productId", productId);
        if (excludeId != null) query.setParameter("excludeId", excludeId);
        if (validFrom != null) query.setParameter("validFrom", java.sql.Date.valueOf(validFrom));
        if (validTo != null) query.setParameter("validTo", java.sql.Date.valueOf(validTo));
        List<Tuple> conflicts = query.getResultList();
        if (!conflicts.isEmpty()) {
            List<String> desc = new ArrayList<>();
            for (Tuple t : conflicts) {
                desc.add(String.format("记录#%s（%s %s，%s ~ %s，折扣率 %s）",
                        t.get("id"), DiscountSupport.val(t.get("product_code")), DiscountSupport.val(t.get("product_name")),
                        DateFmt.fmt(t.get("valid_from")), DateFmt.fmt(t.get("valid_to")), t.get("discount_rate")));
            }
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                    "同一产品的生效时间段不可重叠，冲突记录：" + String.join("；", desc));
        }
    }

    private ProductGlobalDiscount loadEntity(UUID tid, Long id) {
        return em.createQuery("SELECT d FROM ProductGlobalDiscount d WHERE d.id=:id AND d.tenantId=:tid",
                        ProductGlobalDiscount.class)
                .setParameter("id", id).setParameter("tid", tid).getResultStream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "产品全局折扣不存在"));
    }

    private Map<String, Object> toMap(Tuple t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.get("id"));
        m.put("productId", t.get("product_id"));
        m.put("productCode", DiscountSupport.val(t.get("product_code")));
        m.put("productName", DiscountSupport.val(t.get("product_name")));
        m.put("productUnit", DiscountSupport.val(t.get("product_unit")));
        m.put("discountRate", t.get("discount_rate"));
        m.put("discountRateText", DiscountSupport.rateText(t.get("discount_rate")));
        m.put("validFrom", DateFmt.fmt(t.get("valid_from")));
        m.put("validTo", DateFmt.fmt(t.get("valid_to")));
        m.put("status", t.get("status"));
        m.put("remark", DiscountSupport.val(t.get("remark")));
        m.put("createdAt", DateFmt.fmt(t.get("created_at")));
        m.put("updatedAt", DateFmt.fmt(t.get("updated_at")));
        return m;
    }

    private static Long toLong(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try { return Long.valueOf(String.valueOf(o).trim()); }
        catch (NumberFormatException e) { throw new BusinessException(ErrorCode.PARAM_INVALID, "ID 格式不正确：" + o); }
    }
}
