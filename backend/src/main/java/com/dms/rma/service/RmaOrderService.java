/*
 * RMA 订单服务：
 *   create → 引用 RMA 授权 → quota_used + amount <= quota_amount → 提交
 *   complete → 释放/占用配额
 */
package com.dms.rma.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.rma.entity.RmaAuthorization;
import com.dms.rma.entity.RmaOrder;
import com.dms.rma.repository.RmaAuthorizationRepository;
import com.dms.rma.repository.RmaOrderRepository;
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
public class RmaOrderService {

    private final RmaOrderRepository rmaOrderRepository;
    private final RmaAuthorizationRepository authRepository;
    private final DocNoGenerator docNoGenerator;

    @Transactional(readOnly = true)
    public PageResult<RmaOrder> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<RmaOrder> page = tenantId == null
                ? rmaOrderRepository.findAll(pageQuery.toPageable())
                : rmaOrderRepository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    @Transactional
    public RmaOrder create(RmaOrder req) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (req.getRefRmaAuthId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 refRmaAuthId");
        }
        if (req.getAmount() == null || req.getAmount().signum() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "amount 必须 > 0");
        }

        // 锁定授权 → 校验配额 → 占用
        RmaAuthorization auth = authRepository.lockById(req.getRefRmaAuthId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "RMA 授权不存在"));
        if (!"active".equals(auth.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "RMA 授权未生效");
        }
        BigDecimal used = auth.getQuotaUsed() == null ? BigDecimal.ZERO : auth.getQuotaUsed();
        BigDecimal quota = auth.getQuotaAmount() == null ? BigDecimal.ZERO : auth.getQuotaAmount();
        if (used.add(req.getAmount()).compareTo(quota) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "RMA 授权配额不足: 已用=" + used + " 申请=" + req.getAmount() + " 总额=" + quota);
        }
        auth.setQuotaUsed(used.add(req.getAmount()));
        auth.setUpdatedAt(OffsetDateTime.now());
        authRepository.save(auth);

        req.setId(null);
        req.setTenantId(tenantId);
        req.setCode(docNoGenerator.next("RMA"));
        req.setStatus("SUBMITTED");
        req.setSubmittedAt(OffsetDateTime.now());
        req.setCreatedBy(TenantContext.getUserId());
        req.setUpdatedAt(OffsetDateTime.now());
        req.ensureJson();
        return rmaOrderRepository.save(req);
    }

    /**
     * 完成 RMA 订单：占用配额已在 create 时执行；此处仅更新状态。
     */
    @Transactional
    public RmaOrder complete(Long id) {
        RmaOrder order = rmaOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "RMA 订单不存在"));
        if ("COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "订单已完成");
        }
        order.setStatus("COMPLETED");
        order.setCompletedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        log.info("RMA 订单 {} 已完成，配额已在提交时占用", order.getCode());
        return rmaOrderRepository.save(order);
    }

    /**
     * 取消 RMA 订单：释放已占用配额。
     */
    @Transactional
    public RmaOrder cancel(Long id) {
        RmaOrder order = rmaOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "RMA 订单不存在"));
        if ("COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已完成订单不可取消");
        }
        if ("CANCELLED".equals(order.getStatus())) return order;

        RmaAuthorization auth = authRepository.lockById(order.getRefRmaAuthId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "RMA 授权不存在"));
        BigDecimal used = auth.getQuotaUsed() == null ? BigDecimal.ZERO : auth.getQuotaUsed();
        BigDecimal after = used.subtract(order.getAmount()).max(BigDecimal.ZERO);
        auth.setQuotaUsed(after);
        auth.setUpdatedAt(OffsetDateTime.now());
        authRepository.save(auth);

        order.setStatus("CANCELLED");
        order.setUpdatedAt(OffsetDateTime.now());
        return rmaOrderRepository.save(order);
    }
}
