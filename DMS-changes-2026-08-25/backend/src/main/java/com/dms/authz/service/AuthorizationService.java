/*
 * 授权业务服务：check(dealerId, authType, atTime, lines[])、CRUD、临时授权。
 * 检查规则：对每一行判定是否存在生效授权（product/terminal 匹配 或 null=通配）。
 */
package com.dms.authz.service;

import com.dms.authz.dto.AuthorizationCheckRequest;
import com.dms.authz.dto.AuthorizationCheckResult;
import com.dms.authz.entity.Authorization;
import com.dms.authz.entity.TempAuthorization;
import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.authz.repository.AuthorizationRepository;
import com.dms.authz.repository.TempAuthorizationRepository;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.SpecUtil;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final AuthorizationRepository authorizationRepository;
    private final TempAuthorizationRepository tempAuthorizationRepository;
    private final ApprovalService approvalService;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public PageResult<Authorization> list(PageQuery pageQuery,
                                          Long id, String code, String dealerName,
                                          String validFrom, String validTo, String status,
                                          String createdAtFrom, String createdAtTo,
                                          String updatedAtFrom, String updatedAtTo) {
        UUID tenantId = TenantContext.getTenantId();
        int pageNumber = Math.max(0, pageQuery.getPage() == null ? 0 : pageQuery.getPage() - 1);
        int pageSize = Math.min(1000, Math.max(1, pageQuery.getSize() == null ? 20 : pageQuery.getSize()));
        StringBuilder where = new StringBuilder("WHERE a.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        int idx = 1;
        if (tenantId != null) {
            where.append(" AND a.tenant_id = ?").append(idx++);
            params.add(tenantId);
        }
        if (id != null) { where.append(" AND a.id = ?").append(idx++); params.add(id); }
        if (code != null && !code.isBlank()) {
            where.append(" AND a.contract_id IN (SELECT c.id FROM contracts c WHERE c.code ILIKE ?")
                 .append(idx++).append(")");
            params.add("%" + code.trim() + "%");
        }
        if (dealerName != null && !dealerName.isBlank()) {
            where.append(" AND a.dealer_id IN (SELECT d.id FROM dealers d WHERE d.name ILIKE ?").append(idx++).append(")");
            params.add("%" + dealerName.trim() + "%");
        }
        if (status != null && !status.isBlank()) { where.append(" AND a.status = ?").append(idx++); params.add(status); }
        if (validFrom != null && !validFrom.isBlank()) {
            try {
                java.sql.Date d = java.sql.Date.valueOf(java.time.LocalDate.parse(validFrom.trim().length() > 10 ? validFrom.trim().substring(0, 10) : validFrom.trim()));
                where.append(" AND a.valid_from >= ?").append(idx++);
                params.add(d);
            } catch (Exception ignored) {}
        }
        if (validTo != null && !validTo.isBlank()) {
            try {
                String s = validTo.trim().length() > 10 ? validTo.trim().substring(0, 10) : validTo.trim();
                java.sql.Date d = java.sql.Date.valueOf(java.time.LocalDate.parse(s).plusDays(1));
                where.append(" AND a.valid_to < ?").append(idx++);
                params.add(d);
            } catch (Exception ignored) {}
        }
        if (createdAtFrom != null && !createdAtFrom.isBlank()) {
            java.sql.Timestamp t = SpecUtil.rangeBound(createdAtFrom, true);
            if (t != null) { where.append(" AND a.created_at >= ?").append(idx++); params.add(t); }
        }
        if (createdAtTo != null && !createdAtTo.isBlank()) {
            java.sql.Timestamp t = SpecUtil.rangeBound(createdAtTo, false);
            if (t != null) { where.append(SpecUtil.hasTime(createdAtTo) ? " AND a.created_at <= ?" : " AND a.created_at < ?").append(idx++); params.add(t); }
        }
        if (updatedAtFrom != null && !updatedAtFrom.isBlank()) {
            java.sql.Timestamp t = SpecUtil.rangeBound(updatedAtFrom, true);
            if (t != null) { where.append(" AND a.updated_at >= ?").append(idx++); params.add(t); }
        }
        if (updatedAtTo != null && !updatedAtTo.isBlank()) {
            java.sql.Timestamp t = SpecUtil.rangeBound(updatedAtTo, false);
            if (t != null) { where.append(SpecUtil.hasTime(updatedAtTo) ? " AND a.updated_at <= ?" : " AND a.updated_at < ?").append(idx++); params.add(t); }
        }

        var cnt = em.createNativeQuery("SELECT COUNT(*) FROM authorizations a " + where);
        for (int i = 0; i < params.size(); i++) cnt.setParameter(i + 1, params.get(i));
        long total = ((Number) cnt.getSingleResult()).longValue();

        String sortExpr = buildAuthSortExpr(pageQuery.getSort());
        var q = em.createNativeQuery("SELECT a.* FROM authorizations a " + where + " ORDER BY " + sortExpr + " LIMIT ?" + idx + " OFFSET ?" + (idx + 1), Authorization.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(idx, pageSize);
        q.setParameter(idx + 1, pageNumber * pageSize);
        @SuppressWarnings("unchecked")
        List<Authorization> rows = q.getResultList();
        rows.forEach(this::fillNames);
        return new PageResult<>(total, pageNumber + 1, pageSize, rows);
    }

    private String buildAuthSortExpr(String sort) {
        String defaultSort = "a.updated_at DESC, a.id DESC";
        if (sort == null || sort.isBlank()) return defaultSort;
        List<String> orders = new ArrayList<>();
        for (String seg : sort.split(";")) {
            String[] parts = seg.split(",");
            if (parts.length == 0 || parts[0].isBlank()) continue;
            String field = parts[0].trim();
            String dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc") ? "ASC" : "DESC";
            switch (field) {
                case "id" -> orders.add("a.id " + dir);
                case "status" -> orders.add("a.status " + dir);
                case "authType" -> orders.add("a.auth_type " + dir);
                case "validFrom" -> orders.add("a.valid_from " + dir);
                case "validTo" -> orders.add("a.valid_to " + dir);
                case "createdAt" -> orders.add("a.created_at " + dir);
                case "updatedAt" -> orders.add("a.updated_at " + dir);
                default -> {}
            }
        }
        return orders.isEmpty() ? defaultSort : String.join(",", orders);
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
        a.setAuthorizedCategories(rowsForCsv("product_categories", a.getCategoryIds()));
        a.setAuthorizedTerminals(rowsForCsv("hospitals", a.getTerminalIds()));
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

    private java.util.List<java.util.Map<String, Object>> rowsForCsv(String table, String csv) {
        java.util.List<java.util.Map<String, Object>> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) return result;
        List<Long> ids = new ArrayList<>();
        for (String s : csv.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) { try { ids.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {} }
        }
        if (ids.isEmpty()) return result;
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                    "SELECT id, name FROM " + table + " WHERE id IN (" +
                    ids.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")").getResultList();
            for (Object[] row : rows) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", row[0]); m.put("name", row[1]);
                result.add(m);
            }
        } catch (Exception e) { return result; }
        return result;
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
        // 新增授权必须经过审批，忽略客户端传入的 status，防止绕过审批直接生效
        req.setStatus("pending_approval");
        if (req.getSource() == null) req.setSource("contract");
        if (req.getAuthType() == null) req.setAuthType("ORDER");
        req.setUpdatedAt(OffsetDateTime.now());
        Authorization saved = authorizationRepository.save(req);
        try {
            StartApprovalRequest request = new StartApprovalRequest();
            request.setBusinessType("AUTHORIZATION");
            request.setBusinessId(saved.getId());
            request.setBusinessCode("AUTH-" + saved.getId());
            request.setTitle("授权审批: AUTH-" + saved.getId());
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("dealerId", saved.getDealerId());
            snapshot.put("authType", saved.getAuthType());
            snapshot.put("productLineId", saved.getProductLineId());
            snapshot.put("terminalId", saved.getTerminalId());
            snapshot.put("validFrom", saved.getValidFrom());
            snapshot.put("validTo", saved.getValidTo());
            request.setBusinessSnapshot(snapshot);
            ApprovalInstance instance = approvalService.start(request);
            if ("APPROVED".equals(instance.getStatus().name()) || "AUTO_APPROVED".equals(instance.getStatus().name())) {
                saved.setStatus("active");
                saved.setUpdatedAt(OffsetDateTime.now());
                saved = authorizationRepository.save(saved);
            }
        } catch (Exception e) {
            saved.setStatus("draft");
            authorizationRepository.save(saved);
            throw e;
        }
        return saved;
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
                            && matchTerminal(a, line.getTerminalId()));
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
        if (csv == null || csv.isBlank()) return false;
        if (terminalId == null) return true;
        for (String s : csv.split(",")) {
            if (s.trim().equals(String.valueOf(terminalId))) return true;
        }
        return false;
    }

    /**
     * 终端匹配：授权未指定终端（terminalId 与 terminalIds 均空）表示通配；
     * 否则当行未带 terminalId 时视为不限制；带值时要求命中单值或 CSV 列表。
     */
    private boolean matchTerminal(Authorization a, Long terminalId) {
        boolean noScope = a.getTerminalId() == null && (a.getTerminalIds() == null || a.getTerminalIds().isBlank());
        if (noScope) return true;
        if (terminalId == null) return true;
        if (a.getTerminalId() != null && a.getTerminalId().equals(terminalId)) return true;
        return matchTerminalCsv(a.getTerminalIds(), terminalId);
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

    @Transactional
    public void delete(Long id) {
        Authorization a = authorizationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "授权不存在"));
        a.setDeletedAt(OffsetDateTime.now());
        authorizationRepository.save(a);
    }
}
