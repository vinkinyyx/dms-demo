/*
 * 促销业务服务：CRUD + V1 promoType 白名单校验。
 */
package com.dms.promotion.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.promotion.entity.Promotion;
import com.dms.promotion.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {

    /** V1 支持的促销类型 */
    private static final Set<String> V1_ALLOWED_TYPES = Set.of("MOQ", "FULL_REDUCTION");
    private static final Set<String> V1_NOT_YET_TYPES = Set.of("GIFT", "BUNDLE");

    private final PromotionRepository repository;

    @Transactional(readOnly = true)
    public PageResult<Promotion> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<Promotion> page = tenantId == null
                ? repository.findAll(pageQuery.toPageable())
                : repository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public Promotion get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "促销不存在"));
    }

    @Transactional
    public Promotion create(Promotion req) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        // V1 promoType 白名单校验
        String type = req.getPromoType();
        if (type == null || (!V1_ALLOWED_TYPES.contains(type) && !V1_NOT_YET_TYPES.contains(type))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "非法的 promoType");
        }
        if (V1_NOT_YET_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "V1 未启用该促销类型");
        }
        req.setId(null);
        req.setTenantId(tenantId);
        if (req.getStatus() == null) req.setStatus("draft");
        if (req.getPriority() == null) req.setPriority(50);
        if (req.getExclusive() == null) req.setExclusive(false);
        req.setCreatedBy(TenantContext.getUserId());
        req.setUpdatedAt(OffsetDateTime.now());
        req.ensureMaps();
        return repository.save(req);
    }

    @Transactional
    public Promotion update(Long id, Promotion patch) {
        Promotion old = get(id);
        if (patch.getName() != null) old.setName(patch.getName());
        if (patch.getPriority() != null) old.setPriority(patch.getPriority());
        if (patch.getValidFrom() != null) old.setValidFrom(patch.getValidFrom());
        if (patch.getValidTo() != null) old.setValidTo(patch.getValidTo());
        if (patch.getDealerScope() != null) old.setDealerScope(patch.getDealerScope());
        if (patch.getProductScope() != null) old.setProductScope(patch.getProductScope());
        if (patch.getExclusive() != null) old.setExclusive(patch.getExclusive());
        if (patch.getDescription() != null) old.setDescription(patch.getDescription());
        if (patch.getStatus() != null) old.setStatus(patch.getStatus());
        old.setUpdatedAt(OffsetDateTime.now());
        old.setUpdatedBy(TenantContext.getUserId());
        return repository.save(old);
    }

    @Transactional
    public void deactivate(Long id) {
        Promotion p = get(id);
        p.setStatus("inactive");
        p.setUpdatedAt(OffsetDateTime.now());
        repository.save(p);
        log.info("促销 {} 已停用", p.getCode());
    }
}
