/*
 * 商品分类业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.ProductCategory;
import com.dms.masterdata.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryRepository repository;

    @Transactional(readOnly = true)
    public PageResult<ProductCategory> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<ProductCategory> page = tenantId == null
                ? repository.findAll(pageQuery.toPageable())
                : repository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public ProductCategory get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品分类不存在"));
    }

    @Transactional
    public ProductCategory create(ProductCategory entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "商品分类编码已存在");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null) entity.setStatus("active");
        if (entity.getLevel() == null) entity.setLevel(1);
        entity.setUpdatedAt(OffsetDateTime.now());
        return repository.save(entity);
    }

    @Transactional
    public ProductCategory update(Long id, ProductCategory patch) {
        ProductCategory old = get(id);
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getParentId() != null) old.setParentId(patch.getParentId());
        if (patch.getLevel() != null) old.setLevel(patch.getLevel());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        old.setUpdatedAt(OffsetDateTime.now());
        return repository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        ProductCategory entity = get(id);
        log.info("停用商品分类: id={} code={}（未做引用检查，V1 简化实现）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
    }
}
