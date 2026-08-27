package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.execution.service.AuditLogService;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.entity.DealerAddress;
import com.dms.masterdata.repository.DealerAddressRepository;
import com.dms.masterdata.repository.DealerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealerAddressService {

    private final DealerAddressRepository repository;
    private final DealerRepository dealerRepository;
    private final AuditLogService opLog;

    @Transactional(readOnly = true)
    public PageResult<DealerAddress> list(PageQuery pageQuery) {
        return list(pageQuery, null, null);
    }

    @Transactional(readOnly = true)
    public PageResult<DealerAddress> list(PageQuery pageQuery, Long dealerId, String status) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (dealerId == null) {
            Page<DealerAddress> page = repository.findByTenantId(tenantId, pageQuery.toPageable());
            return PageResult.of(page);
        }
        List<DealerAddress> all = repository.findByTenantIdAndDealerIdOrderByIsDefaultDescIdAsc(tenantId, dealerId);
        if (status != null && !status.isBlank()) {
            all = all.stream().filter(a -> status.equals(a.getStatus())).toList();
        }
        int size = pageQuery.getSize();
        int page = pageQuery.getPage();
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResult<>((long) all.size(), page, size, all.subList(from, to));
    }

    @Transactional(readOnly = true)
    public List<DealerAddress> listByDealer(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        if (dealerId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "dealerId 不能为空");
        }
        return repository.findByTenantIdAndDealerIdOrderByIsDefaultDescIdAsc(tenantId, dealerId);
    }

    @Transactional(readOnly = true)
    public DealerAddress get(Long id) {
        UUID tenantId = TenantContext.getTenantId();
        return repository.findById(id)
                .filter(a -> tenantId == null || tenantId.equals(a.getTenantId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "地址不存在"));
    }

    @Transactional
    public DealerAddress create(DealerAddress entity) {
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
        DealerAddress saved = repository.save(entity);
        boolean firstOne = repository.countByTenantIdAndDealerId(tenantId, saved.getDealerId()) == 1;
        if (Boolean.TRUE.equals(saved.getIsDefault()) || firstOne) {
            clearOtherDefaults(tenantId, saved.getDealerId(), saved.getId());
            saved.setIsDefault(true);
            saved = repository.save(saved);
        }
        opLog.log("dealer_address", saved.getId(), "CREATE", "新建经销商收货地址 " + dealer.getCode() + " " + saved.getAddressName());
        return saved;
    }

    @Transactional
    public DealerAddress update(Long id, DealerAddress patch) {
        DealerAddress old = get(id);
        validate(patch);
        if (patch.getAddressName() != null) old.setAddressName(patch.getAddressName());
        if (patch.getIsDefault() != null) old.setIsDefault(patch.getIsDefault());
        if (patch.getContactName() != null) old.setContactName(patch.getContactName());
        if (patch.getPhone() != null) old.setPhone(patch.getPhone());
        if (patch.getProvince() != null) old.setProvince(patch.getProvince());
        if (patch.getCity() != null) old.setCity(patch.getCity());
        if (patch.getDistrict() != null) old.setDistrict(patch.getDistrict());
        if (patch.getAddress() != null) old.setAddress(patch.getAddress());
        if (patch.getPostalCode() != null) old.setPostalCode(patch.getPostalCode());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        if (patch.getTags() != null) old.setTags(patch.getTags());
        old.setUpdatedAt(OffsetDateTime.now());
        DealerAddress saved = repository.save(old);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            clearOtherDefaults(saved.getTenantId(), saved.getDealerId(), saved.getId());
        }
        opLog.log("dealer_address", id, "UPDATE", "编辑经销商收货地址 " + saved.getAddressName());
        return saved;
    }

    @Transactional
    public DealerAddress setDefault(Long id) {
        DealerAddress target = get(id);
        clearOtherDefaults(target.getTenantId(), target.getDealerId(), target.getId());
        target.setIsDefault(true);
        target.setUpdatedAt(OffsetDateTime.now());
        DealerAddress saved = repository.save(target);
        opLog.log("dealer_address", id, "UPDATE", "设置默认收货地址 " + saved.getAddressName());
        return saved;
    }

    @Transactional
    public void deactivate(Long id) {
        DealerAddress entity = get(id);
        log.info("软删除经销商地址 id={}", id);
        entity.setDeletedAt(OffsetDateTime.now());
        repository.save(entity);
        opLog.log("dealer_address", id, "DELETE", "删除经销商收货地址 " + entity.getAddressName());
        ensureDefaultExists(entity.getTenantId(), entity.getDealerId());
    }

    private void validate(DealerAddress entity) {
        if (entity.getAddress() == null || entity.getAddress().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "详细地址不能为空");
        }
    }

    private void clearOtherDefaults(UUID tenantId, Long dealerId, Long keepId) {
        List<DealerAddress> siblings = repository
                .findByTenantIdAndDealerIdOrderByIsDefaultDescIdAsc(tenantId, dealerId);
        for (DealerAddress a : siblings) {
            if (!a.getId().equals(keepId) && Boolean.TRUE.equals(a.getIsDefault())) {
                a.setIsDefault(false);
                a.setUpdatedAt(OffsetDateTime.now());
                repository.save(a);
            }
        }
    }

    private void ensureDefaultExists(UUID tenantId, Long dealerId) {
        List<DealerAddress> siblings = repository
                .findByTenantIdAndDealerIdOrderByIsDefaultDescIdAsc(tenantId, dealerId);
        if (siblings.isEmpty()) return;
        if (siblings.stream().noneMatch(a -> Boolean.TRUE.equals(a.getIsDefault()))) {
            DealerAddress next = siblings.get(0);
            next.setIsDefault(true);
            next.setUpdatedAt(OffsetDateTime.now());
            repository.save(next);
        }
    }
}
