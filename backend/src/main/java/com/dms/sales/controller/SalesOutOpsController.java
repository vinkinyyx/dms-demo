/*
 * 销售出库增强 Controller (v3.1)
 * 提供完整的出库业务逻辑：
 *   - 普通销售出库 (isRed=false) → 扣减 QUALIFIED 库存
 *   - 红字销售出库 (isRed=true) → 必须关联原销售出库单 refSalesOutId
 *                                → 增加 PENDING 库存（销退退回,重新质检）
 * 使用 /api/sales-out-ops 端点避免与已有 SalesOutController 冲突
 */
package com.dms.sales.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.inventory.service.InventoryStatusOps;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/sales-out-ops")
@RequiredArgsConstructor
public class SalesOutOpsController {

    private final EntityManager em;
    private final InventoryStatusOps inventoryOps;

    /**
     * 创建销售出库单（含库存扣减 or 红字增加）
     * body:
     *   isRed: false (销售出库) / true (红字销售出库)
     *   dealerId, terminalId, warehouseId (必填)
     *   refSalesOutId (isRed=true 时必填)
     *   salesDate (可选)
     *   remark
     *   lines: [{ productId, qty, unitPrice, batchNo }]
     */
    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");

        boolean isRed = Boolean.TRUE.equals(body.get("isRed"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("isRed")));
        Long dealerId = toLong(body.get("dealerId"));
        Long terminalId = toLong(body.get("terminalId"));
        Long warehouseId = toLong(body.get("warehouseId"));
        Long refSalesOutId = toLong(body.get("refSalesOutId"));
        LocalDate salesDate = parseDate(body.get("salesDate"));
        String remark = strOr(body.get("remark"), "");

        if (dealerId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "经销商必填");
        if (warehouseId == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "仓库必填");

        // 红字销售出库必须关联原销售出库单
        if (isRed) {
            if (refSalesOutId == null) {
                throw new BusinessException(ErrorCode.PARAM_MISSING,
                        "红字销售出库(销退)必须关联原正向销售出库单 refSalesOutId");
            }
            // 校验原单存在且非红字
            var refQ = em.createNativeQuery(
                    "SELECT is_red FROM sales_outs WHERE id = ?1 AND tenant_id = ?2");
            refQ.setParameter(1, refSalesOutId).setParameter(2, tid);
            List<?> rs = refQ.getResultList();
            if (rs.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND,
                        "关联的原销售出库单不存在: " + refSalesOutId);
            }
            if (Boolean.TRUE.equals(rs.get(0))) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "关联的必须是正向销售出库单，不能是红字单");
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "出库明细不能为空");
        }

        // 生成单号
        String code = (isRed ? "SR-" : "SO-") + System.currentTimeMillis();

        // 插入销售出库主表
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (Map<String, Object> l : lines) {
            BigDecimal qty = toBd(l.get("qty"));
            BigDecimal price = toBd(l.get("unitPrice"));
            if (qty == null || qty.signum() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "明细数量必须 > 0");
            }
            if (price == null) price = BigDecimal.ZERO;
            totalAmt = totalAmt.add(qty.multiply(price));
        }
        BigDecimal amtSigned = isRed ? totalAmt.negate() : totalAmt;

        var ins = em.createNativeQuery(
                "INSERT INTO sales_outs (tenant_id, code, dealer_id, terminal_id, sales_date, " +
                "is_red, ref_sales_out_id, status, amount_incl_tax, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 'COMPLETED', ?8, now(), now()) " +
                "RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, dealerId)
                .setParameter(4, terminalId).setParameter(5, salesDate == null ? LocalDate.now() : salesDate)
                .setParameter(6, isRed).setParameter(7, refSalesOutId).setParameter(8, amtSigned);
        Object idObj = ins.getSingleResult();
        Long salesOutId = ((Number) idObj).longValue();

        // 明细 + 库存变动
        for (Map<String, Object> l : lines) {
            Long productId = toLong(l.get("productId"));
            BigDecimal qty = toBd(l.get("qty"));
            BigDecimal price = toBd(l.get("unitPrice"));
            if (price == null) price = BigDecimal.ZERO;
            String batchNo = strOr(l.get("batchNo"), null);
            String serialNo = strOr(l.get("serialNo"), null);

            // 严格校验：根据产品追踪属性判定必须传批次或序列号
            boolean serialManaged = isProductSerialManaged(tid, productId);
            if (serialManaged) {
                if (serialNo == null) throw new BusinessException(ErrorCode.PARAM_MISSING,
                        "产品 " + productId + " 是序列号管理，必须指定 serialNo");
            } else {
                if (batchNo == null) throw new BusinessException(ErrorCode.PARAM_MISSING,
                        "产品 " + productId + " 是批次管理，必须指定 batchNo");
            }

            // 明细写入
            em.createNativeQuery(
                    "INSERT INTO sales_out_lines (sales_out_id, warehouse_id, product_id, batch_no, qty) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, salesOutId).setParameter(2, warehouseId)
                .setParameter(3, productId).setParameter(4, batchNo)
                .setParameter(5, isRed ? qty.negate() : qty)
                .executeUpdate();

            if (isRed) {
                // 红字销售出库 = 销退退回 = 增加 PENDING 库存（重新质检）
                String effKey = serialNo != null ? serialNo : batchNo;
                inventoryOps.change(tid, productId, warehouseId, effKey,
                        qty, "PENDING", "SALES_OUT_RED", "sales_out", salesOutId);
                log.info("红字销售出库 code={} 增加 PENDING 库存 product={} qty={}", code, productId, qty);
            } else {
                // 正常销售出库 = 扣减 QUALIFIED 库存（按指定批次/序列号）
                String effKey = serialNo != null ? serialNo : batchNo;
                inventoryOps.change(tid, productId, warehouseId, effKey,
                        qty.negate(), "QUALIFIED", "SALES_OUT", "sales_out", salesOutId);
                log.info("销售出库 code={} 扣减 QUALIFIED 库存 product={} qty={}", code, productId, qty);
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", salesOutId);
        res.put("code", code);
        res.put("isRed", isRed);
        res.put("dealerId", dealerId);
        res.put("terminalId", terminalId);
        res.put("warehouseId", warehouseId);
        res.put("refSalesOutId", refSalesOutId);
        res.put("amountInclTax", amtSigned);
        res.put("lineCount", lines.size());
        res.put("message", isRed ?
                "红字销售出库完成: 已增加 " + lines.size() + " 项 PENDING 待检库存" :
                "销售出库完成: 已扣减 " + lines.size() + " 项 QUALIFIED 合格库存");
        return ApiResponse.ok(res);
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private boolean isProductSerialManaged(UUID tid, Long productId) {
        try {
            var q = em.createNativeQuery(
                    "SELECT is_serial_managed FROM products WHERE id = ?1 AND tenant_id = ?2");
            q.setParameter(1, productId).setParameter(2, tid);
            List<?> rs = q.getResultList();
            if (rs.isEmpty() || rs.get(0) == null) return false;
            return Boolean.TRUE.equals(rs.get(0));
        } catch (Exception e) { return false; }
    }
    private BigDecimal toBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private String strOr(Object o, String d) {
        if (o == null) return d;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? d : s;
    }
    private LocalDate parseDate(Object o) {
        if (o == null) return null;
        try { return LocalDate.parse(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
