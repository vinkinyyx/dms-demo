/*
 * RMA 授权服务：新建走商务审批 → 简化直接生效。
 */
package com.dms.rma.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.rma.entity.RmaAuthorization;
import com.dms.rma.repository.RmaAuthorizationRepository;
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
public class RmaAuthorizationService {

    private final RmaAuthorizationRepository repository;
    private final DocNoGenerator docNoGenerator;

    @Transactional(readOnly = true)
    public PageResult<RmaAuthorization> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<RmaAuthorization> page = tenantId == null
                ? repository.findAll(pageQuery.toPageable())
                : repository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public RmaAuthorization get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "RMA 授权不存在"));
    }

    @Transactional
    public RmaAuthorization create(RmaAuthorization req) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        req.setId(null);
        req.setTenantId(tenantId);
        req.setCode(docNoGenerator.next("RMAA"));
        req.setStatus("active");
        req.setCreatedBy(TenantContext.getUserId());
        if (req.getQuotaUsed() == null) req.setQuotaUsed(BigDecimal.ZERO);
        req.setUpdatedAt(OffsetDateTime.now());
        log.info("RMA 授权 {} 简化直接生效（跳过商务审批）", req.getCode());
        return repository.save(req);
    }
}
