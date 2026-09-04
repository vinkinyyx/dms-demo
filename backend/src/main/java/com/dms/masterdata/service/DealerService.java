/*
 * 经销商业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.repository.DealerRepository;
import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.service.ApprovalService;
import com.dms.security.DataScope;
import jakarta.persistence.criteria.Predicate;
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

import com.dms.masterdata.service.ReferenceCheckService;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealerService {

    private final DealerRepository repository;
    private final ReferenceCheckService referenceCheckService;
    private final com.dms.execution.service.AuditLogService opLog;
    private final DataScope dataScope;
    @Lazy
    private final ApprovalService approvalService;
    @Lazy
    private final DealerService self;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Transactional(readOnly = true)
    public PageResult<Dealer> list(PageQuery pageQuery) {
        return list(pageQuery, null);
    }

    @Transactional(readOnly = true)
    public PageResult<Dealer> list(PageQuery pageQuery, java.util.Map<String, String> filters) {
        UUID tenantId = TenantContext.getTenantId();
        var base = com.dms.common.util.SpecUtil.<Dealer>byTenantAndFilters(tenantId, filters);
        java.util.Set<Long> allowed = dataScope.accessibleDealerIds();
        org.springframework.data.jpa.domain.Specification<Dealer> spec = base;
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return PageResult.of(Page.empty(pageQuery.toPageable()));
            }
            spec = spec.and((root, q, cb) -> {
                q.distinct(true);
                Predicate p = root.get("id").in(allowed);
                return p;
            });
        }
        Page<Dealer> page = repository.findAll(spec, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public Dealer get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "经销商不存在"));
    }

    @Transactional
    public Dealer create(Dealer entity) {
        return create(entity, true);
    }

    private Dealer create(Dealer entity, boolean requireApproval) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "经销商编码已存在");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        entity.setStatus(requireApproval ? "pending_approval" : "active");
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.ensureAttrs();
        Dealer saved = repository.save(entity);
        opLog.log("dealer", saved.getId(), "CREATE", "新建经销商 " + saved.getCode());
        if (requireApproval) scheduleCreateApproval(saved);
        return saved;
    }

    private void scheduleCreateApproval(Dealer saved) {
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

    private void kickoffCreateApproval(Dealer saved) {
        try {
            self.startCreateApproval(saved.getId(), saved.getCode(), saved.getName());
        } catch (Exception e) {
            log.warn("经销商创建审批发起失败，回退为 active: id={} code={}", saved.getId(), saved.getCode(), e);
            try {
                self.fallbackActive(saved.getId());
            } catch (Exception ex) {
                log.error("经销商创建审批失败后回退 active 也失败: id={}", saved.getId(), ex);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startCreateApproval(Long id, String code, String name) {
        StartApprovalRequest request = new StartApprovalRequest();
        request.setBusinessType(DealerCreateApprovalCallback.BUSINESS_TYPE);
        request.setBusinessId(id);
        request.setBusinessCode(code);
        request.setTitle("经销商创建审批-" + (name != null ? name : code));
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("id", id);
        snapshot.put("code", code);
        snapshot.put("name", name);
        request.setBusinessSnapshot(snapshot);
        approvalService.start(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallbackActive(Long id) {
        em.createNativeQuery("UPDATE dealers SET status='active', updated_at=now() WHERE id=?1")
          .setParameter(1, id).executeUpdate();
        opLog.log("dealer", id, "UPDATE", "经销商创建审批发起失败，回退为生效状态");
    }

    @Transactional
    public Dealer update(Long id, Dealer patch) {
        Dealer old = get(id);
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getLevel() != null) old.setLevel(patch.getLevel());
        if (patch.getParentDealerId() != null) old.setParentDealerId(patch.getParentDealerId());
        if (patch.getLegalPerson() != null) old.setLegalPerson(patch.getLegalPerson());
        if (patch.getUscNo() != null) old.setUscNo(patch.getUscNo());
        if (patch.getRegAddress() != null) old.setRegAddress(patch.getRegAddress());
        if (patch.getRegCapital() != null) old.setRegCapital(patch.getRegCapital());
        if (patch.getFoundedAt() != null) old.setFoundedAt(patch.getFoundedAt());
        if (patch.getBusinessScope() != null) old.setBusinessScope(patch.getBusinessScope());
        if (patch.getGspStatus() != null) old.setGspStatus(patch.getGspStatus());
        if (patch.getGspExpire() != null) old.setGspExpire(patch.getGspExpire());
        if (patch.getGmpStatus() != null) old.setGmpStatus(patch.getGmpStatus());
        if (patch.getGmpExpire() != null) old.setGmpExpire(patch.getGmpExpire());
        if (patch.getRegionId() != null) old.setRegionId(patch.getRegionId());
        if (patch.getContactName() != null) old.setContactName(patch.getContactName());
        if (patch.getContactPhone() != null) old.setContactPhone(patch.getContactPhone());
        if (patch.getContactEmail() != null) old.setContactEmail(patch.getContactEmail());
        if (patch.getSalesOwnerUserId() != null) old.setSalesOwnerUserId(patch.getSalesOwnerUserId());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        if (patch.getAttrs() != null) old.setAttrs(patch.getAttrs());
        old.setUpdatedAt(OffsetDateTime.now());
        Dealer saved = repository.save(old);
        opLog.log("dealer", id, "UPDATE", "编辑经销商 " + saved.getCode());
        return saved;
    }


    /**
     * 通用 upsert 辅助方法；保存成功且存在 id 时返回 true，否则 false
     */
    /**
     * 按业务编码 upsert（供批量导入）：编码已存在则按非空字段更新，否则新建。返回 true 表示新建。
     */
    @Transactional
    public boolean upsertByCode(Dealer entity) {
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
        Dealer entity = get(id);
        var refs = referenceCheckService.dealerReferences(id);
        long total = referenceCheckService.totalRefs(refs);
        if (total > 0) {
            String desc = referenceCheckService.describe(refs);
            log.warn("删除经销商被拒绝: id={} code={} 引用={}", id, entity.getCode(), desc);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除经销商：存在 " + total + " 条引用记录 (" + desc + ")");
        }
        try {
            repository.deleteById(id);
            opLog.log("dealer", id, "DELETE", "删除经销商 " + entity.getCode());
        } catch (DataIntegrityViolationException e) {
            log.warn("删除经销商失败，存在数据库外键约束: id={}", id, e);
            throw new BusinessException(ErrorCode.HAS_REFERENCES,
                "无法删除经销商：该数据被其他业务数据引用，请先删除关联数据");
        }
    }

    @Transactional
    public void deactivate(Long id) {
        Dealer entity = get(id);
        log.info("停用经销商: id={} code={}（未做订单/合同/授权引用检查）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
        opLog.log("dealer", id, "DEACTIVATE", "停用经销商 " + entity.getCode());
    }
}
