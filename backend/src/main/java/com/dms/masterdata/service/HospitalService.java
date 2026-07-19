/*
 * 医院业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Hospital;
import com.dms.masterdata.repository.HospitalRepository;
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
public class HospitalService {

    private final HospitalRepository repository;
    private final com.dms.execution.service.OperationLogService opLog;

    @Transactional(readOnly = true)
    public PageResult<Hospital> list(PageQuery pageQuery) {
        return list(pageQuery, null);
    }

    @Transactional(readOnly = true)
    public PageResult<Hospital> list(PageQuery pageQuery, java.util.Map<String, String> filters) {
        UUID tenantId = TenantContext.getTenantId();
        var spec = com.dms.common.util.SpecUtil.<Hospital>byTenantAndFilters(tenantId, filters);
        Page<Hospital> page = repository.findAll(spec, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public Hospital get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "医院不存在"));
    }

    @Transactional
    public Hospital create(Hospital entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "医院编码已存在");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null) entity.setStatus("active");
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.ensureAttrs();
        Hospital saved = repository.save(entity);
        opLog.log("hospital", saved.getId(), "CREATE", "新建医院 " + saved.getCode());
        return saved;
    }

    @Transactional
    public Hospital update(Long id, Hospital patch) {
        Hospital old = get(id);
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getType() != null) old.setType(patch.getType());
        if (patch.getLevel() != null) old.setLevel(patch.getLevel());
        if (patch.getRegionId() != null) old.setRegionId(patch.getRegionId());
        if (patch.getAddress() != null) old.setAddress(patch.getAddress());
        if (patch.getContact() != null) old.setContact(patch.getContact());
        if (patch.getPhone() != null) old.setPhone(patch.getPhone());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        if (patch.getAttrs() != null) old.setAttrs(patch.getAttrs());
        old.setUpdatedAt(OffsetDateTime.now());
        Hospital saved = repository.save(old);
        opLog.log("hospital", id, "UPDATE", "编辑医院 " + saved.getCode());
        return saved;
    }

    @Transactional
    public void deactivate(Long id) {
        Hospital entity = get(id);
        log.info("停用医院: id={} code={}（未做引用检查）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
        opLog.log("hospital", id, "DEACTIVATE", "停用医院 " + entity.getCode());
    }
}
