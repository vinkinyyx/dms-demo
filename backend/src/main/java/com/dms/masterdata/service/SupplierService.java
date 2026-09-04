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
import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository repository;
    private final ReferenceCheckService referenceCheckService;
    private final com.dms.execution.service.AuditLogService opLog;
    @Lazy
    private final ApprovalService approvalService;
    @Lazy
    private final SupplierService self;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

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
    public Supplier get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在"));
    }

    @Transactional
    public Supplier create(Supplier entity) {
        return create(entity, true);
    }

    private Supplier create(Supplier entity, boolean requireApproval) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "供应商编码已存在");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        entity.setStatus(requireApproval ? "pending_approval" : "active");
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.ensureAttrs();
        Supplier saved = repository.save(entity);
        opLog.log("supplier", saved.getId(), "CREATE", "新建供应商 " + saved.getCode());
        if (requireApproval) scheduleCreateApproval(saved);
        return saved;
    }

    private void scheduleCreateApproval(Supplier saved) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kickoffCreateApproval(saved);
                }
            });
        } else {
            kickoffCreateApproval(saved);
        }
    }

    private void kickoffCreateApproval(Supplier saved) {
        try {
            self.startCreateApproval(saved.getId(), saved.getCode(), saved.getName());
        } catch (Exception e) {
            log.warn("供应商创建审批发起失败，回退为 active: id={} code={}", saved.getId(), saved.getCode(), e);
            try {
                self.fallbackActive(saved.getId());
            } catch (Exception ex) {
                log.error("供应商创建审批失败后回退 active 也失败: id={}", saved.getId(), ex);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startCreateApproval(Long id, String code, String name) {
        StartApprovalRequest request = new StartApprovalRequest();
        request.setBusinessType(SupplierCreateApprovalCallback.BUSINESS_TYPE);
        request.setBusinessId(id);
        request.setBusinessCode(code);
        request.setTitle("供应商创建审批-" + (name != null ? name : code));
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("id", id);
        snapshot.put("code", code);
        snapshot.put("name", name);
        request.setBusinessSnapshot(snapshot);
        approvalService.start(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallbackActive(Long id) {
        em.createNativeQuery("UPDATE suppliers SET status='active', updated_at=now() WHERE id=?1")
          .setParameter(1, id).executeUpdate();
        opLog.log("supplier", id, "UPDATE", "供应商创建审批发起失败，回退为生效状态");
    }

    @Transactional
    public Supplier update(Long id, Supplier patch) {
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
        if (patch.getLevel() != null) old.setLevel(patch.getLevel());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        old.setUpdatedAt(OffsetDateTime.now());
        Supplier saved = repository.save(old);
        opLog.log("supplier", id, "UPDATE", "编辑供应商 " + saved.getCode());
        return saved;
    }


    @Transactional
    public boolean upsertByCode(Supplier entity) {
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
            create(entity, false); return true; });
    }

    @Transactional
    public void deleteById(Long id) {
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
    public void deactivate(Long id) {
        Supplier entity = get(id);
        log.info("停用供应商: id={} code={}（引用检查已完成）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
        opLog.log("supplier", id, "DEACTIVATE", "停用供应商 " + entity.getCode());
    }
}