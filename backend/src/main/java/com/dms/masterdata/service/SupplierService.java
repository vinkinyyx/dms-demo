/*
 * 供应商业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Supplier;
import com.dms.masterdata.repository.SupplierRepository;
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
public class SupplierService {

    private final SupplierRepository repository;
    private final ReferenceCheckService referenceCheckService;
    private final com.dms.execution.service.AuditLogService opLog;

    @Transactional(readOnly = true)
    public PageResult<Supplier> list(PageQuery pageQuery) {
        return list(pageQuery, null);
    }

    @Transactional(readOnly = true)
    public PageResult<Supplier> list(PageQuery pageQuery, java.util.Map<String, String> filters) {
        UUID tenantId = TenantContext.getTenantId();
        var spec = com.dms.common.util.SpecUtil.<Supplier>byTenantAndFilters(tenantId, filters);
        Page<Supplier> page = repository.findAll(spec, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public Supplier get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在"));
    }

    @Transactional
    public Supplier create(Supplier entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "供应商编码已存在");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null) entity.setStatus(1);
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.ensureAttrs();
        Supplier saved = repository.save(entity);
        opLog.log("supplier", saved.getId(), "CREATE", "新建供应商 " + saved.getCode());
        return saved;
    }

    @Transactional
    public Supplier update(UUID id, Supplier patch) {
        Supplier old = get(id);
        if (patch.getCode() != null && !patch.getCode().equals(old.getCode())) {
            if (repository.existsByTenantIdAndCode(old.getTenantId(), patch.getCode())) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "供应商编码已存在: " + patch.getCode());
            }
            old.setCode(patch.getCode());
        }
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getContactPerson() != null) old.setContactPerson(patch.getContactPerson());
        if (patch.getContactPhone() != null) old.setContactPhone(patch.getContactPhone());
        if (patch.getAddress() != null) old.setAddress(patch.getAddress());
        if (patch.getBankAccount() != null) old.setBankAccount(patch.getBankAccount());
        if (patch.getTaxNo() != null) old.setTaxNo(patch.getTaxNo());
        if (patch.getRemark() != null) old.setRemark(patch.getRemark());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        if (patch.getAttrs() != null) old.setAttrs(patch.getAttrs());
        old.setUpdatedAt(OffsetDateTime.now());
        Supplier saved = repository.save(old);
        opLog.log("supplier", id, "UPDATE", "编辑供应商 " + saved.getCode());
        return saved;
    }

    @Transactional
    public void deleteById(UUID id) {
        Supplier entity = get(id);
        var refs = referenceCheckService.supplierReferences(id);
        long total = referenceCheckService.totalRefs(refs);
        if (total > 0) {
            String desc = referenceCheckService.describe(refs);
            log.warn("删除供应商被拒绝: id={} code={} 引用={}", id, entity.getCode(), desc);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除供应商：存在 " + total + " 条引用记录 (" + desc + ")");
        }
        try {
            repository.deleteById(id);
            opLog.log("supplier", id, "DELETE", "删除供应商 " + entity.getCode());
        } catch (DataIntegrityViolationException e) {
            log.warn("删除供应商失败，存在数据库外键约束: id={}", id, e);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除供应商：该数据被其他业务数据引用，请先删除关联数据");
        }
    }

    @Transactional
    public void deactivate(UUID id) {
        Supplier entity = get(id);
        log.info("停用供应商: id={} code={}（引用检查已完成）", id, entity.getCode());
        entity.setStatus(0);
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
        opLog.log("supplier", id, "DEACTIVATE", "停用供应商 " + entity.getCode());
    }
}
