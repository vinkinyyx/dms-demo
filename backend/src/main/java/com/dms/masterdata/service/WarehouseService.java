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
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ReferenceCheckService referenceCheckService;
    private final com.dms.execution.service.AuditLogService opLog;

    @Transactional(readOnly = true)
    public PageResult<Warehouse> list(PageQuery pageQuery) {
        return list(pageQuery, null);
    }

    @Transactional(readOnly = true)
    public PageResult<Warehouse> list(PageQuery pageQuery, java.util.Map<String, String> filters) {
        UUID tenantId = TenantContext.getTenantId();
        var spec = com.dms.common.util.SpecUtil.<Warehouse>byTenantAndFilters(tenantId, filters);
        Page<Warehouse> page = repository.findAll(spec, pageQuery.toPageable());
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
        Warehouse saved = repository.save(entity);
        opLog.log("warehouse", saved.getId(), "CREATE", "新建仓库 " + saved.getCode());
        return saved;
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
        Warehouse saved = repository.save(old);
        opLog.log("warehouse", id, "UPDATE", "编辑仓库 " + saved.getCode());
        return saved;
    }


    /**
     * ????? upsert????????????????????????????? true ?????
     */
    /**
     * 按业务编码 upsert（供批量导入）：编码已存在则按非空字段更新，否则新建。返回 true 表示新建。
     */
    @Transactional
    public boolean upsertByCode(Warehouse entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "编码不能为空");
        }
        return repository.findFirstByTenantIdAndCode(tenantId, entity.getCode()).map(existing -> {
            update(existing.getId(), entity);
            return false;
        }).orElseGet(() -> {
            if (entity.getName() == null || entity.getName().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_MISSING, "名称不能为空");
            }
            entity.setId(null);
            entity.setTenantId(tenantId);
            if (entity.getStatus() == null) entity.setStatus("active");
            if (entity.getType() == null) entity.setType("main");
            entity.setUpdatedAt(OffsetDateTime.now());
            Warehouse saved = repository.save(entity);
            opLog.log("warehouse", saved.getId(), "CREATE", "新建仓库 " + saved.getCode());
            return true;
        });
    }

    @Transactional
    public void deleteById(Long id) {
        Warehouse entity = get(id);
        var refs = referenceCheckService.warehouseReferences(id);
        long total = referenceCheckService.totalRefs(refs);
        if (total > 0) {
            String desc = referenceCheckService.describe(refs);
            log.warn("删除仓库被拒绝: id={} code={} 引用={}", id, entity.getCode(), desc);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除仓库：存在 " + total + " 条引用记录 (" + desc + ")");
        }
        try {
            repository.deleteById(id);
            opLog.log("warehouse", id, "DELETE", "删除仓库 " + entity.getCode());
        } catch (DataIntegrityViolationException e) {
            log.warn("删除仓库失败，存在数据库外键约束: id={}", id, e);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除仓库：该数据被其他业务数据引用，请先删除关联数据");
        }
    }

    @Transactional
    public void deactivate(Long id) {
        Warehouse entity = get(id);
        log.info("停用仓库: id={} code={}（未做库存/单据引用检查）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
        opLog.log("warehouse", id, "DEACTIVATE", "停用仓库 " + entity.getCode());
    }
}
