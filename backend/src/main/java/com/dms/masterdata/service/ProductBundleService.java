package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.*;
import com.dms.masterdata.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

    @Transactional(readOnly = true)
    public PageResult<ProductBundle> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        var spec = com.dms.common.util.SpecUtil.<ProductBundle>byTenantAndFilters(tenantId, null);
        Page<ProductBundle> page = bundleRepository.findAll(spec, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public ProductBundle get(Long id) {
        return bundleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "组套不存在"));
    }

    @Transactional(readOnly = true)
    public ProductBundle getByProductIdAndCode(Long productId, String code) {
        UUID tenantId = TenantContext.getTenantId();
        return bundleRepository.findByTenantIdAndProductIdAndCode(tenantId, productId, code)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "组套不存在"));
    }

    @Transactional(readOnly = true)
    public List<ProductBundle> listByProduct(Long productId) {
        UUID tenantId = TenantContext.getTenantId();
        return bundleRepository.findByTenantIdAndProductId(tenantId, productId);
    }

    @Transactional(readOnly = true)
    public List<ProductBundleLine> listLines(Long bundleId) {
        UUID tenantId = TenantContext.getTenantId();
        return lineRepository.findByTenantIdAndBundleId(tenantId, bundleId);
    }

    @Transactional(readOnly = true)
    public List<ProductBundleLine> listFixedLines(Long bundleId) {
        UUID tenantId = TenantContext.getTenantId();
        return lineRepository.findByTenantIdAndBundleIdAndLineType(tenantId, bundleId, LINE_FIXED);
    }

    @Transactional
    public ProductBundle create(ProductBundle entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (entity.getProductId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 productId（父SKU）");
        }
        if (!productRepository.existsById(entity.getProductId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "关联产品（父SKU）不存在");
        }
        if (bundleRepository.existsByTenantIdAndProductIdAndCode(tenantId, entity.getProductId(), entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "组套编码已存在");
        }
        validatePricingType(entity.getPricingType());
        if (PRICING_OVERRIDE.equals(entity.getPricingType()) && entity.getBundlePrice() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "OVERRIDE 定价方式必须提供 bundlePrice");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getAllowSplit() == null) entity.setAllowSplit(false);
        if (entity.getStatus() == null) entity.setStatus("active");
        entity.setUpdatedAt(OffsetDateTime.now());
        ProductBundle saved = bundleRepository.save(entity);
        flagProductAsBundle(saved.getProductId(), true, saved.getId());
        return saved;
    }

    @Transactional
    public ProductBundle update(Long id, ProductBundle patch) {
        ProductBundle old = get(id);
        if (patch.getCode() != null && !patch.getCode().equals(old.getCode())) {
            if (bundleRepository.existsByTenantIdAndProductIdAndCodeAndIdNot(
                    old.getTenantId(), old.getProductId(), patch.getCode(), id)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "组套编码已存在");
            }
            old.setCode(patch.getCode());
        }
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getDescription() != null) old.setDescription(patch.getDescription());
        if (patch.getPricingType() != null) {
            validatePricingType(patch.getPricingType());
            old.setPricingType(patch.getPricingType());
        }
        if (patch.getBundlePrice() != null) old.setBundlePrice(patch.getBundlePrice());
        if (patch.getAllowSplit() != null) old.setAllowSplit(patch.getAllowSplit());
        if (patch.getSplitRule() != null) old.setSplitRule(patch.getSplitRule());
        if (patch.getVersionNote() != null) old.setVersionNote(patch.getVersionNote());
        if (patch.getValidFrom() != null) old.setValidFrom(patch.getValidFrom());
        if (patch.getValidTo() != null) old.setValidTo(patch.getValidTo());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        if (PRICING_OVERRIDE.equals(old.getPricingType()) && old.getBundlePrice() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "OVERRIDE 定价方式必须提供 bundlePrice");
        }
        old.setUpdatedAt(OffsetDateTime.now());
        return bundleRepository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        ProductBundle entity = get(id);
        log.info("停用组套: id={} code={}", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        bundleRepository.save(entity);
        Long productId = entity.getProductId();
        long remainingActive = bundleRepository.findByTenantIdAndProductId(entity.getTenantId(), productId)
                .stream().filter(b -> "active".equals(b.getStatus())).count();
        if (remainingActive == 0) {
            flagProductAsBundle(productId, false, null);
        }
    }

    @Transactional
    public ProductBundleLine addLine(Long bundleId, ProductBundleLine line) {
        UUID tenantId = TenantContext.getTenantId();
        ProductBundle bundle = get(bundleId);
        if (line.getChildProductId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 childProductId");
        }
        if (line.getChildProductId().equals(bundle.getProductId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "组套不能包含自身作为子件");
        }
        if (!productRepository.existsById(line.getChildProductId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "子产品不存在");
        }
        if (lineRepository.findByTenantIdAndBundleIdAndChildProductId(tenantId, bundleId, line.getChildProductId()).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "该子件已存在于此组套");
        }
        validateLineType(line.getLineType());
        if (line.getQuantity() == null || line.getQuantity().signum() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "子件数量必须为正");
        }
        if (line.getIsRequired() == null) {
            line.setIsRequired(LINE_FIXED.equals(line.getLineType()));
        }
        line.setId(null);
        line.setTenantId(tenantId);
        line.setBundleId(bundleId);
        if (line.getStatus() == null) line.setStatus("active");
        if (line.getSortOrder() == null) line.setSortOrder(0);
        line.setUpdatedAt(OffsetDateTime.now());
        return lineRepository.save(line);
    }

    @Transactional
    public void removeLine(Long bundleId, Long lineId) {
        UUID tenantId = TenantContext.getTenantId();
        ProductBundleLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "组套明细不存在"));
        if (!line.getBundleId().equals(bundleId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "明细不属于该组套");
        }
        if (LINE_FIXED.equals(line.getLineType())) {
            long fixedCount = lineRepository.countByTenantIdAndBundleIdAndLineType(tenantId, bundleId, LINE_FIXED);
            if (fixedCount <= 1) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "组套至少保留 1 个固定件");
            }
        }
        log.info("移除组套明细: id={} bundleId={}", lineId, bundleId);
        line.setDeletedAt(OffsetDateTime.now());
        line.setUpdatedAt(OffsetDateTime.now());
        lineRepository.save(line);
    }

    private void validatePricingType(String type) {
        if (type == null) return;
        if (!PRICING_INHERIT.equals(type) && !PRICING_OVERRIDE.equals(type) && !PRICING_COMPONENT.equals(type)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "定价方式必须为 INHERIT/OVERRIDE/COMPONENT: " + type);
        }
    }

    private void validateLineType(String type) {
        if (type == null) return;
        if (!LINE_FIXED.equals(type) && !LINE_OPTIONAL.equals(type)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "子件类型必须为 FIXED/OPTIONAL: " + type);
        }
    }

    private void flagProductAsBundle(Long productId, boolean isBundle, Long bundleId) {
        log.debug("标记产品组套状态: productId={}, isBundle={}, bundleId={}", productId, isBundle, bundleId);
    }
}
