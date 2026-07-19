/*
 * 销售出库服务：
 *   create → 授权校验(SALES_TO_HOSPITAL) → 扣库存 → 写 sales_out_facts。
 *   redCancel → 生成红字冲销单 + 库存反向变动。
 */
package com.dms.sales.service;

import com.dms.authz.dto.AuthorizationCheckRequest;
import com.dms.authz.dto.AuthorizationCheckResult;
import com.dms.authz.service.AuthorizationService;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.inventory.service.InventoryService;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.repository.DealerRepository;
import com.dms.sales.entity.SalesOut;
import com.dms.sales.entity.SalesOutFact;
import com.dms.sales.entity.SalesOutLine;
import com.dms.sales.repository.SalesOutFactRepository;
import com.dms.sales.repository.SalesOutLineRepository;
import com.dms.sales.repository.SalesOutRepository;
import com.dms.tenant.entity.Tenant;
import com.dms.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOutService {

    private final SalesOutRepository salesOutRepository;
    private final SalesOutLineRepository lineRepository;
    private final SalesOutFactRepository factRepository;
    private final AuthorizationService authorizationService;
    private final InventoryService inventoryService;
    private final DocNoGenerator docNoGenerator;
    private final TenantRepository tenantRepository;
    private final DealerRepository dealerRepository;

    @Transactional(readOnly = true)
    public PageResult<SalesOut> list(PageQuery pageQuery) {
        UUID tenantId = TenantContext.getTenantId();
        Page<SalesOut> page = tenantId == null
                ? salesOutRepository.findAll(pageQuery.toPageable())
                : salesOutRepository.findByTenantId(tenantId, pageQuery.toPageable());
        return PageResult.of(page);
    }

    /**
     * 创建销售出库：授权 → 扣库存 → 写 fact。
     */
    @Transactional
    public SalesOut create(SalesOut salesOut, List<SalesOutLine> lines) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "销售出库明细为空");
        }

        // 医疗器械行业强制附件/序列号（简化：仅日志提示）
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant != null && "medical".equals(tenant.getIndustry())) {
            for (SalesOutLine l : lines) {
                if (l.getSerialNo() == null || l.getSerialNo().isBlank()) {
                    log.warn("[医疗器械] 销售明细缺少 serial_no，product={} qty={}", l.getProductId(), l.getQty());
                }
            }
        }

        // 授权校验 SALES_TO_HOSPITAL
        AuthorizationCheckRequest authReq = new AuthorizationCheckRequest();
        authReq.setDealerId(salesOut.getDealerId());
        authReq.setAuthType("SALES_TO_HOSPITAL");
        authReq.setAtTime(salesOut.getSalesDate() == null ? LocalDate.now() : salesOut.getSalesDate());
        List<AuthorizationCheckRequest.Line> authLines = new ArrayList<>();
        for (SalesOutLine l : lines) {
            AuthorizationCheckRequest.Line al = new AuthorizationCheckRequest.Line();
            al.setProductId(l.getProductId());
            al.setTerminalId(salesOut.getTerminalId());
            authLines.add(al);
        }
        authReq.setLines(authLines);
        List<AuthorizationCheckResult> checks = authorizationService.check(authReq);
        List<String> unauth = checks.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getAuthorized()))
                .map(r -> "product=" + r.getProductId())
                .toList();
        if (!unauth.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "销售授权校验失败: " + String.join(",", unauth));
        }

        salesOut.setId(null);
        salesOut.setTenantId(tenantId);
        salesOut.setCode(docNoGenerator.next("CK"));
        salesOut.setStatus("COMPLETED");
        salesOut.setIsRed(false);
        salesOut.setCreatedBy(TenantContext.getUserId());
        salesOut.setUpdatedAt(OffsetDateTime.now());
        if (salesOut.getSalesDate() == null) salesOut.setSalesDate(LocalDate.now());
        salesOut.ensureJson();

        BigDecimal totalAmount = BigDecimal.ZERO;
        SalesOut saved = salesOutRepository.save(salesOut);

        Long regionId = null;
        if (salesOut.getDealerId() != null) {
            Dealer d = dealerRepository.findById(salesOut.getDealerId()).orElse(null);
            if (d != null) regionId = d.getRegionId();
        }

        for (SalesOutLine l : lines) {
            l.setId(null);
            l.setSalesOutId(saved.getId());
            lineRepository.save(l);

            // 扣库存
            inventoryService.applyTransaction(tenantId, salesOut.getDealerId(), l.getWarehouseId(),
                    l.getProductId(), l.getBatchNo(), l.getSerialNo(),
                    l.getQty().negate(), "SALES_OUT", "SALES_OUT", saved.getId());

            BigDecimal unitPrice = l.getUnitPrice() == null ? BigDecimal.ZERO : l.getUnitPrice();
            BigDecimal amount = l.getQty().multiply(unitPrice);
            totalAmount = totalAmount.add(amount);

            SalesOutFact fact = SalesOutFact.builder()
                    .tenantId(tenantId)
                    .dealerId(salesOut.getDealerId())
                    .productId(l.getProductId())
                    .terminalId(salesOut.getTerminalId())
                    .regionId(regionId)
                    .salesDate(salesOut.getSalesDate())
                    .qty(l.getQty())
                    .amount(amount)
                    .build();
            factRepository.save(fact);
        }

        saved.setAmountInclTax(totalAmount);
        return salesOutRepository.save(saved);
    }

    /**
     * 红字冲销：生成 is_red=true 的负数销售出库单，库存反向入库。
     */
    @Transactional
    public SalesOut redCancel(Long id, String reason) {
        SalesOut origin = salesOutRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "原销售单不存在"));
        if (Boolean.TRUE.equals(origin.getIsRed())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "红字单不可再冲销");
        }
        UUID tenantId = origin.getTenantId();
        List<SalesOutLine> lines = lineRepository.findBySalesOutId(id);

        SalesOut red = SalesOut.builder()
                .tenantId(tenantId)
                .code(docNoGenerator.next("CK"))
                .dealerId(origin.getDealerId())
                .terminalId(origin.getTerminalId())
                .businessType(origin.getBusinessType())
                .salesDate(LocalDate.now())
                .surgeryInfo(origin.getSurgeryInfo())
                .isRed(true)
                .refSalesOutId(origin.getId())
                .status("COMPLETED")
                .amountInclTax(origin.getAmountInclTax() == null
                        ? null : origin.getAmountInclTax().negate())
                .createdBy(TenantContext.getUserId())
                .updatedAt(OffsetDateTime.now())
                .build();
        red.ensureJson();
        red.getSurgeryInfo().put("redReason", reason);
        SalesOut savedRed = salesOutRepository.save(red);

        for (SalesOutLine origLine : lines) {
            SalesOutLine redLine = SalesOutLine.builder()
                    .salesOutId(savedRed.getId())
                    .warehouseId(origLine.getWarehouseId())
                    .productId(origLine.getProductId())
                    .batchNo(origLine.getBatchNo())
                    .serialNo(null) // 红字单不可复用唯一序列号
                    .qty(origLine.getQty().negate())
                    .build();
            lineRepository.save(redLine);
            // 库存反向入库
            inventoryService.applyTransaction(tenantId, origin.getDealerId(), origLine.getWarehouseId(),
                    origLine.getProductId(), origLine.getBatchNo(), origLine.getSerialNo(),
                    origLine.getQty(), "SALES_OUT_RED", "SALES_OUT", savedRed.getId());
        }
        log.info("销售单 {} 红字冲销，红字单 {}", origin.getCode(), savedRed.getCode());
        return savedRed;
    }
}
