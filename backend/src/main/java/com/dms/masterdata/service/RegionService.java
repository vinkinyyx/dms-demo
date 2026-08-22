/*
 * 区域业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.dto.TreeNodeDTO;
import com.dms.masterdata.entity.Region;
import com.dms.masterdata.repository.RegionRepository;
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
public class RegionService {

    private final RegionRepository repository;

    @Transactional(readOnly = true)
    public PageResult<Region> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<Region> page = tenantId == null
                ? repository.findAll(pageQuery.toPageable())
                : repository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public Region get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "区域不存在"));
    }

    @Transactional
    public Region create(Region entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "区域编码已存在");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null) entity.setStatus("active");
        if (entity.getLevel() == null) entity.setLevel(1);
        entity.setUpdatedAt(OffsetDateTime.now());
        return repository.save(entity);
    }

    @Transactional
    public Region update(Long id, Region patch) {
        Region old = get(id);
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
    public boolean upsertByCode(Region entity) {
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
        try {
            repository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            log.warn("删除区域失败，存在数据库外键约束: id={}", id, e);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除区域：该数据被其他业务数据引用，请先删除关联数据");
        }
    }

    @Transactional
    public void deactivate(Long id) {
        Region entity = get(id);
        log.info("停用区域: id={} code={}（未做引用检查）", id, entity.getCode());
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
        java.util.List<Region> regions = repository.findByTenantIdAndStatusOrderByLevelAscSortOrderAscIdAsc(tenantId, "active");
        java.util.Map<Long, TreeNodeDTO> map = new java.util.LinkedHashMap<>();
        for (Region r : regions) {
            map.put(r.getId(), TreeNodeDTO.builder()
                    .id(r.getId()).parentId(r.getParentId()).code(r.getCode()).name(r.getName())
                    .level(r.getLevel()).sortOrder(r.getSortOrder()).status(r.getStatus())
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
