/*
 * 客户代金券业务服务：发放、可用查询、下单核销、作废返还、禁用/作废。
 * 券抵扣不摊入单价，本服务只维护券状态机，计价由 order 模块负责。
 */
package com.dms.voucher.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.voucher.dto.VoucherAcquireRequest;
import com.dms.voucher.dto.VoucherBatchIssueRequest;
import com.dms.voucher.dto.VoucherDTO;
import com.dms.voucher.entity.CustomerVoucher;
import com.dms.voucher.entity.CustomerVoucherUsage;
import com.dms.voucher.repository.CustomerVoucherRepository;
import com.dms.voucher.repository.CustomerVoucherUsageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerVoucherService {

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_PRODUCT = "PRODUCT";
    public static final String SCOPE_CATEGORY = "CATEGORY";

    public static final String STATUS_ISSUED = "ISSUED";
    public static final String STATUS_USED = "USED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_VOID = "VOID";

    public static final String USAGE_USED = "USED";
    public static final String USAGE_REVERSED = "REVERSED";

    private final CustomerVoucherRepository voucherRepository;
    private final CustomerVoucherUsageRepository usageRepository;
    private final EntityManager em;

    private UUID requireTenant() {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未识别租户上下文");
        }
        return tid;
    }

    private CustomerVoucher loadVoucher(UUID tid, Long id) {
        return voucherRepository.findByIdAndTenantId(id, tid)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "代金券不存在"));
    }

    // ==================== 厂家发放 ====================

    @Transactional
    public List<VoucherDTO> batchIssue(VoucherBatchIssueRequest req) {
        UUID tid = requireTenant();
        Long operatorId = TenantContext.getUserId();
        validateIssueRequest(req);

        String scopeType = req.getScopeType() == null || req.getScopeType().isBlank()
                ? SCOPE_ALL : req.getScopeType().trim().toUpperCase();
        if (!Set.of(SCOPE_ALL, SCOPE_PRODUCT, SCOPE_CATEGORY).contains(scopeType)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "券范围类型非法，应为 ALL/PRODUCT/CATEGORY");
        }
        if ((SCOPE_PRODUCT.equals(scopeType) || SCOPE_CATEGORY.equals(scopeType))
                && (req.getScopeRefs() == null || req.getScopeRefs().isEmpty())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "指定产品/品类范围时必须提供 scopeRefs");
        }

        List<Long> dealerIds = resolveTargetDealers(tid, req.getDealerIds(), req.getDealerLevel());
        if (dealerIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "未匹配到任何发放对象（经销商）");
        }

        String batchNo = "VB-" + System.currentTimeMillis();
        OffsetDateTime now = OffsetDateTime.now();
        List<VoucherDTO> created = new ArrayList<>();
        for (Long dealerId : dealerIds) {
            CustomerVoucher v = CustomerVoucher.builder()
                    .tenantId(tid)
                    .code(generateUniqueCode(tid))
                    .name(req.getName().trim())
                    .dealerId(dealerId)
                    .faceValue(req.getFaceValue())
                    .minSpend(req.getMinSpend() == null ? BigDecimal.ZERO : req.getMinSpend())
                    .scopeType(scopeType)
                    .scopeRefs(SCOPE_ALL.equals(scopeType) ? new ArrayList<>() : new ArrayList<>(req.getScopeRefs()))
                    .validFrom(req.getValidFrom())
                    .validTo(req.getValidTo())
                    .status(STATUS_ISSUED)
                    .batchNo(batchNo)
                    .remark(req.getRemark())
                    .createdBy(operatorId)
                    .updatedBy(operatorId)
                    .updatedAt(now)
                    .build();
            v.ensureScopeRefs();
            created.add(VoucherDTO.of(voucherRepository.save(v)));
        }
        log.info("代金券批量发放 batchNo={} 数量={} dealerCount={}", batchNo, created.size(), dealerIds.size());
        return created;
    }

    private void validateIssueRequest(VoucherBatchIssueRequest req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "请求体不能为空");
        }
        if (req.getFaceValue() == null || req.getFaceValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "代金券面值必须大于 0");
        }
        if (req.getValidFrom() != null && req.getValidTo() != null
                && req.getValidTo().isBefore(req.getValidFrom())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "有效期结束时间不能早于开始时间");
        }
    }

    /** 汇总显式 dealerIds 与按等级命中的经销商，去重。 */
    private List<Long> resolveTargetDealers(UUID tid, List<Long> explicit, String level) {
        Set<Long> ids = new HashSet<>();
        if (explicit != null) {
            explicit.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (level != null && !level.isBlank()) {
            var q = em.createNativeQuery(
                    "SELECT id FROM dealers WHERE tenant_id = ?1 AND deleted_at IS NULL AND level = ?2");
            q.setParameter(1, tid).setParameter(2, level.trim());
            @SuppressWarnings("unchecked")
            List<Number> rows = q.getResultList();
            for (Number n : rows) {
                ids.add(n.longValue());
            }
        }
        return new ArrayList<>(ids);
    }

    private String generateUniqueCode(UUID tid) {
        for (int i = 0; i < 20; i++) {
            String raw = Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()), 36).toUpperCase();
            String code = ("VC" + raw).substring(0, Math.min(10, raw.length() + 2));
            if (!voucherRepository.existsByTenantIdAndCode(tid, code)) {
                return code;
            }
        }
        return "VC" + System.nanoTime();
    }

    // ==================== 可用券查询 ====================

    @Transactional(readOnly = true)
    public List<VoucherDTO> available(Long dealerId, BigDecimal amount, List<Long> productIds) {
        UUID tid = requireTenant();
        if (dealerId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "dealerId 不能为空");
        }
        List<CustomerVoucher> list =
                voucherRepository.findByTenantIdAndDealerIdAndStatus(tid, dealerId, STATUS_ISSUED);
        OffsetDateTime now = OffsetDateTime.now();
        Set<Long> productCategories = loadProductCategories(tid, productIds);
        List<VoucherDTO> result = new ArrayList<>();
        for (CustomerVoucher v : list) {
            if (!withinValidity(v, now)) continue;
            if (amount != null && v.getMinSpend() != null && amount.compareTo(v.getMinSpend()) < 0) {
                continue;
            }
            if (!scopeMatches(v, productIds, productCategories)) continue;
            result.add(VoucherDTO.of(v));
        }
        return result;
    }

    private boolean withinValidity(CustomerVoucher v, OffsetDateTime now) {
        if (v.getValidFrom() != null && now.isBefore(v.getValidFrom())) return false;
        if (v.getValidTo() != null && now.isAfter(v.getValidTo())) return false;
        return true;
    }

    private boolean scopeMatches(CustomerVoucher v, List<Long> productIds, Set<Long> productCategories) {
        if (v.getScopeType() == null || SCOPE_ALL.equals(v.getScopeType())) return true;
        Set<Long> refs = extractRefIds(v.getScopeRefs());
        if (refs.isEmpty()) return true;
        if (SCOPE_PRODUCT.equals(v.getScopeType())) {
            if (productIds == null) return false;
            for (Long pid : productIds) {
                if (refs.contains(pid)) return true;
            }
            return false;
        }
        if (SCOPE_CATEGORY.equals(v.getScopeType())) {
            for (Long catId : productCategories) {
                if (refs.contains(catId)) return true;
            }
            return false;
        }
        return true;
    }

    private Set<Long> extractRefIds(List<Map<String, Object>> refs) {
        Set<Long> ids = new HashSet<>();
        if (refs == null) return ids;
        for (Map<String, Object> ref : refs) {
            Object id = ref.get("id");
            if (id instanceof Number n) ids.add(n.longValue());
        }
        return ids;
    }

    private Set<Long> loadProductCategories(UUID tid, List<Long> productIds) {
        Set<Long> cats = new HashSet<>();
        if (productIds == null || productIds.isEmpty()) return cats;
        var q = em.createNativeQuery(
                "SELECT DISTINCT category_id FROM products WHERE tenant_id = ?1 AND deleted_at IS NULL AND id = ANY(?2)");
        q.setParameter(1, tid).setParameter(2, productIds.toArray(new Long[0]));
        @SuppressWarnings("unchecked")
        List<Object> rows = q.getResultList();
        for (Object r : rows) if (r instanceof Number n) cats.add(n.longValue());
        return cats;
    }

    // ==================== 核销 / 返还（供 order 模块调用） ====================

    /**
     * 下单占用并核销券（一单一张）。校验通过后券置 USED 并写 usage。供 order 模块调用。
     */
    @Transactional
    public CustomerVoucherUsage acquire(VoucherAcquireRequest req) {
        UUID tid = requireTenant();
        CustomerVoucher v = loadVoucher(tid, req.getVoucherId());
        OffsetDateTime now = OffsetDateTime.now();

        if (!STATUS_ISSUED.equals(v.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "代金券当前状态为 " + v.getStatus() + "，不可使用");
        }
        if (!withinValidity(v, now)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "代金券不在有效期内");
        }
        BigDecimal usedAmount = req.getUsedAmount() == null ? v.getFaceValue() : req.getUsedAmount();
        if (usedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "代金券抵扣金额必须大于 0");
        }
        if (usedAmount.compareTo(v.getFaceValue()) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "代金券抵扣金额 " + usedAmount + " 超过面值 " + v.getFaceValue());
        }
        if (v.getMinSpend() != null && req.getOrderOriginalAmount() != null
                && req.getOrderOriginalAmount().compareTo(v.getMinSpend()) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "订单原价 " + req.getOrderOriginalAmount() + " 未达到代金券最低消费 " + v.getMinSpend());
        }
        long openUsage = usageRepository.countByTenantIdAndVoucherIdAndStatus(tid, v.getId(), USAGE_USED);
        if (openUsage > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "该代金券已被使用，一单仅限一张");
        }

        v.setStatus(STATUS_USED);
        v.setUpdatedAt(now);
        v.setUpdatedBy(TenantContext.getUserId());
        voucherRepository.save(v);

        CustomerVoucherUsage usage = CustomerVoucherUsage.builder()
                .tenantId(tid)
                .voucherId(v.getId())
                .orderId(req.getOrderId())
                .orderCode(req.getOrderCode())
                .usedAmount(usedAmount)
                .status(USAGE_USED)
                .usedAt(now)
                .build();
        return usageRepository.save(usage);
    }

    /**
     * 整单未出库作废时返还券：usage 置 REVERSED，券恢复 ISSUED。
     * 供 order 模块在订单作废/驳回时调用。部分退货/已出库不退券。
     */
    @Transactional
    public void release(Long orderId) {
        UUID tid = requireTenant();
        if (orderId == null) return;
        List<CustomerVoucherUsage> usages = usageRepository.findByOrderId(orderId);
        OffsetDateTime now = OffsetDateTime.now();
        for (CustomerVoucherUsage usage : usages) {
            if (!USAGE_USED.equals(usage.getStatus())) continue;
            if (!tid.equals(usage.getTenantId())) continue;
            usage.setStatus(USAGE_REVERSED);
            usageRepository.save(usage);
            voucherRepository.findByIdAndTenantId(usage.getVoucherId(), tid).ifPresent(v -> {
                if (STATUS_USED.equals(v.getStatus())) {
                    v.setStatus(STATUS_ISSUED);
                    v.setUpdatedAt(now);
                    v.setUpdatedBy(TenantContext.getUserId());
                    voucherRepository.save(v);
                }
            });
            log.info("代金券返还 voucherId={} orderId={}", usage.getVoucherId(), orderId);
        }
    }

    /**
     * 下单前预校验（不改变状态）。可用返回 null，不可用返回中文原因。供 order 模块提交前调用。
     */
    @Transactional(readOnly = true)
    public String validateUsable(Long voucherId, Long dealerId, BigDecimal orderOriginalAmount) {
        UUID tid = requireTenant();
        CustomerVoucher v = voucherRepository.findByIdAndTenantId(voucherId, tid).orElse(null);
        if (v == null) return "代金券不存在";
        if (dealerId != null && v.getDealerId() != null && !dealerId.equals(v.getDealerId())) {
            return "代金券不属于该客户";
        }
        if (!STATUS_ISSUED.equals(v.getStatus())) return "代金券当前状态为 " + v.getStatus() + "，不可使用";
        if (!withinValidity(v, OffsetDateTime.now())) return "代金券不在有效期内";
        if (v.getMinSpend() != null && orderOriginalAmount != null
                && orderOriginalAmount.compareTo(v.getMinSpend()) < 0) {
            return "订单原价未达到代金券最低消费 " + v.getMinSpend();
        }
        long openUsage = usageRepository.countByTenantIdAndVoucherIdAndStatus(tid, v.getId(), USAGE_USED);
        if (openUsage > 0) return "该代金券已被使用";
        return null;
    }

    // ==================== 管理端：查询 / 禁用 / 启用 / 作废 ====================

    @Transactional(readOnly = true)
    public PageResult<VoucherDTO> list(PageQuery pageQuery, Long dealerId, String status, String keyword) {
        UUID tid = requireTenant();
        int pageNumber = Math.max(0, pageQuery.getPage() == null ? 0 : pageQuery.getPage() - 1);
        int pageSize = Math.min(200, Math.max(1, pageQuery.getSize() == null ? 20 : pageQuery.getSize()));
        StringBuilder where = new StringBuilder("WHERE cv.tenant_id = :tid AND cv.deleted_at IS NULL");
        if (dealerId != null) where.append(" AND cv.dealer_id = :dealerId");
        if (status != null && !status.isBlank()) where.append(" AND cv.status = :status");
        if (keyword != null && !keyword.isBlank()) where.append(" AND (cv.code ILIKE :kw OR cv.name ILIKE :kw OR cv.batch_no ILIKE :kw)");

        String countSql = "SELECT COUNT(1) FROM customer_vouchers cv " + where;
        var countQ = em.createNativeQuery(countSql);
        countQ.setParameter("tid", tid);
        if (dealerId != null) countQ.setParameter("dealerId", dealerId);
        if (status != null && !status.isBlank()) countQ.setParameter("status", status.trim());
        if (keyword != null && !keyword.isBlank()) countQ.setParameter("kw", "%" + keyword.trim() + "%");
        long total = ((Number) countQ.getSingleResult()).longValue();

        var listQ = em.createNativeQuery(
                "SELECT cv.id, d.name AS dealer_name FROM customer_vouchers cv " +
                "LEFT JOIN dealers d ON d.id = cv.dealer_id " +
                where + " ORDER BY cv.id DESC", Tuple.class);
        listQ.setParameter("tid", tid);
        if (dealerId != null) listQ.setParameter("dealerId", dealerId);
        if (status != null && !status.isBlank()) listQ.setParameter("status", status.trim());
        if (keyword != null && !keyword.isBlank()) listQ.setParameter("kw", "%" + keyword.trim() + "%");
        listQ.setFirstResult(pageNumber * pageSize);
        listQ.setMaxResults(pageSize);
        List<VoucherDTO> rows = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Tuple> tuples = listQ.getResultList();
        for (Tuple t : tuples) {
            Long id = ((Number) t.get("id")).longValue();
            voucherRepository.findByIdAndTenantId(id.longValue(), tid)
                    .ifPresent(v -> {
                        VoucherDTO dto = VoucherDTO.of(v);
                        dto.setDealerName(t.get("dealer_name") == null ? null : String.valueOf(t.get("dealer_name")));
                        rows.add(dto);
                    });
        }
        return new PageResult<>(total, pageNumber + 1, pageSize, rows);
    }

    @Transactional(readOnly = true)
    public VoucherDTO detail(Long id) {
        return VoucherDTO.of(loadVoucher(requireTenant(), id));
    }

    @Transactional
    public void disable(Long id) {
        UUID tid = requireTenant();
        CustomerVoucher v = loadVoucher(tid, id);
        if (STATUS_USED.equals(v.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已核销的代金券不可禁用");
        }
        v.setStatus(STATUS_DISABLED);
        v.setUpdatedAt(OffsetDateTime.now());
        v.setUpdatedBy(TenantContext.getUserId());
        voucherRepository.save(v);
    }

    @Transactional
    public void enable(Long id) {
        UUID tid = requireTenant();
        CustomerVoucher v = loadVoucher(tid, id);
        if (!STATUS_DISABLED.equals(v.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "仅禁用状态的代金券可重新启用");
        }
        v.setStatus(STATUS_ISSUED);
        v.setUpdatedAt(OffsetDateTime.now());
        v.setUpdatedBy(TenantContext.getUserId());
        voucherRepository.save(v);
    }

    @Transactional
    public void voidVoucher(Long id) {
        UUID tid = requireTenant();
        CustomerVoucher v = loadVoucher(tid, id);
        if (STATUS_USED.equals(v.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已核销的代金券不可作废");
        }
        v.setStatus(STATUS_VOID);
        v.setUpdatedAt(OffsetDateTime.now());
        v.setUpdatedBy(TenantContext.getUserId());
        voucherRepository.save(v);
    }
}

