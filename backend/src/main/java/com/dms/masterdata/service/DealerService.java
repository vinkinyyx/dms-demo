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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealerService {

    private final DealerRepository repository;

    @Transactional(readOnly = true)
    public PageResult<Dealer> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<Dealer> page = tenantId == null
                ? repository.findAll(pageQuery.toPageable())
                : repository.findByTenantId(tenantId, pageQuery.toPageable());
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
        return repository.save(entity);
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
        return repository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        Dealer entity = get(id);
        log.info("停用经销商: id={} code={}（未做订单/合同/授权引用检查）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
    }
}
