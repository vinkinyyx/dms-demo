/*
 * 区域业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Region;
import com.dms.masterdata.repository.RegionRepository;
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
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        old.setUpdatedAt(OffsetDateTime.now());
        return repository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        Region entity = get(id);
        log.info("停用区域: id={} code={}（未做引用检查）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
    }
}
