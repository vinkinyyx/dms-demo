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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final AuthorizationRepository authorizationRepository;
    private final TempAuthorizationRepository tempAuthorizationRepository;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public PageResult<Authorization> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<Authorization> page = tenantId == null
                ? authorizationRepository.findAll(pageQuery.toPageable())
                : authorizationRepository.findByTenantId(tenantId, pageQuery.toPageable());
        page.getContent().forEach(this::fillNames);
        return PageResult.of(page);
    }

    @Transactional(readOnly = true)
    public Authorization getDetail(Long id) {
        UUID tenantId = TenantContext.getTenantId();
        Authorization a = authorizationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "授权不存在: " + id));
        if (tenantId != null && !tenantId.equals(a.getTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "授权不存在: " + id);
        }
        fillNames(a);
        return a;
    }

    private void fillNames(Authorization a) {
        if (a == null) return;
        if (a.getDealerId() != null) {
            a.setDealerName(queryName("SELECT name FROM dealers WHERE id = ?1", a.getDealerId()));
        }
        a.setCategoryNames(namesForCsv("product_categories", a.getCategoryIds()));
        a.setTerminalNames(namesForCsv("hospitals", a.getTerminalIds()));
    }

    private String queryName(String sql, Long id) {
        try {
            Object r = em.createNativeQuery(sql).setParameter(1, id).getResultList()
                    .stream().findFirst().orElse(null);
            return r == null ? null : String.valueOf(r);
        } catch (Exception e) { return null; }
    }

    private String namesForCsv(String table, String csv) {
        if (csv == null || csv.isBlank()) return null;
        List<Long> ids = new ArrayList<>();
        for (String s : csv.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) { try { ids.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {} }
        }
        if (ids.isEmpty()) return null;
        try {
            @SuppressWarnings("unchecked")
            List<Object> rows = em.createNativeQuery(
                    "SELECT name FROM " + table + " WHERE id IN (" +
                    ids.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")").getResultList();
            return rows.stream().map(String::valueOf).collect(Collectors.joining("、"));
        } catch (Exception e) { return null; }
    }

    @Transactional
    public Authorization create(Authorization req) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        // 授权业务字段校验：经销商 / 产品分类 / 医院 / 有效期 必填
        if (req.getDealerId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "经销商必填");
        }
        if ((req.getCategoryIds() == null || req.getCategoryIds().isBlank())
                && (req.getProductLines() == null || req.getProductLines().isBlank())
                && req.getProductLineId() == null && req.getProductId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "授权产品分类必填");
        }
        if ((req.getTerminalIds() == null || req.getTerminalIds().isBlank())
                && req.getTerminalId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "授权医院/终端必填");
        }
        if (req.getValidFrom() == null || req.getValidTo() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "有效期开始和结束必填");
        }
        if (req.getValidTo().isBefore(req.getValidFrom())) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "有效期结束不能早于开始");
        }
        req.setId(null);
        req.setTenantId(tenantId);
        if (req.getStatus() == null) req.setStatus("active");
        if (req.getSource() == null) req.setSource("contract");
        if (req.getAuthType() == null) req.setAuthType("ORDER");
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
            Long catId = line.getProductId() != null ? productCategoryId(line.getProductId()) : null;
            boolean matched = active.stream().anyMatch(a ->
                    (matchScope(a.getProductId(), line.getProductId()) || matchCategory(a.getCategoryIds(), catId))
                            && matchScope(a.getTerminalId(), line.getTerminalId())
                            && matchTerminalCsv(a.getTerminalIds(), line.getTerminalId()));
            AuthorizationCheckResult r = new AuthorizationCheckResult();
            r.setProductId(line.getProductId());
            r.setTerminalId(line.getTerminalId());
            r.setAuthorized(matched);
            r.setReason(matched ? "OK" : "无有效授权");
            results.add(r);
        }
        return results;
    }

    private Long productCategoryId(Long productId) {
        try {
            Object r = em.createNativeQuery("SELECT category_id FROM products WHERE id = ?1")
                    .setParameter(1, productId).getResultList().stream().findFirst().orElse(null);
            return r == null ? null : Long.parseLong(String.valueOf(r));
        } catch (Exception e) { return null; }
    }

    private boolean matchCategory(String csv, Long categoryId) {
        if (csv == null || csv.isBlank() || categoryId == null) return false;
        for (String s : csv.split(",")) {
            if (s.trim().equals(String.valueOf(categoryId))) return true;
        }
        return false;
    }

    private boolean matchTerminalCsv(String csv, Long terminalId) {
        if (csv == null || csv.isBlank()) return true;
        if (terminalId == null) return true;
        for (String s : csv.split(",")) {
            if (s.trim().equals(String.valueOf(terminalId))) return true;
        }
        return false;
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
