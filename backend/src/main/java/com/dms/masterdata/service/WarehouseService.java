/*
 * 仓库业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Warehouse;
import com.dms.masterdata.repository.WarehouseRepository;
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
public class WarehouseService {

    private final WarehouseRepository repository;

    @Transactional(readOnly = true)
    public PageResult<Warehouse> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<Warehouse> page = tenantId == null
                ? repository.findAll(pageQuery.toPageable())
                : repository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public Warehouse get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "仓库不存在"));
    }

    @Transactional
    public Warehouse create(Warehouse entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (entity.getDealerId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 dealerId");
        }
        if (repository.existsByTenantIdAndDealerIdAndCode(tenantId, entity.getDealerId(), entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "仓库编码在该经销商下已存在");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null) entity.setStatus("active");
        if (entity.getType() == null) entity.setType("main");
        entity.setUpdatedAt(OffsetDateTime.now());
        return repository.save(entity);
    }

    @Transactional
    public Warehouse update(Long id, Warehouse patch) {
        Warehouse old = get(id);
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getType() != null) old.setType(patch.getType());
        if (patch.getHospitalId() != null) old.setHospitalId(patch.getHospitalId());
        if (patch.getAddress() != null) old.setAddress(patch.getAddress());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        old.setUpdatedAt(OffsetDateTime.now());
        return repository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        Warehouse entity = get(id);
        log.info("停用仓库: id={} code={}（未做库存/单据引用检查）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
    }
}
