package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.ProductBundle;
import com.dms.masterdata.entity.ProductBundleLine;
import com.dms.masterdata.repository.ProductBundleLineRepository;
import com.dms.masterdata.repository.ProductBundleRepository;
import com.dms.masterdata.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductBundleService {
    private static final String PRICING_INHERIT = "INHERIT";
    private static final String PRICING_OVERRIDE = "OVERRIDE";
    private static final String PRICING_COMPONENT = "COMPONENT";
    private static final String LINE_FIXED = "FIXED";
    private static final String LINE_OPTIONAL = "OPTIONAL";

    private final ProductBundleRepository bundleRepository;
    private final ProductBundleLineRepository lineRepository;
    private final ProductRepository productRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Transactional(readOnly = true)
    public PageResult<ProductBundle> list(PageQuery pageQuery, String keyword, String code, String name, String status, String versionStatus) {
        UUID tenantId = TenantContext.getTenantId();
        int pageNumber = Math.max(0, pageQuery.getPage() == null ? 0 : pageQuery.getPage() - 1);
        int pageSize = Math.min(1000, Math.max(1, pageQuery.getSize() == null ? 20 : pageQuery.getSize()));
        StringBuilder where = new StringBuilder("WHERE pb.tenant_id=?1 AND pb.deleted_at IS NULL");
        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(tenantId);
        int idx = 2;
        if (code != null && !code.isBlank()) { where.append(" AND pb.code ILIKE ?").append(idx++); params.add("%" + code.trim() + "%"); }
        if (name != null && !name.isBlank()) { where.append(" AND pb.name ILIKE ?").append(idx++); params.add("%" + name.trim() + "%"); }
        if (status != null && !status.isBlank()) { where.append(" AND pb.status = ?").append(idx++); params.add(status); }
        if (versionStatus != null && !versionStatus.isBlank()) { where.append(" AND pb.version_status = ?").append(idx++); params.add(versionStatus); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (");
            String[] tokens = keyword.trim().split("[\\s,，]+");
            boolean first = true;
            for (String token : tokens) {
                if (token.isBlank()) continue;
                if (!first) where.append(" OR ");
                where.append("(pb.code ILIKE ?").append(idx).append(" OR pb.name ILIKE ?").append(idx+1).append(" OR p.code ILIKE ?").append(idx+2).append(" OR p.name_cn ILIKE ?").append(idx+3).append(")");
                String kw = "%" + token.trim() + "%";
                params.add(kw); params.add(kw); params.add(kw); params.add(kw);
                idx += 4;
                first = false;
            }
            where.append(")");
        }
        var cnt = em.createNativeQuery("SELECT COUNT(*) FROM product_bundles pb LEFT JOIN products p ON p.id=pb.product_id " + where);
        for (int i=0;i<params.size();i++) cnt.setParameter(i+1, params.get(i));
        long total = ((Number) cnt.getSingleResult()).longValue();
        String sortExpr = "pb.updated_at DESC, pb.id DESC";
        if (pageQuery.getSort() != null && !pageQuery.getSort().isBlank()) {
            String[] parts = pageQuery.getSort().split("[;,]");
            java.util.List<String> orders = new java.util.ArrayList<>();
            for (String part : parts) {
                String[] seg = part.split(",");
                String field = seg[0].trim();
                String dir = seg.length > 1 && seg[1].trim().equalsIgnoreCase("asc") ? "ASC" : "DESC";
                if ("productCode".equals(field)) orders.add("p.code " + dir);
                else if ("productName".equals(field)) orders.add("p.name_cn " + dir);
                else if ("code".equals(field)) orders.add("pb.code " + dir);
                else if ("name".equals(field)) orders.add("pb.name " + dir);
                else if ("bomVersion".equals(field)) orders.add("pb.bom_version " + dir);
                else if ("updatedAt".equals(field)) orders.add("pb.updated_at " + dir);
            }
            if (!orders.isEmpty()) sortExpr = String.join(",", orders);
        }
        var q = em.createNativeQuery("SELECT pb.* FROM product_bundles pb LEFT JOIN products p ON p.id=pb.product_id " + where + " ORDER BY " + sortExpr + " LIMIT ?" + idx + " OFFSET ?" + (idx+1), ProductBundle.class);
        for (int i=0;i<params.size();i++) q.setParameter(i+1, params.get(i));
        q.setParameter(idx, pageSize);
        q.setParameter(idx+1, pageNumber * pageSize);
        @SuppressWarnings("unchecked")
        java.util.List<ProductBundle> rows = q.getResultList();
        rows.forEach(b -> { fillProductInfo(b); b.setLines(java.util.List.of()); });
        return new PageResult<>(total, pageNumber + 1, pageSize, rows);
    }

    @Transactional(readOnly = true)
    public ProductBundle get(Long id) {
        ProductBundle bundle = bundleRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "BOM不存在或已删除"));
        fillProductInfo(bundle);
        bundle.setLines(listLines(bundle.getId()));
        return bundle;
    }

    @Transactional(readOnly = true)
    public ProductBundle getByProductIdAndCode(Long productId, String code) {
        UUID tenantId = TenantContext.getTenantId();
        ProductBundle bundle = bundleRepository.findByTenantIdAndProductIdAndCode(tenantId, productId, code).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "BOM不存在或已删除"));
        fillProductInfo(bundle);
        bundle.setLines(listLines(bundle.getId()));
        return bundle;
    }

    @Transactional(readOnly = true)
    public List<ProductBundle> listByProduct(Long productId) {
        UUID tenantId = TenantContext.getTenantId();
        List<ProductBundle> list = bundleRepository.findByTenantIdAndProductId(tenantId, productId);
        list.forEach(this::fillProductInfo);
        return list;
    }

    @Transactional(readOnly = true)
    public List<ProductBundleLine> listLines(Long bundleId) {
        UUID tenantId = TenantContext.getTenantId();
        List<ProductBundleLine> lines = lineRepository.findByTenantIdAndBundleId(tenantId, bundleId);
        lines.forEach(this::fillChildInfo);
        return lines;
    }

    @Transactional(readOnly = true)
    public List<ProductBundleLine> listFixedLines(Long bundleId) {
        return lineRepository.findByTenantIdAndBundleIdAndLineType(TenantContext.getTenantId(), bundleId, LINE_FIXED);
    }

    @Transactional
    public ProductBundle create(ProductBundle entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少tenantId");
        if (entity.getProductId() == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少SKU");
        if (!productRepository.existsById(entity.getProductId())) throw new BusinessException(ErrorCode.NOT_FOUND, "SKU不存在或已删除");
        if (bundleRepository.existsByTenantIdAndProductIdAndCode(tenantId, entity.getProductId(), entity.getCode())) throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "BOM子件不存在");
        validatePricingType(entity.getPricingType());
        if (PRICING_OVERRIDE.equals(entity.getPricingType()) && entity.getBundlePrice() == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "母件覆盖价模式必须填写母件覆盖价");
        validatePeriod(entity);
        entity.setId(null);
        entity.setTenantId(tenantId);
        applyDefaults(entity);
        ProductBundle saved = bundleRepository.save(entity);
        replaceLines(saved, entity.getLines());
        saved.setLines(listLines(saved.getId()));
        fillProductInfo(saved);
        return saved;
    }

    @Transactional
    public ProductBundle update(Long id, ProductBundle patch) {
        ProductBundle old = get(id);
        if (!"draft".equalsIgnoreCase(old.getVersionStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有草稿状态的BOM版本才能编辑，如需修改请新建版本");
        }
        // 母件头部字段一旦创建不允许修改
        if (patch.getProductId() != null && !patch.getProductId().equals(old.getProductId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "BOM母件SKU不允许修改");
        }
        if (patch.getCode() != null && !patch.getCode().equals(old.getCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "BOM编码不允许修改");
        }
        if (patch.getName() != null && !patch.getName().equals(old.getName())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "BOM名称不允许修改");
        }
        if (patch.getDescription() != null) old.setDescription(patch.getDescription());
        if (patch.getPricingType() != null) old.setPricingType(patch.getPricingType());
        if (patch.getBundlePrice() != null) old.setBundlePrice(patch.getBundlePrice());
        if (patch.getAllowSplit() != null) old.setAllowSplit(patch.getAllowSplit());
        if (patch.getSplitRule() != null) old.setSplitRule(patch.getSplitRule());
        if (patch.getVersionNote() != null) old.setVersionNote(patch.getVersionNote());
        if (patch.getValidFrom() != null) old.setValidFrom(patch.getValidFrom());
        if (patch.getValidTo() != null) old.setValidTo(patch.getValidTo());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        // bomVersion / versionStatus / versionLocked 不接受编辑传入
        validatePricingType(old.getPricingType());
        if (PRICING_OVERRIDE.equals(old.getPricingType()) && old.getBundlePrice() == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "母件覆盖价模式必须填写母件覆盖价");
        validatePeriod(old);
        old.setUpdatedAt(OffsetDateTime.now());
        old.setUpdatedBy(TenantContext.getUserId());
        ProductBundle saved = bundleRepository.save(old);
        if (patch.getLines() != null) replaceLines(saved, patch.getLines());
        saved.setLines(listLines(saved.getId()));
        fillProductInfo(saved);
        return saved;
    }

    /** 活动BOM（订单选BOM时使用）。*/
    @Transactional(readOnly = true)
    public ProductBundle activeByProduct(Long productId) {
        UUID tenantId = TenantContext.getTenantId();
        List<ProductBundle> actives = bundleRepository.findByTenantIdAndProductIdAndVersionStatus(tenantId, productId, "active");
        if (actives.isEmpty()) {
            List<ProductBundle> all = bundleRepository.findByTenantIdAndProductId(tenantId, productId);
            if (all.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "该产品没有可用的BOM定义");
            actives = all;
        }
        ProductBundle bundle = actives.stream().reduce((a, b) -> a.getId() > b.getId() ? a : b).orElseThrow();
        fillProductInfo(bundle);
        bundle.setLines(listFixedLines(bundle.getId()));
        bundle.getLines().forEach(this::fillChildInfo);
        return bundle;
    }

    /** 基于当前版本新建草稿版本（原版本保持不变，草稿可编辑子件）。*/
    @Transactional
    public ProductBundle createNewVersion(Long id) {
        ProductBundle base = get(id);
        if ("draft".equalsIgnoreCase(base.getVersionStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "当前版本已是草稿，无需新建版本");
        }
        List<ProductBundleLine> baseLines = listLines(base.getId());
        ProductBundle draft = ProductBundle.builder()
                .tenantId(base.getTenantId())
                .productId(base.getProductId())
                .code(base.getCode())
                .name(base.getName())
                .description(base.getDescription())
                .pricingType(base.getPricingType())
                .bundlePrice(base.getBundlePrice())
                .allowSplit(base.getAllowSplit())
                .splitRule(base.getSplitRule())
                .bomVersion(nextVersion(base.getTenantId(), base.getProductId()))
                .versionStatus("draft")
                .versionLocked(Boolean.FALSE)
                .validFrom(base.getValidFrom())
                .validTo(base.getValidTo())
                .status("active")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdBy(TenantContext.getUserId())
                .updatedBy(TenantContext.getUserId())
                .build();
        ProductBundle saved = bundleRepository.save(draft);
        int seq = 1;
        for (ProductBundleLine l : baseLines) {
            if (l.getDeletedAt() != null) continue;
            ProductBundleLine copy = ProductBundleLine.builder()
                    .tenantId(saved.getTenantId()).bundleId(saved.getId())
                    .childProductId(l.getChildProductId()).lineType(l.getLineType())
                    .quantity(l.getQuantity()).isRequired(l.getIsRequired())
                    .sortOrder(l.getSortOrder() != null ? l.getSortOrder() : seq)
                    .description(l.getDescription()).status("active")
                    .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                    .createdBy(TenantContext.getUserId()).updatedBy(TenantContext.getUserId())
                    .build();
            lineRepository.save(copy);
            seq++;
        }
        saved.setLines(listLines(saved.getId()));
        fillProductInfo(saved);
        return saved;
    }

    /** 发布草稿：原活动版本置为历史，草稿变为活动。*/
    @Transactional
    public ProductBundle activateDraft(Long id) {
        ProductBundle draft = get(id);
        if (!"draft".equalsIgnoreCase(draft.getVersionStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有草稿版本可以发布");
        }
        List<ProductBundleLine> lines = listLines(draft.getId());
        if (lines.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "请至少维护一条BOM子件");
        UUID tenantId = draft.getTenantId();
        List<ProductBundle> actives = bundleRepository.findByTenantIdAndProductIdAndVersionStatus(tenantId, draft.getProductId(), "active");
        for (ProductBundle a : actives) {
            a.setVersionStatus("history");
            a.setVersionLocked(Boolean.TRUE);
            a.setUpdatedAt(OffsetDateTime.now());
            a.setUpdatedBy(TenantContext.getUserId());
        }
        bundleRepository.saveAll(actives);
        draft.setVersionStatus("active");
        draft.setVersionLocked(Boolean.TRUE);
        draft.setUpdatedAt(OffsetDateTime.now());
        draft.setUpdatedBy(TenantContext.getUserId());
        ProductBundle saved = bundleRepository.save(draft);
        saved.setLines(lines);
        fillProductInfo(saved);
        return saved;
    }

    @Transactional
    public ProductBundleLine addLine(Long bundleId, ProductBundleLine request) {
        ProductBundle bundle = get(bundleId);
        if (request.getChildProductId() == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少SKU");
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ONE) < 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "子件数量最少为1");
        request.setId(null);
        request.setBundleId(bundleId);
        request.setLineType(request.getLineType() == null ? LINE_FIXED : request.getLineType());
        replaceLines(bundle, List.of(request));
        List<ProductBundleLine> lines = listLines(bundleId);
        return lines.isEmpty() ? null : lines.get(lines.size() - 1);
    }

    @Transactional
    public void removeLine(Long bundleId, Long lineId) {
        get(bundleId);
        ProductBundleLine line = lineRepository.findById(lineId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "BOM子件不存在"));
        if (!line.getBundleId().equals(bundleId)) throw new BusinessException(ErrorCode.PARAM_INVALID, "子件不属于该BOM");
        line.setDeletedAt(OffsetDateTime.now());
        line.setUpdatedAt(OffsetDateTime.now());
        lineRepository.save(line);
    }

    @Transactional
    public void deactivate(Long id) {
        ProductBundle entity = get(id);
        log.info("停用BOM: id={} code={}", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        bundleRepository.save(entity);
    }

    @Transactional
    public void deleteById(Long id) {
        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "BOM不允许删除，请使用停用或新建版本");
    }

    private String nextVersion(UUID tenantId, Long productId) {
        return bundleRepository.findMaxBomVersion(tenantId, productId)
                .map(v -> {
                    String s = v == null ? "" : v.trim();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
                    if (m.find()) {
                        try { return String.valueOf(Integer.parseInt(m.group(1)) + 1); }
                        catch (NumberFormatException ignored) {}
                    }
                    return s + ".1";
                }).orElse("1");
    }

    private Pageable safePageable(PageQuery pageQuery) {
        Pageable origin = pageQuery.toPageable();
        Sort sort = Sort.unsorted();
        for (Sort.Order order : origin.getSort()) {
            String prop = order.getProperty();
            if ("productCode".equals(prop)) prop = "productId";
            if ("productName".equals(prop)) prop = "productId";
            if ("bomVersion".equals(prop)) prop = "bomVersion";
            sort = sort.and(Sort.by(order.getDirection(), prop));
        }
        return PageRequest.of(origin.getPageNumber(), origin.getPageSize(), sort);
    }

    private void applyDefaults(ProductBundle entity) {
        if (entity.getAllowSplit() == null) entity.setAllowSplit(true);
        if (entity.getBomVersion() == null || entity.getBomVersion().isBlank()) entity.setBomVersion("1.0");
        if (entity.getVersionStatus() == null) entity.setVersionStatus("active");
        if (entity.getVersionLocked() == null) entity.setVersionLocked(Boolean.TRUE);
        if (entity.getStatus() == null) entity.setStatus("active");
        if (entity.getPricingType() == null) entity.setPricingType(PRICING_INHERIT);
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setCreatedBy(TenantContext.getUserId());
        entity.setUpdatedBy(TenantContext.getUserId());
    }

    private void validatePeriod(ProductBundle entity) {
        if (entity.getValidFrom() != null && entity.getValidTo() != null && entity.getValidTo().isBefore(entity.getValidFrom())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "BOM生效结束时间不能早于开始时间");
        }
    }

    private void replaceLines(ProductBundle bundle, List<ProductBundleLine> requested) {
        if (requested == null) return;
        UUID tenantId = bundle.getTenantId();
        List<ProductBundleLine> existing = lineRepository.findByTenantIdAndBundleId(tenantId, bundle.getId());
        existing.forEach(l -> { l.setDeletedAt(OffsetDateTime.now()); l.setUpdatedAt(OffsetDateTime.now()); });
        lineRepository.saveAll(existing);
        lineRepository.flush();
        em.flush();
        int seq = 1;
        for (ProductBundleLine line : requested) {
            if (line.getChildProductId() == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "BOM子件不能为空");
            if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ONE) < 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "BOM子件数量最少为1");
            if (line.getChildProductId().equals(bundle.getProductId())) throw new BusinessException(ErrorCode.PARAM_INVALID, "BOM子件不能与母件相同");
            if (!productRepository.existsById(line.getChildProductId())) throw new BusinessException(ErrorCode.NOT_FOUND, "SKU不存在或已删除");
            ProductBundleLine saved = ProductBundleLine.builder()
                    .tenantId(tenantId).bundleId(bundle.getId()).childProductId(line.getChildProductId())
                    .lineType(line.getLineType() == null ? LINE_FIXED : line.getLineType())
                    .quantity(line.getQuantity()).isRequired(line.getIsRequired() == null ? Boolean.TRUE : line.getIsRequired())
                    .sortOrder(line.getSortOrder() == null ? seq : line.getSortOrder())
                    .description(line.getDescription()).status("active")
                    .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                    .createdBy(TenantContext.getUserId()).updatedBy(TenantContext.getUserId())
                    .build();
            lineRepository.save(saved);
            seq++;
        }
    }

    private void fillProductInfo(ProductBundle bundle) {
        if (bundle == null || bundle.getProductId() == null) return;
        productRepository.findById(bundle.getProductId()).ifPresent(p -> {
            bundle.setProductCode(p.getCode());
            bundle.setProductName(p.getNameCn());
        });
    }

    private void fillChildInfo(ProductBundleLine line) {
        productRepository.findById(line.getChildProductId()).ifPresent(p -> {
            line.setChildProductCode(p.getCode());
            line.setChildProductName(p.getNameCn());
            line.setChildProductSpec(p.getSpec());
        });
    }

    private void validatePricingType(String type) {
        if (type == null) return;
        if (!PRICING_INHERIT.equals(type) && !PRICING_OVERRIDE.equals(type) && !PRICING_COMPONENT.equals(type)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "价格方式必须是INHERIT/OVERRIDE/COMPONENT: " + type);
        }
    }
}

