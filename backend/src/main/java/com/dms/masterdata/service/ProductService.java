/*
 * 商品业务服务：list/get/create/update/deactivate。
 */
package com.dms.masterdata.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Product;
import com.dms.masterdata.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final ReferenceCheckService referenceCheckService;

    @Transactional(readOnly = true)
    public PageResult<Product> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<Product> page = tenantId == null
                ? repository.findAll(pageQuery.toPageable())
                : repository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在"));
    }

    @Transactional
    public Product create(Product entity) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (repository.existsByTenantIdAndCode(tenantId, entity.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "商品编码已存在");
        }
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null) entity.setStatus("active");
        if (entity.getTaxRate() == null) entity.setTaxRate(new BigDecimal("0.13"));
        if (entity.getUdiRequired() == null) entity.setUdiRequired(true);
        if (entity.getWarnMonths() == null) entity.setWarnMonths(3);
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.ensureAttrs();
        return repository.save(entity);
    }

    @Transactional
    public Product update(Long id, Product patch) {
        Product old = get(id);
        if (patch.getNameCn() != null) old.setNameCn(patch.getNameCn());
        if (patch.getNameEn() != null) old.setNameEn(patch.getNameEn());
        if (patch.getCategoryId() != null) old.setCategoryId(patch.getCategoryId());
        if (patch.getSpec() != null) old.setSpec(patch.getSpec());
        if (patch.getUnit() != null) old.setUnit(patch.getUnit());
        if (patch.getCurrentPrice() != null) old.setCurrentPrice(patch.getCurrentPrice());
        if (patch.getTaxRate() != null) old.setTaxRate(patch.getTaxRate());
        if (patch.getUdiRequired() != null) old.setUdiRequired(patch.getUdiRequired());
        if (patch.getWarnMonths() != null) old.setWarnMonths(patch.getWarnMonths());
        if (patch.getSafetyQty() != null) old.setSafetyQty(patch.getSafetyQty());
        if (patch.getMinOrderQty() != null) old.setMinOrderQty(patch.getMinOrderQty());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        if (patch.getAttrs() != null) old.setAttrs(patch.getAttrs());
        old.setUpdatedAt(OffsetDateTime.now());
        return repository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        Product entity = get(id);
        // US-A-02：引用检查
        var refs = referenceCheckService.productReferences(id);
        long total = referenceCheckService.totalRefs(refs);
        if (total > 0) {
            String desc = referenceCheckService.describe(refs);
            log.warn("停用商品被拒绝: id={} code={} 引用={}", id, entity.getCode(), desc);
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "该商品被以下业务引用，无法停用: " + desc);
        }
        log.info("停用商品: id={} code={}（引用检查通过）", id, entity.getCode());
        entity.setStatus("inactive");
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
    }
}
