/*
 * 商品分类业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.dto.TreeNodeDTO;
import com.dms.masterdata.entity.ProductCategory;
import com.dms.masterdata.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ReferenceCheckService referenceCheckService;

    @Transactional(readOnly = true)
    public PageResult<ProductCategory> list(PageQuery pageQuery) {
        return list(pageQuery, null);
    }

    @Transactional(readOnly = true)
    public PageResult<ProductCategory> list(PageQuery pageQuery, java.util.Map<String, String> filters) {
        UUID tenantId = TenantContext.getTenantId();
        var spec = com.dms.common.util.SpecUtil.<ProductCategory>byTenantAndFilters(tenantId, filters);
        Page<ProductCategory> page = repository.findAll(spec, pageQuery.toPageable());
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
        if (patch.getCode() != null && !patch.getCode().equals(old.getCode())) {
            if (repository.existsByTenantIdAndCode(old.getTenantId(), patch.getCode())) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "商品分类编码已存在: " + patch.getCode());
            }
            old.setCode(patch.getCode());
        }
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getParentId() != null) old.setParentId(patch.getParentId());
        if (patch.getLevel() != null) old.setLevel(patch.getLevel());
        if (patch.getSortOrder() != null) old.setSortOrder(patch.getSortOrder());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        old.setUpdatedAt(OffsetDateTime.now());
        return repository.save(old);
    }


    /**
     * 通用 upsert 辅助方法；保存成功且存在 id 时返回 true，否则 false
     */
    /**
     * 按业务编码 upsert（供批量导入）：编码已存在则按非空字段更新，否则新建。返回 true 表示新建。
     */
    @Transactional
    public boolean upsertByCode(ProductCategory entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "编码不能为空");
        }
        return repository.findByTenantIdAndCode(tenantId, entity.getCode()).map(existing -> {
            update(existing.getId(), entity);
            return false;
        }).orElseGet(() -> {
            if (entity.getName() == null || entity.getName().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_MISSING, "名称不能为空");
            }
            create(entity); return true; });
    }

    @Transactional
    public void deleteById(Long id) {
        ProductCategory entity = get(id);
        var refs = referenceCheckService.categoryReferences(id);
        long total = referenceCheckService.totalRefs(refs);
        if (total > 0) {
            String desc = referenceCheckService.describe(refs);
            log.warn("删除商品分类被拒绝: id={} code={} 引用={}", id, entity.getCode(), desc);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除商品分类：存在 " + total + " 条引用记录 (" + desc + ")");
        }
        try {
            repository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            log.warn("删除商品分类失败，存在数据库外键约束: id={}", id, e);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除商品分类：该数据被其他业务数据引用，请先删除关联数据");
        }
    }

    @Transactional
    public void deactivate(Long id) {
        ProductCategory entity = get(id);
        log.info("停用商品分类: id={} code={}（未做引用检查，V1 简化实现）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public java.util.List<TreeNodeDTO> tree() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        return buildTree(repository.findByTenantIdAndStatusOrderByLevelAscSortOrderAscIdAsc(tenantId, "active"));
    }

    private java.util.List<TreeNodeDTO> buildTree(java.util.List<ProductCategory> categories) {
        java.util.Map<Long, TreeNodeDTO> map = new java.util.LinkedHashMap<>();
        for (ProductCategory c : categories) {
            map.put(c.getId(), TreeNodeDTO.builder()
                    .id(c.getId()).parentId(c.getParentId()).code(c.getCode()).name(c.getName())
                    .level(c.getLevel()).sortOrder(c.getSortOrder()).status(c.getStatus())
                    .children(new java.util.ArrayList<>()).build());
        }
        java.util.List<TreeNodeDTO> roots = new java.util.ArrayList<>();
        for (TreeNodeDTO node : map.values()) {
            TreeNodeDTO parent = node.getParentId() == null ? null : map.get(node.getParentId());
            if (parent != null) parent.getChildren().add(node); else roots.add(node);
        }
        return roots;
    }
}
