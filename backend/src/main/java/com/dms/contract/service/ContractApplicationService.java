/*
 * 合同申请业务服务：新建/提交/审批通过 → 自动生成 Contract。
 */
package com.dms.contract.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.contract.entity.Contract;
import com.dms.contract.entity.ContractApplication;
import com.dms.contract.repository.ContractApplicationRepository;
import com.dms.contract.repository.ContractRepository;
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
public class ContractApplicationService {

    private final ContractApplicationRepository applicationRepository;
    private final ContractRepository contractRepository;
    private final DocNoGenerator docNoGenerator;

    @Transactional(readOnly = true)
    public PageResult<ContractApplication> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<ContractApplication> page = tenantId == null
                ? applicationRepository.findAll(pageQuery.toPageable())
                : applicationRepository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public ContractApplication get(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "合同申请不存在"));
    }

    @Transactional
    public ContractApplication create(ContractApplication req) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        req.setId(null);
        req.setTenantId(tenantId);
        req.setCode(docNoGenerator.next("CT-APP"));
        if (req.getStatus() == null) req.setStatus("draft");
        req.setCreatedBy(TenantContext.getUserId());
        req.setUpdatedAt(OffsetDateTime.now());
        req.ensureMaps();
        return applicationRepository.save(req);
    }

    @Transactional
    public ContractApplication update(Long id, ContractApplication patch) {
        ContractApplication old = get(id);
        if (!"draft".equals(old.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "非草稿状态不可编辑");
        }
        if (patch.getContractCategory() != null) old.setContractCategory(patch.getContractCategory());
        if (patch.getDealerId() != null) old.setDealerId(patch.getDealerId());
        if (patch.getDealerSnapshot() != null) old.setDealerSnapshot(patch.getDealerSnapshot());
        if (patch.getAuthorizationScope() != null) old.setAuthorizationScope(patch.getAuthorizationScope());
        if (patch.getIndicators() != null) old.setIndicators(patch.getIndicators());
        if (patch.getValidFrom() != null) old.setValidFrom(patch.getValidFrom());
        if (patch.getValidTo() != null) old.setValidTo(patch.getValidTo());
        if (patch.getRemark() != null) old.setRemark(patch.getRemark());
        old.setUpdatedAt(OffsetDateTime.now());
        old.setUpdatedBy(TenantContext.getUserId());
        return applicationRepository.save(old);
    }

    @Transactional
    public ContractApplication submit(Long id) {
        ContractApplication app = get(id);
        if (!"draft".equals(app.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "非草稿状态不可提交");
        }
        app.setStatus("submitted");
        app.setSubmittedAt(OffsetDateTime.now());
        app.setUpdatedAt(OffsetDateTime.now());
        return applicationRepository.save(app);
    }

    /**
     * 审批通过：直接把 status 置 effective + 自动生成 Contract。
     */
    @Transactional
    public Contract approve(Long id) {
        ContractApplication app = get(id);
        if (!"submitted".equals(app.getStatus()) && !"draft".equals(app.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "非提交状态不可审批");
        }
        app.setStatus("effective");
        app.setEffectiveAt(OffsetDateTime.now());
        app.setUpdatedAt(OffsetDateTime.now());
        applicationRepository.save(app);

        Contract contract = Contract.builder()
                .tenantId(app.getTenantId())
                .code(docNoGenerator.next("CT"))
                .applicationId(app.getId())
                .dealerId(app.getDealerId())
                .category(app.getContractCategory())
                .validFrom(app.getValidFrom())
                .validTo(app.getValidTo())
                .status("effective")
                .updatedAt(OffsetDateTime.now())
                .build();
        Contract saved = contractRepository.save(contract);
        log.info("合同申请 {} 审批通过，生成合同 {}", app.getCode(), saved.getCode());
        return saved;
    }

    @Transactional
    public void terminate(Long id) {
        ContractApplication app = get(id);
        app.setStatus("terminated");
        app.setUpdatedAt(OffsetDateTime.now());
        applicationRepository.save(app);
    }
}
