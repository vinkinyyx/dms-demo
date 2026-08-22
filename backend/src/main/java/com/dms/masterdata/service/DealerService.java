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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public PageResult<Dealer> list(PageQuery pageQuery) {
        return list(pageQuery, null);
    }

    @Transactional(readOnly = true)
    public PageResult<Dealer> list(PageQuery pageQuery, java.util.Map<String, String> filters) {
        UUID tenantId = TenantContext.getTenantId();
        var spec = com.dms.common.util.SpecUtil.<Dealer>byTenantAndFilters(tenantId, filters);
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
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "经销商编码已存在");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null) entity.setStatus("active");
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.ensureAttrs();
        Dealer saved = repository.save(entity);
        opLog.log("dealer", saved.getId(), "CREATE", "新建经销商 " + saved.getCode());
        return saved;
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
            create(entity); return true; });
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
