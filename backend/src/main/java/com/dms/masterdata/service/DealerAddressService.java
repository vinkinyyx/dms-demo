/*
 * 经销商地址业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.DealerAddress;
import com.dms.masterdata.repository.DealerAddressRepository;
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
public class DealerAddressService {

    private final DealerAddressRepository repository;

    @Transactional(readOnly = true)
    public PageResult<DealerAddress> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<DealerAddress> page = tenantId == null
                ? repository.findAll(pageQuery.toPageable())
                : repository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public DealerAddress get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "地址不存在"));
    }

    @Transactional
    public DealerAddress create(DealerAddress entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getIsDefault() == null) entity.setIsDefault(false);
        entity.setUpdatedAt(OffsetDateTime.now());
        return repository.save(entity);
    }

    @Transactional
    public DealerAddress update(Long id, DealerAddress patch) {
        DealerAddress old = get(id);
        if (patch.getIsDefault() != null) old.setIsDefault(patch.getIsDefault());
        if (patch.getContactName() != null) old.setContactName(patch.getContactName());
        if (patch.getPhone() != null) old.setPhone(patch.getPhone());
        if (patch.getProvince() != null) old.setProvince(patch.getProvince());
        if (patch.getCity() != null) old.setCity(patch.getCity());
        if (patch.getDistrict() != null) old.setDistrict(patch.getDistrict());
        if (patch.getAddress() != null) old.setAddress(patch.getAddress());
        if (patch.getPostalCode() != null) old.setPostalCode(patch.getPostalCode());
        old.setUpdatedAt(OffsetDateTime.now());
        return repository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        DealerAddress entity = get(id);
        log.info("软删除经销商地址 id={}", id);
        entity.setDeletedAt(OffsetDateTime.now());
        repository.save(entity);
    }
}
