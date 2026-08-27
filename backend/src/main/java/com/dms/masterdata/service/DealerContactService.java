package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.execution.service.AuditLogService;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.entity.DealerContact;
import com.dms.masterdata.repository.DealerContactRepository;
import com.dms.masterdata.repository.DealerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealerContactService {

    private final DealerContactRepository repository;
    private final DealerRepository dealerRepository;
    private final AuditLogService opLog;

    @Transactional(readOnly = true)
    public PageResult<DealerContact> list(PageQuery pageQuery, Long dealerId, String status) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (dealerId == null) {
            Page<DealerContact> page = repository.findByTenantId(tenantId, pageQuery.toPageable());
            return PageResult.of(page);
        }
        List<DealerContact> all = repository.findByTenantIdAndDealerIdOrderByIsDefaultDescIdAsc(tenantId, dealerId);
        if (status != null && !status.isBlank()) {
            all = all.stream().filter(c -> status.equals(c.getStatus())).toList();
        }
        int size = pageQuery.getSize();
        int page = pageQuery.getPage();
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResult<>((long) all.size(), page, size, all.subList(from, to));
    }

    @Transactional(readOnly = true)
    public List<DealerContact> listByDealer(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        if (dealerId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "dealerId 不能为空");
        }
        return repository.findByTenantIdAndDealerIdOrderByIsDefaultDescIdAsc(tenantId, dealerId);
    }

    @Transactional(readOnly = true)
    public DealerContact get(Long id) {
        UUID tenantId = TenantContext.getTenantId();
        return repository.findById(id)
                .filter(c -> tenantId == null || tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "联系人不存在"));
    }

    @Transactional
    public DealerContact create(DealerContact entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (entity.getDealerId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "dealerId 不能为空");
        }
        Dealer dealer = dealerRepository.findById(entity.getDealerId())
                .filter(d -> tenantId.equals(d.getTenantId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "经销商不存在"));
        validate(entity);
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getIsDefault() == null) entity.setIsDefault(false);
        if (entity.getStatus() == null) entity.setStatus("active");
        entity.setUpdatedAt(OffsetDateTime.now());
        DealerContact saved = repository.save(entity);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            clearOtherDefaults(tenantId, saved.getDealerId(), saved.getId());
        }
        if (repository.countByTenantIdAndDealerId(tenantId, saved.getDealerId()) == 1) {
            saved.setIsDefault(true);
            repository.save(saved);
        }
        opLog.log("dealer_contact", saved.getId(), "CREATE", "新建经销商联系人 " + dealer.getCode() + " " + saved.getContactName());
        return saved;
    }

    @Transactional
    public DealerContact update(Long id, DealerContact patch) {
        DealerContact old = get(id);
        validate(patch);
        if (patch.getContactName() != null) old.setContactName(patch.getContactName());
        if (patch.getPhone() != null) old.setPhone(patch.getPhone());
        if (patch.getEmail() != null) old.setEmail(patch.getEmail());
        if (patch.getPosition() != null) old.setPosition(patch.getPosition());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        if (patch.getRemark() != null) old.setRemark(patch.getRemark());
        if (patch.getIsDefault() != null) old.setIsDefault(patch.getIsDefault());
        old.setUpdatedAt(OffsetDateTime.now());
        DealerContact saved = repository.save(old);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            clearOtherDefaults(saved.getTenantId(), saved.getDealerId(), saved.getId());
        }
        opLog.log("dealer_contact", id, "UPDATE", "编辑经销商联系人 " + saved.getContactName());
        return saved;
    }

    @Transactional
    public DealerContact setDefault(Long id) {
        DealerContact target = get(id);
        clearOtherDefaults(target.getTenantId(), target.getDealerId(), target.getId());
        target.setIsDefault(true);
        target.setUpdatedAt(OffsetDateTime.now());
        DealerContact saved = repository.save(target);
        opLog.log("dealer_contact", id, "UPDATE", "设置默认联系人 " + saved.getContactName());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        DealerContact entity = get(id);
        entity.setDeletedAt(OffsetDateTime.now());
        repository.save(entity);
        log.info("软删除经销商联系人 id={}", id);
        opLog.log("dealer_contact", id, "DELETE", "删除经销商联系人 " + entity.getContactName());
        ensureDefaultExists(entity.getTenantId(), entity.getDealerId());
    }

    private void validate(DealerContact entity) {
        if (entity.getContactName() == null || entity.getContactName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "联系人姓名不能为空");
        }
        if (entity.getEmail() != null && !entity.getEmail().isBlank()
                && !entity.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "邮箱格式不正确");
        }
    }

    private void clearOtherDefaults(UUID tenantId, Long dealerId, Long keepId) {
        List<DealerContact> siblings = repository
                .findByTenantIdAndDealerIdOrderByIsDefaultDescIdAsc(tenantId, dealerId);
        for (DealerContact c : siblings) {
            if (!c.getId().equals(keepId) && Boolean.TRUE.equals(c.getIsDefault())) {
                c.setIsDefault(false);
                c.setUpdatedAt(OffsetDateTime.now());
                repository.save(c);
            }
        }
    }

    private void ensureDefaultExists(UUID tenantId, Long dealerId) {
        List<DealerContact> siblings = repository
                .findByTenantIdAndDealerIdOrderByIsDefaultDescIdAsc(tenantId, dealerId);
        if (siblings.isEmpty()) return;
        if (siblings.stream().noneMatch(c -> Boolean.TRUE.equals(c.getIsDefault()))) {
            DealerContact next = siblings.get(0);
            next.setIsDefault(true);
            next.setUpdatedAt(OffsetDateTime.now());
            repository.save(next);
        }
    }
}
