/*
 * 商品业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Product;
import com.dms.masterdata.repository.ProductRepository;
import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.criteria.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final ReferenceCheckService referenceCheckService;
    private final com.dms.execution.service.AuditLogService opLog;
    @Lazy
    private final ApprovalService approvalService;
    @Lazy
    private final ProductService self;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Transactional(readOnly = true)
    public PageResult<Product> list(PageQuery pageQuery) {
        return list(pageQuery, null);
    }

    @Transactional(readOnly = true)
    public PageResult<Product> list(PageQuery pageQuery, java.util.Map<String, String> filters) {
        UUID tenantId = TenantContext.getTenantId();
        var spec = com.dms.common.util.SpecUtil.<Product>byTenantAndFilters(tenantId, filters);
        // excludeBundle=true 时，排除存在 active BOM 的母件（用于价格维护等只选子件/单品的场景）
        if (filters != null && "true".equalsIgnoreCase(filters.get("excludeBundle"))) {
            var base = spec;
            spec = (root, query, cb) -> {
                var sub = query.subquery(Long.class);
                var subRoot = sub.from(com.dms.masterdata.entity.ProductBundle.class);
                sub.select(subRoot.get("productId")).where(
                    cb.equal(subRoot.get("versionStatus"), "active"),
                    cb.isNull(subRoot.get("deletedAt")));
                var ps = new java.util.ArrayList<Predicate>();
                var basePred = base.toPredicate(root, query, cb);
                if (basePred != null) ps.add(basePred);
                ps.add(cb.not(root.get("id").in(sub)));
                return cb.and(ps.toArray(new Predicate[0]));
            };
        }
        org.springframework.data.domain.Pageable pageable = pageQuery.toPageable();

        // categoryName 是关联表(product_categories.name)的展示字段，不是 Product 实体属性。
        // 直接把 categoryName 传给 JPA 排序会触发 SQL 异常并返回 500。这里改为按 join 后的分类名称排序：
        // 通过一条原生查询拿到有序的 product id 列表，再用 id 分页加载数据，保证跨页全局排序正确。
        boolean sortByCategoryName = pageable.getSort().stream()
                .anyMatch(o -> "categoryName".equals(o.getProperty()));
        if (sortByCategoryName) {
            var order = pageable.getSort().getOrderFor("categoryName");
            String dir = (order != null && order.getDirection() == org.springframework.data.domain.Sort.Direction.DESC)
                    ? "DESC" : "ASC";
            String kw = null;
            if (filters != null) {
                kw = filters.get("keyword");
                if (kw == null || kw.isBlank()) kw = filters.get("kw");
            }
            StringBuilder sql = new StringBuilder(
                "SELECT p.id FROM products p LEFT JOIN product_categories c ON p.category_id = c.id "
                + "WHERE p.deleted_at IS NULL ");
            java.util.List<Object> args = new java.util.ArrayList<>();
            if (tenantId != null) { sql.append("AND p.tenant_id = ? "); args.add(tenantId); }
            if (kw != null && !kw.isBlank()) {
                sql.append("AND (LOWER(p.code) LIKE ? OR LOWER(p.name_cn) LIKE ? OR LOWER(p.spec) LIKE ?) ");
                String like = "%" + kw.trim().toLowerCase() + "%";
                args.add(like); args.add(like); args.add(like);
            }
            if (filters != null) {
                for (var e : filters.entrySet()) {
                    if ("keyword".equals(e.getKey()) || "kw".equals(e.getKey())) continue;
                    if (e.getValue() == null || e.getValue().isBlank()) continue;
                    // 仅对 products 表上存在的常用过滤字段做安全映射
                    String col = switch (e.getKey()) {
                        case "status" -> "p.status";
                        case "productType" -> "p.product_type";
                        case "code" -> "p.code";
                        default -> null;
                    };
                    if (col != null) { sql.append("AND ").append(col).append(" = ? "); args.add(e.getValue().trim()); }
                }
            }
            sql.append("ORDER BY c.name ").append(dir).append(" NULLS LAST, p.id ").append(dir);
            jakarta.persistence.Query idQuery = em.createNativeQuery(sql.toString()).setMaxResults(100000);
            for (int i = 0; i < args.size(); i++) idQuery.setParameter(i+1, args.get(i));
            @SuppressWarnings("unchecked")
            java.util.List<Number> idRows = idQuery.getResultList();
            java.util.List<Long> orderedIds = new java.util.ArrayList<>();
            for (Object o : idRows) orderedIds.add(((Number) o).longValue());

            int total = orderedIds.size();
            int from = Math.min((int) pageable.getOffset(), total);
            int to = Math.min(from + pageable.getPageSize(), total);
            java.util.List<Product> content;
            if (from >= to) {
                content = java.util.Collections.emptyList();
            } else {
                java.util.List<Long> pageIds = orderedIds.subList(from, to);
                content = repository.findAllById(pageIds);
                // 按 pageIds 顺序重排
                java.util.Map<Long, Product> byId = new java.util.HashMap<>();
                for (Product pp : content) byId.put(pp.getId(), pp);
                content = new java.util.ArrayList<>();
                for (Long id : pageIds) if (byId.containsKey(id)) content.add(byId.get(id));
            }
            fillDisplayNames(content);
            return PageResult.of(new org.springframework.data.domain.PageImpl<>(content, pageable, total));
        }

        Page<Product> page = repository.findAll(spec, pageable);
        fillDisplayNames(page.getContent());
        return PageResult.of(page);
    }

    private void fillDisplayNames(java.util.List<Product> products) {
        fillCategoryNames(products);
        fillProductLineNames(products);
        fillIsBundle(products);
    }

    private void fillIsBundle(java.util.List<Product> products) {
        if (products == null || products.isEmpty()) return;
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Product p : products) if (p.getId() != null) ids.add(p.getId());
        if (ids.isEmpty()) return;
        java.util.Set<Long> bundleIds = new java.util.HashSet<>();
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Number> rows = em.createNativeQuery(
                "SELECT DISTINCT product_id FROM product_bundles WHERE version_status = 'active' AND deleted_at IS NULL AND product_id IN (:ids)")
                .setParameter("ids", ids)
                .getResultList();
            for (Number n : rows) bundleIds.add(n.longValue());
        } catch (Exception ignored) {}
        for (Product p : products) p.setIsBundle(bundleIds.contains(p.getId()));
    }

    private void fillCategoryNames(java.util.List<Product> products) {
        if (products == null || products.isEmpty()) return;
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Product p : products) {
            if (p.getCategoryId() != null) ids.add(p.getCategoryId());
        }
        if (ids.isEmpty()) return;
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object[]> rows = em.createNativeQuery(
                "SELECT id, name FROM product_categories WHERE id IN (:ids)")
                .setParameter("ids", ids)
                .getResultList();
            java.util.Map<Long, String> map = new java.util.HashMap<>();
            for (Object[] row : rows) {
                map.put(((Number) row[0]).longValue(), String.valueOf(row[1]));
            }
            for (Product p : products) {
                if (p.getCategoryId() != null && map.containsKey(p.getCategoryId())) {
                    p.setCategoryName(map.get(p.getCategoryId()));
                }
            }
        } catch (Exception ignored) {}
        fillProductTypeNames(products);
    }

    private void fillProductLineNames(java.util.List<Product> products) {
        if (products == null || products.isEmpty()) return;
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Product p : products) {
            if (p.getProductLineId() != null) ids.add(p.getProductLineId());
        }
        if (ids.isEmpty()) return;
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object[]> rows = em.createNativeQuery(
                "SELECT id, name FROM product_lines WHERE id IN (:ids) AND deleted_at IS NULL")
                .setParameter("ids", ids)
                .getResultList();
            java.util.Map<Long, String> map = new java.util.HashMap<>();
            for (Object[] row : rows) {
                map.put(((Number) row[0]).longValue(), String.valueOf(row[1]));
            }
            for (Product p : products) {
                if (p.getProductLineId() != null) p.setProductLineName(map.get(p.getProductLineId()));
            }
        } catch (Exception ignored) {}
    }

    private void fillProductTypeNames(java.util.List<Product> products) {
        if (products == null || products.isEmpty()) return;
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object[]> rows = em.createNativeQuery(
                "SELECT di.code, di.name FROM dict_items di JOIN dict_types dt ON di.type_id = dt.id " +
                "WHERE dt.code = 'product_type'")
                .getResultList();
            java.util.Map<String, String> typeMap = new java.util.HashMap<>();
            for (Object[] row : rows) {
                typeMap.put(String.valueOf(row[0]), String.valueOf(row[1]));
            }
            for (Product p : products) {
                if (p.getProductType() != null) {
                    p.setProductTypeName(typeMap.getOrDefault(p.getProductType(), p.getProductType()));
                }
            }
        } catch (Exception ignored) {}
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        Product p = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在"));
        if (p.getCategoryId() != null) {
            try {
                Object name = em.createNativeQuery(
                        "SELECT name FROM product_categories WHERE id = ?1")
                        .setParameter(1, p.getCategoryId())
                        .getResultList().stream().findFirst().orElse(null);
                if (name != null) p.setCategoryName(String.valueOf(name));
            } catch (Exception ignored) {}
        }
        if (p.getProductLineId() != null) {
            try {
                Object lineName = em.createNativeQuery(
                        "SELECT name FROM product_lines WHERE id = ?1 AND deleted_at IS NULL")
                        .setParameter(1, p.getProductLineId())
                        .getResultList().stream().findFirst().orElse(null);
                if (lineName != null) p.setProductLineName(String.valueOf(lineName));
            } catch (Exception ignored) {}
        }
        fillProductTypeNames(java.util.List.of(p));
        fillIsBundle(java.util.List.of(p));
        return p;
    }

    @Transactional
    public Product create(Product entity) {
        return create(entity, true);
    }

    private Product create(Product entity, boolean requireApproval) {
        validateProductType(entity.getProductType());
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "商品编码已存在");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        entity.setStatus(requireApproval ? "pending_approval" : "active");
        if (entity.getTaxRate() == null) entity.setTaxRate(new BigDecimal("0.13"));
        if (entity.getUdiRequired() == null) entity.setUdiRequired(true);
        if (entity.getIsSerialManaged() == null) entity.setIsSerialManaged(false);
        // productType is accepted as-is from request (dict code, e.g. CONSUMABLE); null allowed.
        if (entity.getWarnMonths() == null) entity.setWarnMonths(3);
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.ensureAttrs();
        Product saved = repository.save(entity);
        opLog.log("product", saved.getId(), "CREATE", "新建产品 " + saved.getCode());
        if (requireApproval) scheduleCreateApproval(saved);
        return saved;
    }

    private void scheduleCreateApproval(Product saved) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kickoffCreateApproval(saved);
                }
            });
        } else {
            kickoffCreateApproval(saved);
        }
    }

    private void kickoffCreateApproval(Product saved) {
        try {
            self.startCreateApproval(saved.getId(), saved.getCode(), saved.getNameCn());
        } catch (Exception e) {
            log.warn("产品创建审批发起失败，回退为 active: id={} code={}", saved.getId(), saved.getCode(), e);
            try {
                self.fallbackActive(saved.getId());
            } catch (Exception ex) {
                log.error("产品创建审批失败后回退 active 也失败: id={}", saved.getId(), ex);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startCreateApproval(Long id, String code, String name) {
        StartApprovalRequest request = new StartApprovalRequest();
        request.setBusinessType(ProductCreateApprovalCallback.BUSINESS_TYPE);
        request.setBusinessId(id);
        request.setBusinessCode(code);
        request.setTitle("产品创建审批-" + (name != null ? name : code));
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("id", id);
        snapshot.put("code", code);
        snapshot.put("name", name);
        request.setBusinessSnapshot(snapshot);
        approvalService.start(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallbackActive(Long id) {
        em.createNativeQuery("UPDATE products SET status='active', updated_at=now() WHERE id=?1")
          .setParameter(1, id).executeUpdate();
        opLog.log("product", id, "UPDATE", "产品创建审批发起失败，回退为生效状态");
    }
    public void validateProductType(String productType) {
        if (productType == null || productType.isBlank()) {
            return;
        }
        Long count = (Long) em.createNativeQuery("""
                        SELECT COUNT(1)
                        FROM dict_items di
                        JOIN dict_types dt ON dt.id = di.type_id
                        WHERE dt.code = 'product_type'
                          AND di.code = :code
                          AND di.status = 'active'
                        """)
                .setParameter("code", productType)
                .getSingleResult();
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "productType 非法，请使用字典 product_type 中的启用值");
        }
    }


    @Transactional
    public Product update(Long id, Product patch) {
        Product old = get(id);
        validateProductType(patch.getProductType());
        if (patch.getCode() != null && !patch.getCode().equals(old.getCode())) {
            if (repository.existsByTenantIdAndCode(old.getTenantId(), patch.getCode())) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "产品编码已存在: " + patch.getCode());
            }
            old.setCode(patch.getCode());
        }
        if (patch.getNameCn() != null) old.setNameCn(patch.getNameCn());
        if (patch.getNameEn() != null) old.setNameEn(patch.getNameEn());
        if (patch.getCategoryId() != null) old.setCategoryId(patch.getCategoryId());
        if (patch.getProductLineId() != null) old.setProductLineId(patch.getProductLineId());
        if (patch.getProductType() != null) old.setProductType(patch.getProductType());
        if (patch.getSpec() != null) old.setSpec(patch.getSpec());
        if (patch.getUnit() != null) old.setUnit(patch.getUnit());
        if (patch.getCurrentPrice() != null) old.setCurrentPrice(patch.getCurrentPrice());
        if (patch.getTaxRate() != null) old.setTaxRate(patch.getTaxRate());
        if (patch.getUdiRequired() != null) old.setUdiRequired(patch.getUdiRequired());
        if (patch.getIsSerialManaged() != null) old.setIsSerialManaged(patch.getIsSerialManaged());
        if (patch.getWarnMonths() != null) old.setWarnMonths(patch.getWarnMonths());
        if (patch.getSafetyQty() != null) old.setSafetyQty(patch.getSafetyQty());
        if (patch.getMinOrderQty() != null) old.setMinOrderQty(patch.getMinOrderQty());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        if (patch.getAttrs() != null) old.setAttrs(patch.getAttrs());
        old.setUpdatedAt(OffsetDateTime.now());
        Product saved = repository.save(old);
        opLog.log("product", id, "UPDATE", "编辑产品 " + saved.getCode());
        return saved;
    }


    /**
     * 通用 upsert 辅助方法；保存成功且存在 id 时返回 true，否则 false
     */
    /**
     * 按业务编码 upsert（供批量导入）：编码已存在则按非空字段更新，否则新建。返回 true 表示新建。
     */
    @Transactional
    public boolean upsertByCode(Product entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "编码不能为空");
        }
        return repository.findByTenantIdAndCode(tenantId, entity.getCode()).map(existing -> {
            update(existing.getId(), entity);
            return false;
        }).orElseGet(() -> {
            if (entity.getNameCn() == null || entity.getNameCn().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_MISSING, "中文名称不能为空");
            }
            create(entity, false); return true; });
    }

    @Transactional
    public void deleteById(Long id) {
        Product entity = get(id);
        var refs = referenceCheckService.productReferences(id);
        long total = referenceCheckService.totalRefs(refs);
        if (total > 0) {
            String desc = referenceCheckService.describe(refs);
            log.warn("删除商品被拒绝: id={} code={} 引用={}", id, entity.getCode(), desc);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除产品：存在 " + total + " 条引用记录 (" + desc + ")");
        }
        try {
            repository.deleteById(id);
            opLog.log("product", id, "DELETE", "删除产品 " + entity.getCode());
        } catch (DataIntegrityViolationException e) {
            log.warn("删除商品失败，存在数据库外键约束: id={}", id, e);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除产品：该数据被其他业务数据引用，请先删除关联数据");
        }
    }

    @Transactional
    public void deactivate(Long id) {
        Product entity = get(id);
        // US-A-02：引用检查
        var refs = referenceCheckService.productReferences(id);
        long total = referenceCheckService.totalRefs(refs);
        if (total > 0) {
            String desc = referenceCheckService.describe(refs);
            log.warn("停用商品被拒绝: id={} code={} 引用={}", id, entity.getCode(), desc);
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "该商品被以下业务引用，无法停用: " + desc);
        }
        log.info("停用商品: id={} code={}（引用检查通过）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
        opLog.log("product", id, "DEACTIVATE", "停用产品 " + entity.getCode());
    }
}

