package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.ProductPackageLevel;
import com.dms.masterdata.entity.Product;
import com.dms.masterdata.repository.ProductPackageLevelRepository;
import com.dms.masterdata.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPackageLevelService {

    private final ProductPackageLevelRepository repository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public PageResult<ProductPackageLevel> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        var spec = com.dms.common.util.SpecUtil.<ProductPackageLevel>byTenantAndFilters(tenantId, null);
        Page<ProductPackageLevel> page = repository.findAll(spec, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public ProductPackageLevel get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "包装层级不存在"));
    }

    @Transactional(readOnly = true)
    public List<ProductPackageLevel> listByProduct(Long productId) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) return List.of();
        return repository.findByTenantIdAndProductId(tenantId, productId);
    }

    @Transactional(readOnly = true)
    public List<ProductPackageLevel> listRootsByProduct(Long productId) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) return List.of();
        return repository.findByTenantIdAndProductIdAndParentIdIsNull(tenantId, productId);
    }

    @Transactional(readOnly = true)
    public List<ProductPackageLevel> listChildren(Long parentId) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) return List.of();
        return repository.findByTenantIdAndParentId(tenantId, parentId);
    }

    @Transactional
    public ProductPackageLevel create(ProductPackageLevel entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (entity.getProductId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 productId");
        }
        if (!productRepository.existsById(entity.getProductId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "关联产品不存在");
        }
        if (repository.existsByTenantIdAndProductIdAndCode(tenantId, entity.getProductId(), entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "包装层级编码已存在");
        }
        validateLevel(entity);
        if (entity.getQuantity() == null || entity.getQuantity() < 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "数量必须为正整数");
        }
        if (entity.getParentId() != null) {
            ProductPackageLevel parent = get(entity.getParentId());
            if (!parent.getProductId().equals(entity.getProductId())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "父层级必须属于同一产品");
            }
            if (entity.getLevel() <= parent.getLevel()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "子层级 level 必须大于父层级");
            }
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null) entity.setStatus("active");
        if (entity.getUom() == null) entity.setUom("piece");
        if (entity.getSortOrder() == null) entity.setSortOrder(0);
        entity.setUpdatedAt(OffsetDateTime.now());
        ProductPackageLevel saved = repository.save(entity);
        updateProductPackageLevelsCount(entity.getProductId());
        return saved;
    }

    @Transactional
    public ProductPackageLevel update(Long id, ProductPackageLevel patch) {
        ProductPackageLevel old = get(id);
        if (patch.getCode() != null && !patch.getCode().equals(old.getCode())) {
            if (repository.existsByTenantIdAndProductIdAndCodeAndIdNot(
                    old.getTenantId(), old.getProductId(), patch.getCode(), id)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "编码已存在: " + patch.getCode());
            }
            old.setCode(patch.getCode());
        }
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getLevel() != null) old.setLevel(patch.getLevel());
        if (patch.getQuantity() != null && patch.getQuantity() >= 1) old.setQuantity(patch.getQuantity());
        if (patch.getUom() != null) old.setUom(patch.getUom());
        if (patch.getBarcodeFormat() != null) old.setBarcodeFormat(patch.getBarcodeFormat());
        if (patch.getGtin() != null) old.setGtin(patch.getGtin());
        if (patch.getSnRule() != null) old.setSnRule(patch.getSnRule());
        if (patch.getDescription() != null) old.setDescription(patch.getDescription());
        if (patch.getSortOrder() != null) old.setSortOrder(patch.getSortOrder());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        validateLevel(old);
        old.setUpdatedAt(OffsetDateTime.now());
        return repository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        ProductPackageLevel entity = get(id);
        log.info("停用包装层级: id={} code={}", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
        updateProductPackageLevelsCount(entity.getProductId());
    }

    private void validateLevel(ProductPackageLevel entity) {
        if (entity.getLevel() != null && (entity.getLevel() < 1 || entity.getLevel() > 4)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "包装层级必须在 1-4 之间");
        }
    }

    private void updateProductPackageLevelsCount(Long productId) {
        try {
            Product product = productRepository.findById(productId).orElse(null);
            if (product != null) {
                UUID tenantId = TenantContext.getTenantId();
                long count = repository.findByTenantIdAndProductId(tenantId, productId).size();
                Map<String, Object> attrs = product.getAttrs();
                if (attrs == null) attrs = new HashMap<>();
                attrs.put("packageLevelsCount", (int) count);
                product.setAttrs(attrs);
                productRepository.save(product);
            }
        } catch (Exception e) {
            log.warn("更新产品包装层级数量失败: productId={}", productId, e);
        }
    }
}
