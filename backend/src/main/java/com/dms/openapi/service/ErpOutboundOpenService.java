package com.dms.openapi.service;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.openapi.dto.ErpOutboundLine;
import com.dms.openapi.dto.ErpOutboundRequest;
import com.dms.openapi.dto.ErpOutboundResult;
import com.dms.v4.V4ErpService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ERP -> DMS 销售出库回传对外接口服务。
 *
 * <p>对外暴露带校验的 DTO，内部组装为 {@link V4ErpService#receiveOutbound(Map)}
 * 能接受的报文结构并复用其出库、库存/批号/序列号、状态机与幂等逻辑。
 * callbacks 记录由 receiveOutbound 写入，本服务仅在成功后回填 request_id，避免重复写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpOutboundOpenService {

    private final EntityManager em;
    private final V4ErpService v4ErpService;

    @Transactional
    public ApiResponse<ErpOutboundResult> receive(ErpOutboundRequest req) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) {
            return ApiResponse.fail(40100, "未识别租户");
        }

        String direction = normalizeDirection(req.getDirection());
        if (direction == null) {
            return ApiResponse.fail(ErrorCode.PARAM_INVALID, "direction 仅支持 FORWARD 或 RED");
        }
        boolean red = "RED".equals(direction);

        if (req.getSourceOrderId() == null
                && (req.getSourceOrderCode() == null || req.getSourceOrderCode().isBlank())) {
            return ApiResponse.fail(ErrorCode.PARAM_MISSING, "sourceOrderId 与 sourceOrderCode 至少传一个");
        }

        ErpOutboundResult existed = findIdempotent(tid, req.getIdempotencyKey());
        if (existed != null) {
            log.info("[OPEN-ERP] 幂等命中 key={} salesOutId={} requestId={}",
                    req.getIdempotencyKey(), existed.getSalesOutId(), req.getRequestId());
            return ApiResponse.ok(existed);
        }

        Tuple order = resolveOrder(tid, req.getSourceOrderId(), req.getSourceOrderCode(), red);
        if (order == null) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND,
                    "订单不存在: " + (req.getSourceOrderId() != null ? req.getSourceOrderId() : req.getSourceOrderCode()));
        }
        Long orderId = toLong(order.get("id"));
        Long dealerId = toLong(order.get("dealer_id"));

        Long warehouseId = null;
        if (req.getWarehouseCode() != null && !req.getWarehouseCode().isBlank()) {
            warehouseId = lookupWarehouse(req.getWarehouseCode(), tid);
            if (warehouseId == null) {
                return ApiResponse.fail(ErrorCode.NOT_FOUND, "仓库不存在: " + req.getWarehouseCode());
            }
        }

        List<Tuple> orderLines = loadOrderLines(orderId, red);
        List<Map<String, Object>> payloadLines = new ArrayList<>();
        List<ErpOutboundResult.FailedLine> failedLines = new ArrayList<>();
        int lineNo = 0;
        for (ErpOutboundLine line : req.getLines()) {
            lineNo++;
            String productLabel = productLabel(line);
            try {
                Long productId = resolveProduct(line, tid);
                if (productId == null) {
                    failedLines.add(new ErpOutboundResult.FailedLine(lineNo, productLabel, "产品不存在或无权限"));
                    continue;
                }
                Tuple orderLine = matchOrderLine(orderLines, line.getSourceOrderLineId(), productId, orderId);
                if (orderLine == null) {
                    failedLines.add(new ErpOutboundResult.FailedLine(lineNo, productLabel,
                            "未匹配到订单行（请检查产品或传入 sourceOrderLineId）"));
                    continue;
                }
                BigDecimal already = bd(orderLine.get("shipped"));
                BigDecimal allowed = bd(orderLine.get("qty")).subtract(bd(orderLine.get("closed_qty"))).subtract(already);
                if (line.getQty().compareTo(allowed) > 0) {
                    failedLines.add(new ErpOutboundResult.FailedLine(lineNo, productLabel,
                            "出库数量 " + line.getQty() + " 超过待出库数量 " + allowed));
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orderLineId", toLong(orderLine.get("id")));
                row.put("productId", productId);
                row.put("qty", line.getQty());
                row.put("batchNo", line.getBatchNo());
                row.put("serialNo", line.getSerialNo());
                if (line.getUnitPrice() != null) row.put("unitPrice", line.getUnitPrice());
                payloadLines.add(row);
            } catch (BusinessException e) {
                log.warn("[OPEN-ERP] 行业务校验失败 lineNo={} product={}: {}", lineNo, productLabel, e.getMessage());
                failedLines.add(new ErpOutboundResult.FailedLine(lineNo, productLabel, e.getMessage()));
            } catch (Exception e) {
                log.warn("[OPEN-ERP] 行解析失败 lineNo={} product={}: {}", lineNo, productLabel, e.getMessage());
                failedLines.add(new ErpOutboundResult.FailedLine(lineNo, productLabel, e.getMessage()));
            }
        }

        if (!failedLines.isEmpty()) {
            ErpOutboundResult fail = new ErpOutboundResult();
            fail.setCode(ErrorCode.BUSINESS_RULE_VIOLATION.getCode());
            fail.setMessage("出库回传存在 " + failedLines.size() + " 行校验失败");
            fail.setDirection(direction);
            fail.setIdempotent(false);
            fail.setProcessedLines(0);
            fail.setFailedLines(failedLines);
            return new ApiResponse<>(ErrorCode.BUSINESS_RULE_VIOLATION.getCode(), fail.getMessage(), fail, "");
        }

        LocalDate salesDate = req.getOutboundDate() != null ? req.getOutboundDate() : LocalDate.now();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceOrderId", orderId);
        payload.put("dealerId", dealerId);
        payload.put("direction", direction);
        payload.put("warehouseId", warehouseId);
        payload.put("salesDate", salesDate);
        payload.put("lines", payloadLines);
        payload.put("idempotencyKey", req.getIdempotencyKey());
        payload.put("erpOutboundNo", req.getErpOutboundNo());

        Map<String, Object> out;
        try {
            out = v4ErpService.receiveOutbound(payload);
        } catch (BusinessException e) {
            log.warn("[OPEN-ERP] 出库回传业务失败 key={}: {}", req.getIdempotencyKey(), e.getMessage());
            return ApiResponse.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[OPEN-ERP] 出库回传系统异常 key={}", req.getIdempotencyKey(), e);
            return ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "出库回传处理失败: " + e.getMessage());
        }

        if (req.getRequestId() != null && !req.getRequestId().isBlank()) {
            em.createNativeQuery("UPDATE erp_outbound_callbacks SET request_id=?1 WHERE tenant_id=?2 AND idempotency_key=?3")
                    .setParameter(1, req.getRequestId())
                    .setParameter(2, tid)
                    .setParameter(3, req.getIdempotencyKey())
                    .executeUpdate();
        }

        ErpOutboundResult result = new ErpOutboundResult();
        result.setCode(0);
        result.setMessage("OK");
        result.setSalesOutId(toLong(out.get("id")));
        result.setSalesOutCode(str(out.get("code")));
        result.setIdempotent(Boolean.TRUE.equals(out.get("idempotent")));
        result.setDirection(direction);
        result.setProcessedLines(payloadLines.size());
        log.info("[OPEN-ERP] 出库回传成功 key={} salesOutId={} code={} lines={} requestId={}",
                req.getIdempotencyKey(), result.getSalesOutId(), result.getSalesOutCode(),
                payloadLines.size(), req.getRequestId());
        return ApiResponse.ok(result);
    }
    @Transactional(readOnly = true)
    public ApiResponse<ErpOutboundResult> query(String idempotencyKey) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) {
            return ApiResponse.fail(40100, "未识别租户");
        }
        ErpOutboundResult result = findIdempotent(tid, idempotencyKey);
        if (result == null) {
            return ApiResponse.fail(ErrorCode.NOT_FOUND, "未找到回调记录: " + idempotencyKey);
        }
        return ApiResponse.ok(result);
    }

    private ErpOutboundResult findIdempotent(UUID tid, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
        @SuppressWarnings("unchecked")
        List<Tuple> rs = em.createNativeQuery(
                "SELECT c.sales_out_id, c.direction, c.process_status, c.request_id, c.erp_outbound_no, "
                        + "s.code AS sales_out_code, "
                        + "(SELECT COUNT(1) FROM sales_out_lines sol WHERE sol.sales_out_id = c.sales_out_id) AS line_count "
                        + "FROM erp_outbound_callbacks c LEFT JOIN sales_outs s ON s.id = c.sales_out_id "
                        + "WHERE c.tenant_id = ?1 AND c.idempotency_key = ?2", Tuple.class)
                .setParameter(1, tid).setParameter(2, idempotencyKey).getResultList();
        if (rs.isEmpty()) return null;
        Tuple t = rs.get(0);
        ErpOutboundResult r = new ErpOutboundResult();
        boolean processed = "PROCESSED".equals(str(t.get("process_status")));
        r.setCode(processed ? 0 : ErrorCode.BUSINESS_RULE_VIOLATION.getCode());
        r.setMessage(processed ? "OK" : "回调记录状态: " + t.get("process_status"));
        r.setSalesOutId(toLong(t.get("sales_out_id")));
        r.setSalesOutCode(str(t.get("sales_out_code")));
        r.setIdempotent(true);
        r.setDirection(str(t.get("direction")));
        r.setProcessedLines(toInt(t.get("line_count")));
        return r;
    }

    private Tuple resolveOrder(UUID tid, Long orderId, String orderCode, boolean red) {
        String sql = "SELECT id, dealer_id, warehouse_id, status, erp_status FROM orders "
                + "WHERE tenant_id = ?1 AND COALESCE(is_red,false) = ?2 AND deleted_at IS NULL AND ";
        jakarta.persistence.Query q;
        if (orderId != null) {
            q = em.createNativeQuery(sql + "id = ?3", Tuple.class)
                    .setParameter(1, tid).setParameter(2, red).setParameter(3, orderId);
        } else {
            q = em.createNativeQuery(sql + "code = ?3", Tuple.class)
                    .setParameter(1, tid).setParameter(2, red).setParameter(3, orderCode);
        }
        @SuppressWarnings("unchecked")
        List<Tuple> rs = q.getResultList();
        return rs.isEmpty() ? null : rs.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Tuple> loadOrderLines(Long orderId, boolean red) {
        return em.createNativeQuery(
                "SELECT ol.id, ol.product_id, ol.qty, ol.closed_qty, ol.line_level, ol.is_group_header, "
                        + "COALESCE((SELECT SUM(COALESCE(sol.shipped_qty, sol.qty, 0)) "
                        + "  FROM sales_out_lines sol JOIN sales_outs so ON so.id = sol.sales_out_id "
                        + "  WHERE sol.source_order_line_id = ol.id AND so.is_red = ?2 AND so.deleted_at IS NULL), 0) AS shipped "
                        + "FROM order_lines ol WHERE ol.order_id = ?1 "
                        + "AND COALESCE(ol.line_level, 'NORMAL') <> 'PARENT'", Tuple.class)
                .setParameter(1, orderId).setParameter(2, red).getResultList();
    }

    private Tuple matchOrderLine(List<Tuple> orderLines, Long sourceOrderLineId, Long productId, Long orderId) {
        if (sourceOrderLineId != null) {
            for (Tuple t : orderLines) {
                if (sourceOrderLineId.equals(toLong(t.get("id")))) {
                    if (Boolean.TRUE.equals(t.get("is_group_header"))) {
                        throw new BusinessException(ErrorCode.PARAM_INVALID, "订单行是 BOM 母件行，不能直接出库");
                    }
                    if (!productId.equals(toLong(t.get("product_id")))) {
                        throw new BusinessException(ErrorCode.PARAM_INVALID, "订单行产品与报文产品不一致");
                    }
                    return t;
                }
            }
            throw new BusinessException(ErrorCode.PARAM_INVALID, "sourceOrderLineId 不属于当前订单: " + sourceOrderLineId);
        }
        List<Tuple> candidates = new ArrayList<>();
        for (Tuple t : orderLines) {
            if (Boolean.TRUE.equals(t.get("is_group_header"))) continue;
            if (productId.equals(toLong(t.get("product_id")))) {
                BigDecimal allowed = bd(t.get("qty")).subtract(bd(t.get("closed_qty"))).subtract(bd(t.get("shipped")));
                if (allowed.signum() > 0) candidates.add(t);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "产品存在多个可出库订单行，请传入 sourceOrderLineId 明确指定");
        }
        return candidates.get(0);
    }

    private Long resolveProduct(ErpOutboundLine line, UUID tid) {
        if (line.getProductId() != null) {
            var rs = em.createNativeQuery("SELECT id FROM products WHERE id = ?1 AND tenant_id = ?2")
                    .setParameter(1, line.getProductId()).setParameter(2, tid).getResultList();
            return rs.isEmpty() ? null : toLong(rs.get(0));
        }
        if (line.getProductCode() != null && !line.getProductCode().isBlank()) {
            var rs = em.createNativeQuery("SELECT id FROM products WHERE tenant_id = ?1 AND code = ?2")
                    .setParameter(1, tid).setParameter(2, line.getProductCode()).getResultList();
            return rs.isEmpty() ? null : toLong(rs.get(0));
        }
        throw new BusinessException(ErrorCode.PARAM_MISSING, "productCode 与 productId 至少传一个");
    }

    private Long lookupWarehouse(String code, UUID tid) {
        var rs = em.createNativeQuery("SELECT id FROM warehouses WHERE tenant_id = ?1 AND code = ?2")
                .setParameter(1, tid).setParameter(2, code).getResultList();
        return rs.isEmpty() ? null : toLong(rs.get(0));
    }

    private String normalizeDirection(String d) {
        if (d == null || d.isBlank()) return "FORWARD";
        String u = d.trim().toUpperCase();
        return ("FORWARD".equals(u) || "RED".equals(u)) ? u : null;
    }

    private String productLabel(ErpOutboundLine line) {
        if (line.getProductCode() != null && !line.getProductCode().isBlank()) return line.getProductCode();
        if (line.getProductId() != null) return String.valueOf(line.getProductId());
        if (line.getSourceOrderLineId() != null) return "line:" + line.getSourceOrderLineId();
        return "unknown";
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private Integer toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private BigDecimal bd(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(o));
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}