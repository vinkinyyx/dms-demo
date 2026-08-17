package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.ProductLine;
import com.dms.masterdata.repository.ProductLineRepository;
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
public class ProductLineService {

    private final ProductLineRepository repository;

    @Transactional(readOnly = true)
    public PageResult<ProductLine> list(PageQuery pageQuery) {
        return list(pageQuery, null);
    }

    @Transactional(readOnly = true)
    public PageResult<ProductLine> list(PageQuery pageQuery, Map<String, String> filters) {
        UUID tenantId = TenantContext.getTenantId();
        var spec = com.dms.common.util.SpecUtil.<ProductLine>byTenantAndFilters(tenantId, filters);
        Page<ProductLine> page = repository.findAll(spec, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public ProductLine get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "产品线不存在"));
    }

    @Transactional(readOnly = true)
    public List<ProductLine> listByLevel(Integer level) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) return List.of();
        return repository.findByTenantIdAndLevel(tenantId, level);
    }

    @Transactional(readOnly = true)
    public List<ProductLine> listChildren(Long parentId) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) return List.of();
        return repository.findByTenantIdAndParentId(tenantId, parentId);
    }

    @Transactional
    public ProductLine create(ProductLine entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "产品线编码已存在");
        }
        validateLevel(entity);
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null) entity.setStatus("active");
        if (entity.getSortOrder() == null) entity.setSortOrder(0);
        entity.setUpdatedAt(OffsetDateTime.now());
        return repository.save(entity);
    }

    @Transactional
    public ProductLine update(Long id, ProductLine patch) {
        ProductLine old = get(id);
        if (patch.getCode() != null && !patch.getCode().equals(old.getCode())) {
            if (repository.existsByTenantIdAndCodeAndIdNot(old.getTenantId(), patch.getCode(), id)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "产品线编码已存在: " + patch.getCode());
            }
            old.setCode(patch.getCode());
        }
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getParentId() != null) old.setParentId(patch.getParentId());
        if (patch.getLevel() != null) old.setLevel(patch.getLevel());
        if (patch.getDescription() != null) old.setDescription(patch.getDescription());
        if (patch.getSortOrder() != null) old.setSortOrder(patch.getSortOrder());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        validateLevel(old);
        old.setUpdatedAt(OffsetDateTime.now());
        return repository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        ProductLine entity = get(id);
        log.info("停用产品线: id={} code={}", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
    }

    @Transactional
    public void deleteById(Long id) {
        ProductLine entity = get(id);
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null && !repository.findByTenantIdAndParentId(tenantId, id).isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "该产品线下存在子产品线，无法删除");
        }
        repository.delete(entity);
    }

    private void validateLevel(ProductLine entity) {
        if (entity.getLevel() != null && entity.getLevel() < 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "产品线层级必须 >= 1");
        }
        if (entity.getLevel() != null && entity.getLevel() > 3) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "产品线层级不能超过 3（BU/产品线/分类）");
        }
    }
}
