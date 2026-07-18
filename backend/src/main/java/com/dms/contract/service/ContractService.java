/*
 * 合同业务服务：列表/详情/终止。
 */
package com.dms.contract.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.contract.entity.Contract;
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
public class ContractService {

    private final ContractRepository repository;

    @Transactional(readOnly = true)
    public PageResult<Contract> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<Contract> page = tenantId == null
                ? repository.findAll(pageQuery.toPageable())
                : repository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public Contract get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "合同不存在"));
    }

    @Transactional
    public void terminate(Long id) {
        Contract contract = get(id);
        contract.setStatus("terminated");
        contract.setUpdatedAt(OffsetDateTime.now());
        repository.save(contract);
        log.info("合同 {} 已终止", contract.getCode());
    }
}
