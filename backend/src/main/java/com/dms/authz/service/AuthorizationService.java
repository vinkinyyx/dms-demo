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
import com.dms.system.service.SystemSettingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
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
    private final SystemSettingService systemSettingService;

    /** 授权创建/续约审批业务类型 */
    public static final String BT_AUTHORIZATION = "AUTHORIZATION";
    /** 授权终止审批业务类型 */
    public static final String BT_AUTHORIZATION_TERMINATE = "AUTHORIZATION_TERMINATE";

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
        a.setProductLineNames(namesForCsv("product_lines", a.getProductLines()));
        a.setAuthorizedProductLines(rowsForCsv("product_lines", a.getProductLines()));
        // 状态中文展示
        a.setStatusLabel(statusLabel(a.getStatus()));
    }

    private String statusLabel(String status) {
        if (status == null) return null;
        return switch (status) {
            case "draft" -> "草稿";
            case "pending_approval" -> "审批中";
            case "terminate_pending" -> "终止审批中";
            case "active" -> "生效中";
            case "not_started" -> "未开始";
            case "expired" -> "已到期";
            case "terminated" -> "已终止";
            case "rejected" -> "已驳回";
            default -> status;
        };
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
    /**
     * 创建授权（厂家授权给经销商）：维度为 经销商 + 产品线(多选) + 终端医院(多选) + 有效期。
     * 必须经过审批；审批通过由回调置为 active/not_started。创建前做跨经销商排他校验。
     */
    @Transactional
    public Authorization create(Authorization req) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (req.getDealerId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "经销商必填");
        }
        normalizeScope(req);
        if (req.getProductLines() == null || req.getProductLines().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "授权产品线必填（至少选择一个产品线）");
        }
        if (req.getTerminalIds() == null || req.getTerminalIds().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "授权终端医院必填（至少选择一家医院）");
        }
        if (req.getValidFrom() == null || req.getValidTo() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "有效期开始和结束必填");
        }
        if (req.getValidTo().isBefore(req.getValidFrom())) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "有效期结束不能早于开始");
        }
        assertNoOverlap(tenantId, req.getDealerId(), req.getProductLines(), req.getTerminalIds(),
                req.getValidFrom(), req.getValidTo(), null);

        req.setId(null);
        req.setTenantId(tenantId);
        // 新增授权必须经过审批，忽略客户端传入的 status，防止绕过审批直接生效
        req.setStatus("pending_approval");
        if (req.getSource() == null) req.setSource("manual");
        if (req.getAuthType() == null) req.setAuthType("ORDER");
        req.setUpdatedAt(OffsetDateTime.now());
        Authorization saved = authorizationRepository.save(req);
        try {
            StartApprovalRequest request = new StartApprovalRequest();
            request.setBusinessType(BT_AUTHORIZATION);
            request.setBusinessId(saved.getId());
            request.setBusinessCode("AUTH-" + saved.getId());
            request.setTitle("授权审批: AUTH-" + saved.getId());
            request.setBusinessSnapshot(buildSnapshot(saved));
            approvalService.start(request);
        } catch (Exception e) {
            saved.setStatus("draft");
            authorizationRepository.save(saved);
            throw e;
        }
        return saved;
    }

    /**
     * 授权续约：基于已有的生效/未开始/已到期授权复制经销商+产品线+终端医院，按新时间段生成新授权并走审批。
     * 同一经销商续约允许时间段相接/重叠（排他校验跳过本经销商）。
     */
    @Transactional
    public Authorization renew(Long id, Authorization req) {
        UUID tenantId = TenantContext.getTenantId();
        Authorization src = getOwned(id, tenantId);
        if (!"active".equals(src.getStatus()) && !"not_started".equals(src.getStatus())
                && !"expired".equals(src.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "仅生效中/未开始/已到期的授权可以续约");
        }
        Authorization copy = new Authorization();
        copy.setDealerId(src.getDealerId());
        copy.setContractId(src.getContractId());
        copy.setAuthType(src.getAuthType());
        copy.setProductLines(src.getProductLines());
        copy.setTerminalIds(src.getTerminalIds());
        copy.setCategoryIds(src.getCategoryIds());
        copy.setSource("renew");
        copy.setRemark(req != null && req.getRemark() != null ? req.getRemark()
                : "续约自授权 AUTH-" + src.getId());
        if (req != null && req.getValidFrom() != null) copy.setValidFrom(req.getValidFrom());
        else copy.setValidFrom(java.time.LocalDate.now());
        if (req != null && req.getValidTo() != null) copy.setValidTo(req.getValidTo());
        else throw new BusinessException(ErrorCode.PARAM_MISSING, "续约必须指定新的有效期结束时间");
        if (copy.getValidTo().isBefore(copy.getValidFrom())) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "续约有效期结束不能早于开始");
        }
        return create(copy);
    }

    /**
     * 授权终止：对生效中(active)/未开始(not_started)的授权发起终止审批；通过后由回调置 terminated。
     */
    @Transactional
    public Authorization terminate(Long id, String reason) {
        UUID tenantId = TenantContext.getTenantId();
        Authorization a = getOwned(id, tenantId);
        if (!"active".equals(a.getStatus()) && !"not_started".equals(a.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "仅生效中或未开始的授权可以终止");
        }
        String prevStatus = a.getStatus();
        a.setStatus("terminate_pending");
        a.setUpdatedAt(OffsetDateTime.now());
        a = authorizationRepository.save(a);
        try {
            StartApprovalRequest request = new StartApprovalRequest();
            request.setBusinessType(BT_AUTHORIZATION_TERMINATE);
            request.setBusinessId(a.getId());
            request.setBusinessCode("AUTH-TERM-" + a.getId());
            request.setTitle("授权终止审批: AUTH-" + a.getId());
            Map<String, Object> snapshot = buildSnapshot(a);
            snapshot.put("prevStatus", prevStatus);
            snapshot.put("terminateReason", reason);
            request.setBusinessSnapshot(snapshot);
            approvalService.start(request);
        } catch (Exception e) {
            a.setStatus(prevStatus);
            authorizationRepository.save(a);
            throw e;
        }
        return a;
    }

    /** 终端医院选项：可按区域子树(省/市)过滤 + 关键字，返回 id/name/region 等，供授权批量选择 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTerminals(Long regionId, String keyword) {
        UUID tenantId = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "SELECT h.id, h.name, h.code, h.region_id, r.name AS region_name " +
                "FROM hospitals h LEFT JOIN regions r ON r.id = h.region_id " +
                "WHERE h.deleted_at IS NULL ");
        List<Object> params = new ArrayList<>();
        int idx = 1;
        if (tenantId != null) { sql.append(" AND h.tenant_id = ?").append(idx++); params.add(tenantId); }
        if (regionId != null) {
            sql.append(" AND h.region_id IN (WITH RECURSIVE sub AS (")
               .append(" SELECT id FROM regions WHERE id = ?").append(idx++)
               .append(" UNION ALL SELECT c.id FROM regions c JOIN sub ON c.parent_id = sub.id)")
               .append(" SELECT id FROM sub)");
            params.add(regionId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND h.name ILIKE ?").append(idx++);
            params.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY h.id LIMIT 500");
        var q = em.createNativeQuery(sql.toString(), Tuple.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.get("id", Long.class) == null ? null : ((Number) t.get("id")).longValue());
            m.put("name", t.get("name", String.class));
            m.put("code", t.get("code", String.class));
            m.put("regionId", t.get("region_id") == null ? null : ((Number) t.get("region_id")).longValue());
            m.put("regionName", t.get("region_name", String.class));
            list.add(m);
        }
        return list;
    }

    /** 产品线选项：启用状态的产品线 id/name/code */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listProductLines() {
        UUID tenantId = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT id, code, name FROM product_lines WHERE deleted_at IS NULL " +
                "AND (status IS NULL OR status = 'active') " +
                (tenantId != null ? "AND tenant_id = ?1 " : "") +
                "ORDER BY sort_order NULLS LAST, id", Tuple.class);
        if (tenantId != null) q.setParameter(1, tenantId);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ((Number) t.get("id")).longValue());
            m.put("code", t.get("code", String.class));
            m.put("name", t.get("name", String.class));
            list.add(m);
        }
        return list;
    }

    /** 授权-下单挂钩开关（当前租户） */
    @Transactional(readOnly = true)
    public boolean isOrderAuthzEnforced() {
        return systemSettingService.isOrderAuthzEnforced();
    }

    /** 更新授权-下单挂钩开关（当前租户） */
    @Transactional
    public void setOrderAuthzEnforced(boolean enabled) {
        systemSettingService.setOrderAuthzEnforced(enabled);
    }

    /** 产品线/终端字段归一到 CSV 字段（兼容单值 productLineId/terminalId） */
    private void normalizeScope(Authorization a) {
        if ((a.getProductLines() == null || a.getProductLines().isBlank()) && a.getProductLineId() != null) {
            a.setProductLines(String.valueOf(a.getProductLineId()));
        }
        if ((a.getTerminalIds() == null || a.getTerminalIds().isBlank()) && a.getTerminalId() != null) {
            a.setTerminalIds(String.valueOf(a.getTerminalId()));
        }
    }

    private Map<String, Object> buildSnapshot(Authorization a) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("dealerId", a.getDealerId());
        snapshot.put("authType", a.getAuthType());
        snapshot.put("productLines", a.getProductLines());
        snapshot.put("terminalIds", a.getTerminalIds());
        snapshot.put("validFrom", a.getValidFrom());
        snapshot.put("validTo", a.getValidTo());
        return snapshot;
    }

    private Authorization getOwned(Long id, UUID tenantId) {
        Authorization a = authorizationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "授权不存在: " + id));
        if (tenantId != null && !tenantId.equals(a.getTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "授权不存在: " + id);
        }
        return a;
    }

    /**
     * 排他校验：同一租户内，时间段重叠、产品线集合与终端医院集合均有交集，
     * 且授权给「其他经销商」的、状态为 pending_approval/active/not_started 的授权视为冲突。
     */
    private void assertNoOverlap(UUID tenantId, Long dealerId, String productLinesCsv,
                                 String terminalIdsCsv, LocalDate validFrom, LocalDate validTo, Long excludeId) {
        List<Long> lineIds = parseIdList(productLinesCsv);
        List<Long> termIds = parseIdList(terminalIdsCsv);
        if (lineIds.isEmpty() || termIds.isEmpty()) return;
        List<Authorization> blockers = authorizationRepository.findOverlapCandidates(
                tenantId, validFrom, validTo,
                java.util.List.of("pending_approval", "active", "not_started"));
        for (Authorization b : blockers) {
            if (excludeId != null && excludeId.equals(b.getId())) continue;
            if (b.getDealerId() != null && b.getDealerId().equals(dealerId)) continue;
            if (!intersects(lineIds, parseIdList(b.getProductLines()))) continue;
            if (!intersects(termIds, parseIdList(b.getTerminalIds()))) continue;
            String dealerName = queryName("SELECT name FROM dealers WHERE id = ?1", b.getDealerId());
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "授权冲突：所选部分终端医院在 " + b.getValidFrom() + "~" + b.getValidTo()
                            + " 内已授权给经销商 [" + (dealerName != null ? dealerName : b.getDealerId())
                            + "]，同一医院同一产品线在同一时间段不能重复授权给不同经销商");
        }
    }

    private List<Long> parseIdList(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.isBlank()) return ids;
        for (String s : csv.split("[,，]")) {
            s = s.trim();
            if (!s.isEmpty()) { try { ids.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {} }
        }
        return ids;
    }

    private boolean intersects(List<Long> a, List<Long> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        for (Long x : a) if (b.contains(x)) return true;
        return false;
    }

    /**
     * 授权检查：为每一行订单商品/终端判定是否被覆盖。
     */
    /**
     * 授权检查：为每一行订单商品/终端判定是否被覆盖。
     * 维度：订单产品的 product_line_id 命中授权 product_lines；终端医院命中 terminal_ids。
     * 若租户关闭「授权-下单挂钩」开关，则直接全部放行（授权与下单解耦）。
     */
    @Transactional(readOnly = true)
    public List<AuthorizationCheckResult> check(AuthorizationCheckRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        List<AuthorizationCheckResult> results = new ArrayList<>();
        if (request.getLines() == null || request.getLines().isEmpty()) {
            return results;
        }
        // 开关关闭：授权与下单无关，直接放行
        if (!systemSettingService.isOrderAuthzEnforced(tenantId)) {
            for (AuthorizationCheckRequest.Line line : request.getLines()) {
                results.add(new AuthorizationCheckResult(line.getProductId(), line.getTerminalId(),
                        true, "授权校验未启用"));
            }
            return results;
        }

        LocalDate at = request.getAtTime() != null ? request.getAtTime() : LocalDate.now();
        String authType = request.getAuthType() == null ? "ORDER" : request.getAuthType();
        List<Authorization> active = authorizationRepository.findActive(
                tenantId, request.getDealerId(), authType, at);

        boolean hospitalScope = "SALES_TO_HOSPITAL".equalsIgnoreCase(authType);
        for (AuthorizationCheckRequest.Line line : request.getLines()) {
            Long productLineId = line.getProductLineId() != null ? line.getProductLineId()
                    : productLineId(line.getProductId());
            // 必须存在「同一条」授权同时覆盖产品线与终端医院，避免跨授权交叉放行
            boolean matched = active.stream().anyMatch(a -> {
                if (!matchProductLine(a, productLineId)) return false;
                if (hospitalScope) return matchTerminal(a, line.getTerminalId());
                return true; // ORDER 下单时终端未定，只校验产品线
            });

            String reason;
            if (matched) {
                reason = "OK";
            } else {
                boolean lineMatched = active.stream().anyMatch(a -> matchProductLine(a, productLineId));
                reason = lineMatched ? "终端医院未在授权范围" : "产品所属产品线未授权";
            }
            AuthorizationCheckResult r = new AuthorizationCheckResult();
            r.setProductId(line.getProductId());
            r.setTerminalId(line.getTerminalId());
            r.setAuthorized(matched);
            r.setReason(reason);
            results.add(r);
        }
        return results;
    }

    /** 产品线匹配：授权 product_lines(CSV) 含该行产品线，或授权未限定产品线（通配） */
    private boolean matchProductLine(Authorization a, Long productLineId) {
        List<Long> lines = parseIdList(a.getProductLines());
        if (lines.isEmpty()) return true; // 未限定产品线=通配
        return productLineId != null && lines.contains(productLineId);
    }


    private Long productLineId(Long productId) {
        if (productId == null) return null;
        try {
            Object r = em.createNativeQuery("SELECT product_line_id FROM products WHERE id = ?1")
                    .setParameter(1, productId).getResultList().stream().findFirst().orElse(null);
            return r == null ? null : Long.parseLong(String.valueOf(r));
        } catch (Exception e) { return null; }
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
        Authorization a = getOwned(id, TenantContext.getTenantId());
        if (!"draft".equals(a.getStatus()) && !"rejected".equals(a.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "仅草稿或已驳回的授权可以删除");
        }
        a.setDeletedAt(OffsetDateTime.now());
        authorizationRepository.save(a);
    }
}