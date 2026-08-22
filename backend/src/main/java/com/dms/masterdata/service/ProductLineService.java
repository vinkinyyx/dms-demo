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
        page.forEach(this::fillParentName);
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public ProductLine get(Long id) {
        ProductLine entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "\u4ea7\u54c1\u5c42\u6b21\u4e0d\u5b58\u5728"));
        fillParentName(entity);
        return entity;
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
            throw new BusinessException(ErrorCode.PARAM_MISSING, "\u7f3a\u5c11 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "\u4ea7\u54c1\u5c42\u6b21\u7f16\u7801\u5df2\u5b58\u5728");
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
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "\u4ea7\u54c1\u5c42\u6b21\u7f16\u7801\u5df2\u5b58\u5728: " + patch.getCode());
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
        fillParentName(old);
        old.setUpdatedAt(OffsetDateTime.now());
        return repository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        ProductLine entity = get(id);
        log.info("\u505c\u7528\u4ea7\u54c1\u5c42\u6b21 id={} code={}", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
    }

    @Transactional
    public void deleteById(Long id) {
        ProductLine entity = get(id);
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null && !repository.findByTenantIdAndParentId(tenantId, id).isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "\u8be5\u4ea7\u54c1\u5c42\u6b21\u4e0b\u5b58\u5728\u5b50\u4ea7\u54c1\u5c42\u6b21\uff0c\u65e0\u6cd5\u5220\u9664");
        }
        repository.delete(entity);
    }

    private void validateLevel(ProductLine entity) {
        if (entity.getLevel() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "\u4ea7\u54c1\u5c42\u6b21\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (entity.getLevel() < 1 || entity.getLevel() > 3) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "\u4ea7\u54c1\u5c42\u6b21\u53ea\u80fd\u662f1\u30012\u30013");
        }
        if (entity.getLevel() > 1 && entity.getParentId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "\u4e0a\u7ea7\u4ea7\u54c1\u5c42\u6b21\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (entity.getParentId() != null) {
            ProductLine parent = repository.findById(entity.getParentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "\u4e0a\u7ea7\u4ea7\u54c1\u5c42\u6b21\u4e0d\u5b58\u5728"));
            if (parent.getLevel() + 1 != entity.getLevel()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "\u4ea7\u54c1\u5c42\u6b21\u5fc5\u987b\u4e0e\u4e0a\u7ea7\u5c42\u6b21\u8fde\u7eed");
            }
        }
    }

    private void fillParentName(ProductLine entity) {
        if (entity == null || entity.getParentId() == null) return;
        repository.findById(entity.getParentId()).ifPresent(parent -> entity.setParentName(parent.getName()));
    }
}
