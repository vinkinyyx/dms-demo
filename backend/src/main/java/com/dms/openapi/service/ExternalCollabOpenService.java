package com.dms.openapi.service;

import com.dms.common.ApiResponse;
import com.dms.common.ErrorCode;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.openapi.dto.collab.CollabPurchaseOrderRequest;
import com.dms.openapi.dto.collab.CollabPurchaseReturnRequest;
import com.dms.openapi.dto.collab.CollabSubmitResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 平台外下游经销商 -> 厂家 DMS 报文协同入站服务（v4.5.4）。
 *
 * <p>接口1：经销商采购订单提交 -> 厂家租户创建 DRAFT 销售订单（物料按 open_partner_materials 映射）。
 * <p>接口3：经销商采退单提交 -> 厂家租户创建 DRAFT 红字销退订单（is_red=true）。
 *
 * <p>幂等：open_collab_messages 上 (app_id, msg_type, partner_doc_no) WHERE direction='IN' 唯一索引，
 * 同一应用同一经销商单号重复报文直接返回首次结果（created=false）。
 * 鉴权上下文由 {@link com.dms.openapi.OpenApiAuthFilter} 写入（厂家租户 + appKey + openAppId）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalCollabOpenService {

    private final EntityManager em;
    private final DocNoGenerator docNoGenerator;
    private final ObjectMapper objectMapper;

    /** 接口1：采购订单提交。 */
    @Transactional
    public ApiResponse<CollabSubmitResult> submitPurchaseOrder(CollabPurchaseOrderRequest req) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) return ApiResponse.fail(40100, "未识别租户");
        Long appId = currentAppId();
        String appKey = str(TenantContext.get("appKey"));
        CollabPurchaseOrderRequest.CollabPoHeader h = req.getHeader();
        String poNo = h.getPoNo();

        Tuple dealer = validateDealerApp(tid, appId, h.getDealerCode());
        if (dealer == null) {
            return ApiResponse.fail(ErrorCode.FORBIDDEN,
                    "报文 dealerCode 与开放应用绑定经销商不一致或应用未配置经销商: " + h.getDealerCode());
        }
        Long dealerId = toLong(dealer.get("dealer_id"));

        CollabSubmitResult existed = findInboundIdempotent(tid, appId, "PURCHASE_ORDER", poNo);
        if (existed != null) {
            log.info("[OPEN-COLLAB] 采购订单幂等命中 appKey={} poNo={} so={}", appKey, poNo, existed.getSalesOrderNo());
            return ApiResponse.ok(existed);
        }

        List<Tuple> mapped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        int lineNo = 0;
        for (CollabPurchaseOrderRequest.CollabPoLine line : req.getLines()) {
            lineNo++;
            Tuple m = findMaterialMapping(tid, appId, line.getMaterialCode());
            if (m == null) {
                failed.add("第" + lineNo + "行物料[" + line.getMaterialCode()
                        + (line.getMaterialName() == null ? "" : " " + line.getMaterialName()) + "]未配置映射");
                continue;
            }
            if (line.getQty() == null || line.getQty().signum() <= 0) {
                failed.add("第" + lineNo + "行物料[" + line.getMaterialCode() + "]数量非法");
                continue;
            }
            mapped.add(m);
        }
        if (!failed.isEmpty()) {
            return ApiResponse.fail(ErrorCode.BUSINESS_RULE_VIOLATION, "物料映射/数据校验失败：" + String.join("；", failed));
        }

        Long warehouseId = null;
        if (h.getWarehouseCode() != null && !h.getWarehouseCode().isBlank()) {
            warehouseId = lookupWarehouse(h.getWarehouseCode(), tid);
        }

        String soCode = docNoGenerator.next("SO");
        String remark = buildRemark(h.getRemark(), "经销商采购订单 " + poNo + " 报文转入（草稿，待厂家补价格并审批）");
        Object ins = em.createNativeQuery(
                "INSERT INTO orders (tenant_id, code, order_type, is_red, dealer_id, warehouse_id, ship_snapshot, " +
                "amount_incl_tax, discount_amount, final_amount, tax_amount, expected_date, status, remark, customer_po_code, " +
                "extra, created_at, updated_at) " +
                "VALUES (?1,?2,'NORMAL',false,?3,?4,CAST(?5 AS jsonb),0,0,0,0,?6,'DRAFT',?7,?8,CAST(?9 AS jsonb),now(),now()) RETURNING id")
                .setParameter(1, tid).setParameter(2, soCode).setParameter(3, dealerId).setParameter(4, warehouseId)
                .setParameter(5, shipSnapshot(dealer))
                .setParameter(6, h.getExpectedDate())
                .setParameter(7, remark).setParameter(8, poNo)
                .setParameter(9, extraJson(h.getOrderDate(), h.getWarehouseCode()))
                .getSingleResult();
        Long orderId = ((Number) ins).longValue();

        int seq = 1;
        for (int i = 0; i < req.getLines().size(); i++) {
            CollabPurchaseOrderRequest.CollabPoLine line = req.getLines().get(i);
            Tuple m = mapped.get(i);
            BigDecimal price = line.getUnitPrice() == null ? BigDecimal.ZERO : line.getUnitPrice();
            em.createNativeQuery(
                    "INSERT INTO order_lines (order_id, seq, product_id, qty, unit_price, tax_rate, sub_total, is_gift, created_at, updated_at) " +
                    "VALUES (?1,?2,?3,?4,?5,0.13,?6,false,now(),now())")
                    .setParameter(1, orderId).setParameter(2, seq++)
                    .setParameter(3, toLong(m.get("product_id")))
                    .setParameter(4, line.getQty()).setParameter(5, price)
                    .setParameter(6, line.getQty().multiply(price))
                    .executeUpdate();
        }

        CollabSubmitResult result = new CollabSubmitResult();
        result.setPartnerDocNo(poNo);
        result.setSalesOrderNo(soCode);
        result.setSalesOrderStatus("DRAFT");
        result.setCreated(true);
        recordInbound(tid, appId, appKey, "PURCHASE_ORDER", poNo, soCode, h.getDealerCode(), "PROCESSED", req);
        log.info("[OPEN-COLLAB] 采购订单 {} -> 厂家销售订单草稿 {} (appKey={}, dealer={})", poNo, soCode, appKey, h.getDealerCode());
        return ApiResponse.ok(result);
    }

    /** 接口3：采购退货单提交（红字销退）。 */
    @Transactional
    public ApiResponse<CollabSubmitResult> submitPurchaseReturn(CollabPurchaseReturnRequest req) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) return ApiResponse.fail(40100, "未识别租户");
        Long appId = currentAppId();
        String appKey = str(TenantContext.get("appKey"));
        CollabPurchaseReturnRequest.CollabReturnHeader h = req.getHeader();
        String returnNo = h.getReturnNo();

        Tuple dealer = validateDealerApp(tid, appId, h.getDealerCode());
        if (dealer == null) {
            return ApiResponse.fail(ErrorCode.FORBIDDEN,
                    "报文 dealerCode 与开放应用绑定经销商不一致或应用未配置经销商: " + h.getDealerCode());
        }
        Long dealerId = toLong(dealer.get("dealer_id"));

        CollabSubmitResult existed = findInboundIdempotent(tid, appId, "PURCHASE_RETURN", returnNo);
        if (existed != null) {
            log.info("[OPEN-COLLAB] 采退单幂等命中 appKey={} returnNo={} redSo={}", appKey, returnNo, existed.getRedSalesReturnNo());
            return ApiResponse.ok(existed);
        }

        List<String> failed = new ArrayList<>();
        List<Tuple> mapped = new ArrayList<>();
        int lineNo = 0;
        for (CollabPurchaseReturnRequest.CollabReturnLine line : req.getLines()) {
            lineNo++;
            Tuple m = findMaterialMapping(tid, appId, line.getMaterialCode());
            if (m == null) {
                failed.add("第" + lineNo + "行物料[" + line.getMaterialCode()
                        + (line.getMaterialName() == null ? "" : " " + line.getMaterialName()) + "]未配置映射");
                continue;
            }
            if (line.getQty() == null || line.getQty().signum() <= 0) {
                failed.add("第" + lineNo + "行物料[" + line.getMaterialCode() + "]数量非法");
                continue;
            }
            mapped.add(m);
        }
        if (!failed.isEmpty()) {
            return ApiResponse.fail(ErrorCode.BUSINESS_RULE_VIOLATION, "物料映射/数据校验失败：" + String.join("；", failed));
        }

        String redSoCode = docNoGenerator.next("SO");
        String remark = "经销商采退单 " + returnNo + " 报文转入红字销退（草稿，待审批）"
                + (h.getRefOutboundNo() == null || h.getRefOutboundNo().isBlank() ? "" : "；原厂家出库单：" + h.getRefOutboundNo())
                + (h.getRemark() == null || h.getRemark().isBlank() ? "" : "；备注：" + h.getRemark());
        Object ins = em.createNativeQuery(
                "INSERT INTO orders (tenant_id, code, order_type, is_red, dealer_id, amount_incl_tax, discount_amount, final_amount, " +
                "status, remark, customer_po_code, ship_snapshot, created_at, updated_at) " +
                "VALUES (?1,?2,'NORMAL',true,?3,0,0,0,'DRAFT',?4,?5,CAST('{}' AS jsonb),now(),now()) RETURNING id")
                .setParameter(1, tid).setParameter(2, redSoCode).setParameter(3, dealerId)
                .setParameter(4, remark).setParameter(5, returnNo)
                .getSingleResult();
        Long redSoId = ((Number) ins).longValue();

        int seq = 1;
        for (int i = 0; i < req.getLines().size(); i++) {
            CollabPurchaseReturnRequest.CollabReturnLine line = req.getLines().get(i);
            Tuple m = mapped.get(i);
            em.createNativeQuery(
                    "INSERT INTO order_lines (order_id, seq, product_id, qty, unit_price, tax_rate, sub_total, " +
                    "line_discount_value, line_discount_amount, header_discount_amount, is_gift, created_at, updated_at) " +
                    "VALUES (?1,?2,?3,?4,0,0.13,0,0,0,0,false,now(),now())")
                    .setParameter(1, redSoId).setParameter(2, seq++)
                    .setParameter(3, toLong(m.get("product_id")))
                    .setParameter(4, line.getQty())
                    .executeUpdate();
        }

        CollabSubmitResult result = new CollabSubmitResult();
        result.setPartnerDocNo(returnNo);
        result.setRedSalesReturnNo(redSoCode);
        result.setRedSalesReturnStatus("DRAFT");
        result.setCreated(true);
        recordInbound(tid, appId, appKey, "PURCHASE_RETURN", returnNo, redSoCode, h.getDealerCode(), "PROCESSED", req);
        log.info("[OPEN-COLLAB] 采退单 {} -> 厂家红字销退草稿 {} (appKey={}, dealer={})", returnNo, redSoCode, appKey, h.getDealerCode());
        return ApiResponse.ok(result);
    }

    // ---------------- helpers ----------------

    private void recordInbound(UUID tid, Long appId, String appKey, String msgType, String partnerDocNo,
                               String localDocNo, String dealerCode, String status, Object payload) {
        try {
            em.createNativeQuery(
                    "INSERT INTO open_collab_messages (tenant_id, app_id, app_key, direction, msg_type, partner_doc_no, " +
                    "local_doc_no, dealer_code, request_body, status, created_at, updated_at) " +
                    "VALUES (?1,?2,?3,'IN',?4,?5,?6,?7,?8,?9,now(),now())")
                    .setParameter(1, tid).setParameter(2, appId).setParameter(3, appKey)
                    .setParameter(4, msgType).setParameter(5, partnerDocNo).setParameter(6, localDocNo)
                    .setParameter(7, dealerCode)
                    .setParameter(8, objectMapper.writeValueAsString(payload))
                    .setParameter(9, status)
                    .executeUpdate();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ux_ocm_in_idem")) {
                log.warn("[OPEN-COLLAB] 入站台账唯一键冲突（并发重复报文）msgType={} docNo={}", msgType, partnerDocNo);
                return;
            }
            throw new RuntimeException("[OPEN-COLLAB] 入站台账落库失败 msgType=" + msgType + " docNo=" + partnerDocNo + ": " + e.getMessage(), e);
        }
    }

    private CollabSubmitResult findInboundIdempotent(UUID tid, Long appId, String msgType, String partnerDocNo) {
        List<Tuple> rows = em.createNativeQuery(
                "SELECT local_doc_no, status FROM open_collab_messages " +
                "WHERE tenant_id=?1 AND app_id=?2 AND direction='IN' AND msg_type=?3 AND partner_doc_no=?4 " +
                "ORDER BY id DESC LIMIT 1", Tuple.class)
                .setParameter(1, tid).setParameter(2, appId).setParameter(3, msgType).setParameter(4, partnerDocNo)
                .getResultList();
        if (rows.isEmpty()) return null;
        Tuple r = rows.get(0);
        String local = str(r.get("local_doc_no"));
        if (local == null) return null;
        CollabSubmitResult res = new CollabSubmitResult();
        res.setPartnerDocNo(partnerDocNo);
        res.setCreated(false);
        if ("PURCHASE_ORDER".equals(msgType)) {
            res.setSalesOrderNo(local);
            res.setSalesOrderStatus("DRAFT");
        } else {
            res.setRedSalesReturnNo(local);
            res.setRedSalesReturnStatus("DRAFT");
        }
        return res;
    }

    private Tuple validateDealerApp(UUID tid, Long appId, String dealerCode) {
        if (appId == null || dealerCode == null || dealerCode.isBlank()) return null;
        List<Tuple> rows = em.createNativeQuery(
                "SELECT a.dealer_code, a.partner_type, d.id AS dealer_id " +
                "FROM open_app a LEFT JOIN dealers d ON d.tenant_id = a.tenant_id AND d.code = a.dealer_code " +
                "AND d.deleted_at IS NULL " +
                "WHERE a.id = ?1 AND a.tenant_id = ?2 AND a.status = 'active'", Tuple.class)
                .setParameter(1, appId).setParameter(2, tid).getResultList();
        if (rows.isEmpty()) return null;
        Tuple app = rows.get(0);
        String boundCode = str(app.get("dealer_code"));
        if (!"DEALER".equals(str(app.get("partner_type"))) || boundCode == null) return null;
        if (!boundCode.equals(dealerCode.trim())) return null;
        if (app.get("dealer_id") == null) return null;
        return app;
    }

    private Tuple findMaterialMapping(UUID tid, Long appId, String externalCode) {
        if (externalCode == null || externalCode.isBlank()) return null;
        List<Tuple> rows = em.createNativeQuery(
                "SELECT product_id, product_code, external_code, external_name FROM open_partner_materials " +
                "WHERE tenant_id=?1 AND app_id=?2 AND external_code=?3 AND status='active' AND deleted_at IS NULL LIMIT 1", Tuple.class)
                .setParameter(1, tid).setParameter(2, appId).setParameter(3, externalCode.trim())
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long lookupWarehouse(String code, UUID tid) {
        List<?> rs = em.createNativeQuery(
                "SELECT id FROM warehouses WHERE tenant_id=?1 AND code=?2 AND COALESCE(status,'active')='active' LIMIT 1")
                .setParameter(1, tid).setParameter(2, code.trim()).getResultList();
        return rs.isEmpty() ? null : ((Number) rs.get(0)).longValue();
    }

    private String shipSnapshot(Tuple dealer) {
        try {
            List<Tuple> ds = em.createNativeQuery("SELECT name FROM dealers WHERE id=?1", Tuple.class)
                    .setParameter(1, toLong(dealer.get("dealer_id"))).getResultList();
            String name = ds.isEmpty() ? "" : str(ds.get(0).get("name"));
            return objectMapper.writeValueAsString(java.util.Map.of("dealerName", name == null ? "" : name));
        } catch (Exception e) {
            return "{\"dealerName\":\"\"}";
        }
    }

    private String extraJson(java.time.LocalDate orderDate, String warehouseCode) {
        try {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            if (orderDate != null) m.put("orderDate", orderDate.toString());
            if (warehouseCode != null && !warehouseCode.isBlank()) m.put("dealerWarehouseCode", warehouseCode);
            m.put("source", "EXTERNAL_DEALER_COLLAB");
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{\"source\":\"EXTERNAL_DEALER_COLLAB\"}";
        }
    }

    private String buildRemark(String userRemark, String prefix) {
        if (userRemark == null || userRemark.isBlank()) return prefix;
        return prefix + "；经销商备注：" + userRemark;
    }

    private Long currentAppId() {
        Object v = TenantContext.get("openAppId");
        if (v == null) return null;
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private Long toLong(Object o) { return o == null ? null : ((Number) o).longValue(); }
    private String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
