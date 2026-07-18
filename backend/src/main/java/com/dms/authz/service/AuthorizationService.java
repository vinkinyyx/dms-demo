/*
 * 授权业务服务：check(dealerId, authType, atTime, lines[])、CRUD、临时授权。
 * 检查规则：对每一行判定是否存在生效授权（product/terminal 匹配 或 null=通配）。
 */
package com.dms.authz.service;

import com.dms.authz.dto.AuthorizationCheckRequest;
import com.dms.authz.dto.AuthorizationCheckResult;
import com.dms.authz.entity.Authorization;
import com.dms.authz.entity.TempAuthorization;
import com.dms.authz.repository.AuthorizationRepository;
import com.dms.authz.repository.TempAuthorizationRepository;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final AuthorizationRepository authorizationRepository;
    private final TempAuthorizationRepository tempAuthorizationRepository;

    @Transactional(readOnly = true)
    public PageResult<Authorization> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<Authorization> page = tenantId == null
                ? authorizationRepository.findAll(pageQuery.toPageable())
                : authorizationRepository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional
    public Authorization create(Authorization req) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        req.setId(null);
        req.setTenantId(tenantId);
        if (req.getStatus() == null) req.setStatus("active");
        if (req.getSource() == null) req.setSource("contract");
        req.setUpdatedAt(OffsetDateTime.now());
        return authorizationRepository.save(req);
    }

    /**
     * 授权检查：为每一行订单商品/终端判定是否被覆盖。
     */
    @Transactional(readOnly = true)
    public List<AuthorizationCheckResult> check(AuthorizationCheckRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        LocalDate at = request.getAtTime() != null ? request.getAtTime() : LocalDate.now();
        String authType = request.getAuthType() == null ? "ORDER" : request.getAuthType();

        List<Authorization> active = authorizationRepository.findActive(
                tenantId, request.getDealerId(), authType, at);

        List<AuthorizationCheckResult> results = new ArrayList<>();
        if (request.getLines() == null || request.getLines().isEmpty()) {
            return results;
        }
        for (AuthorizationCheckRequest.Line line : request.getLines()) {
            boolean matched = active.stream().anyMatch(a ->
                    matchScope(a.getProductId(), line.getProductId())
                            && matchScope(a.getTerminalId(), line.getTerminalId()));
            AuthorizationCheckResult r = new AuthorizationCheckResult();
            r.setProductId(line.getProductId());
            r.setTerminalId(line.getTerminalId());
            r.setAuthorized(matched);
            r.setReason(matched ? "OK" : "无有效授权");
            results.add(r);
        }
        return results;
    }

    /**
     * scope 匹配：授权字段为 null 表示通配所有；否则要求相等。
     */
    private boolean matchScope(Long authValue, Long lineValue) {
        if (authValue == null) return true;
        return Objects.equals(authValue, lineValue);
    }

    @Transactional
    public TempAuthorization createTemp(TempAuthorization req) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        req.setId(null);
        req.setTenantId(tenantId);
        if (req.getStatus() == null) req.setStatus("pending");
        req.setApplicantId(TenantContext.getUserId());
        req.setUpdatedAt(OffsetDateTime.now());
        req.ensureScope();
        return tempAuthorizationRepository.save(req);
    }
}
