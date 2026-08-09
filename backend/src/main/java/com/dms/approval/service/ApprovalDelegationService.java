package com.dms.approval.service;

import com.dms.approval.dto.DelegationRequest;
import com.dms.approval.entity.ApprovalDelegation;
import com.dms.approval.repository.ApprovalDelegationRepository;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalDelegationService {
    private final ApprovalDelegationRepository delegationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResult<ApprovalDelegation> list(PageQuery pageQuery) {
        UUID tenantId = requireTenantId();
        return PageResult.of(delegationRepository.findAll(
                PageRequest.of(pageQuery.getPage() - 1, pageQuery.getSize(), Sort.by(Sort.Direction.DESC, "id")))
                .map(d -> d.getTenantId().equals(tenantId) ? d : null));
    }

    @Transactional
    public ApprovalDelegation create(DelegationRequest request) {
        UUID tenantId = requireTenantId();
        if (request.getDelegatorId() == null || request.getDelegateeId() == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "委托人和被委托人不能为空");
        if (request.getStartsAt() == null || request.getEndsAt() == null || !request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "委托生效时间不正确");
        }
        userRepository.findById(request.getDelegatorId()).filter(u -> tenantId.equals(u.getTenantId())).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "委托人不存在"));
        userRepository.findById(request.getDelegateeId()).filter(u -> tenantId.equals(u.getTenantId())).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "被委托人不存在"));
        return delegationRepository.save(ApprovalDelegation.builder()
                .tenantId(tenantId).delegatorId(request.getDelegatorId()).delegateeId(request.getDelegateeId())
                .startsAt(request.getStartsAt()).endsAt(request.getEndsAt()).status("ACTIVE").reason(request.getReason())
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build());
    }

    @Transactional
    public ApprovalDelegation disable(Long id) {
        ApprovalDelegation delegation = getTenantDelegation(id);
        delegation.setStatus("DISABLED");
        delegation.setUpdatedAt(OffsetDateTime.now());
        return delegationRepository.save(delegation);
    }

    private ApprovalDelegation getTenantDelegation(Long id) {
        ApprovalDelegation delegation = delegationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "委托配置不存在"));
        if (!requireTenantId().equals(delegation.getTenantId())) throw new BusinessException(ErrorCode.FORBIDDEN, "不能操作其他租户的委托配置");
        return delegation;
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少租户上下文");
        return tenantId;
    }
}
