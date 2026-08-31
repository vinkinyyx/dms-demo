/*
 * 厂家 <-> 经销商跨租户协同服务。
 * 路径A：经销商采购单（供应商=平台厂家）提交 -> 厂家草稿销售订单（对码转换 + 回写厂家销售单号）。
 * 路径B：厂家销售出库 -> 经销商待收货入库单；无对应采购单时先自动补建"已审批"采购单。
 * 回传只带产品/数量/批次/序列号，不带价格。对码缺失一律阻断；台账保证幂等。
 */
package com.dms.collab;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrossTenantCollabService {

    private final EntityManager em;
    private final DocNoGenerator docNoGenerator;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 经销商采购订单提交：供应商是平台厂家则转厂家草稿销售订单，返回厂家销售订单号；未触发返回 null。 */
    public String onPurchaseOrderSubmitted(Long poId) {
        UUID dealerTenant = TenantContext.getTenantId();
        if (dealerTenant == null) return null;

        List<Tuple> poRows = em.createNativeQuery(
                "SELECT po.id, po.code, po.supplier_id, po.remark, s.manufacturer_tenant_id " +
                "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
                "WHERE po.id = ?1 AND po.tenant_id = ?2 AND po.deleted_at IS NULL", Tuple.class)
                .setParameter(1, poId).setParameter(2, dealerTenant).getResultList();
        if (poRows.isEmpty()) return null;
        Tuple po = poRows.get(0);
        UUID mfrTenant = toUuid(po.get("manufacturer_tenant_id"));
        if (mfrTenant == null) return null;

        List<?> exist = em.createNativeQuery(
                "SELECT sales_order_code FROM cross_tenant_doc_links WHERE po_id = ?1 AND link_type='PO_TO_SALES_ORDER'")
                .setParameter(1, poId).getResultList();
        if (!exist.isEmpty() && exist.get(0) != null) return String.valueOf(exist.get(0));

        Long mfrDealerId = resolveMfrDealerId(mfrTenant, dealerTenant);
        if (mfrDealerId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "经销商租户尚未绑定厂家客户主数据，无法转销售订单，请联系厂家完成绑定");
        }

        List<Tuple> lines = em.createNativeQuery(
                "SELECT pol.id, pol.seq, pol.product_id, pol.qty, p.code AS product_code, p.name_cn AS product_name " +
                "FROM purchase_order_lines pol LEFT JOIN products p ON p.id = pol.product_id " +
                "WHERE pol.po_id = ?1 ORDER BY pol.seq, pol.id", Tuple.class)
                .setParameter(1, poId).getResultList();
        if (lines.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "采购订单明细为空，无法转销售订单");

        List<Map<String, Object>> lineRefs = new ArrayList<>();
        List<Object[]> mapped = new ArrayList<>();
        StringBuilder missing = new StringBuilder();
        for (Tuple ln : lines) {
            Tuple map = findMappingByDealerProduct(mfrTenant, dealerTenant, lng(ln.get("product_id")));
            if (map == null) {
                if (missing.length() > 0) missing.append("、");
                missing.append("[").append(str(ln.get("product_code"))).append(" ")
                       .append(str(ln.get("product_name"))).append("]");
                continue;
            }
            mapped.add(new Object[]{lng(map.get("manufacturer_product_id")), bd(ln.get("qty")),
                    lng(ln.get("id")), lng(ln.get("seq"))});
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("poLineId", lng(ln.get("id")));
            ref.put("poSeq", lng(ln.get("seq")));
            ref.put("dealerProductCode", str(ln.get("product_code")));
            ref.put("mfrProductId", lng(map.get("manufacturer_product_id")));
            ref.put("mfrProductCode", str(map.get("manufacturer_product_code")));
            ref.put("qty", bd(ln.get("qty")));
            lineRefs.add(ref);
        }
        if (missing.length() > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "以下物料厂家尚未对码，无法转销售订单，请厂家先完成对码：" + missing);
        }

        String poCode = str(po.get("code"));
        String remark = po.get("remark") == null ? "" : String.valueOf(po.get("remark"));
        UUID savedCtx = TenantContext.getTenantId();
        String soCode;
        Long soId;
        try {
            TenantContext.setTenantId(mfrTenant);
            soCode = docNoGenerator.next("SO");
            Object ins = em.createNativeQuery(
                    "INSERT INTO orders (tenant_id, code, order_type, dealer_id, amount_incl_tax, discount_amount, final_amount, " +
                    "status, remark, customer_po_code, ship_snapshot, created_at, updated_at) " +
                    "VALUES (?1, ?2, 'NORMAL', ?3, 0, 0, 0, 'DRAFT', ?4, ?5, CAST('{}' AS jsonb), now(), now()) RETURNING id")
                    .setParameter(1, mfrTenant).setParameter(2, soCode).setParameter(3, mfrDealerId)
                    .setParameter(4, "经销商采购单 " + poCode + " 自动转入（草稿，待补充价格/折扣并审批）"
                            + (remark.isBlank() ? "" : "；采购备注：" + remark))
                    .setParameter(5, poCode)
                    .getSingleResult();
            soId = ((Number) ins).longValue();
            int seq = 1;
            for (Object[] ml : mapped) {
                em.createNativeQuery(
                        "INSERT INTO order_lines (order_id, seq, product_id, qty, unit_price, tax_rate, sub_total, " +
                        "line_discount_value, line_discount_amount, header_discount_amount, is_gift, created_at, updated_at) " +
                        "VALUES (?1, ?2, ?3, ?4, 0, 0.13, 0, 0, 0, 0, false, now(), now())")
                        .setParameter(1, soId).setParameter(2, seq++)
                        .setParameter(3, ml[0]).setParameter(4, (BigDecimal) ml[1])
                        .executeUpdate();
            }
        } finally {
            TenantContext.setTenantId(savedCtx);
        }

        em.createNativeQuery(
                "INSERT INTO cross_tenant_doc_links (manufacturer_tenant_id, dealer_tenant_id, link_type, po_id, po_code, " +
                "sales_order_id, sales_order_code, line_refs, status, created_at, updated_at) " +
                "VALUES (?1,?2,'PO_TO_SALES_ORDER',?3,?4,?5,?6, CAST(?7 AS jsonb),'linked',now(),now())")
                .setParameter(1, mfrTenant).setParameter(2, dealerTenant)
                .setParameter(3, poId).setParameter(4, poCode)
                .setParameter(5, soId).setParameter(6, soCode).setParameter(7, toJson(lineRefs))
                .executeUpdate();
        em.createNativeQuery("UPDATE purchase_orders SET vendor_order_code=?1, updated_at=now() WHERE id=?2 AND tenant_id=?3")
                .setParameter(1, soCode).setParameter(2, poId).setParameter(3, dealerTenant)
                .executeUpdate();

        log.info("[collab] 采购单 {} 转厂家草稿销售单 {} (mfr={}, dealer={})", poCode, soCode, mfrTenant, dealerTenant);
        return soCode;
    }

    /** 厂家销售出库发货后：向经销商租户推送待收货入库单（无采购单则自动补建已审批采购单）。 */
    public void onSalesOutShipped(Long salesOutId, List<ShippedLine> shipped) {
        UUID mfrTenant = TenantContext.getTenantId();
        if (mfrTenant == null || shipped == null || shipped.isEmpty()) return;

        List<Tuple> soRows = em.createNativeQuery(
                "SELECT so.id, so.code, so.dealer_id, so.source_order_id FROM sales_outs so " +
                "WHERE so.id=?1 AND so.tenant_id=?2 AND so.deleted_at IS NULL", Tuple.class)
                .setParameter(1, salesOutId).setParameter(2, mfrTenant).getResultList();
        if (soRows.isEmpty()) return;
        Tuple so = soRows.get(0);
        Long mfrDealerId = lng(so.get("dealer_id"));
        if (mfrDealerId == null) return;

        // 幂等按"出库单 + 本次发货执行行"粒度：同一张出库单分批发货，每批回传一张收货单；已回传过的执行行跳过
        List<?> doneRefRows = em.createNativeQuery(
                "SELECT COALESCE(CAST(line_refs AS text), '[]') FROM cross_tenant_doc_links " +
                "WHERE sales_out_id=?1 AND link_type='SALES_OUT_TO_RECEIPT'")
                .setParameter(1, salesOutId).getResultList();
        java.util.Set<Long> doneOutLineIds = new java.util.HashSet<>();
        for (Object row : doneRefRows) {
            try {
                for (com.fasterxml.jackson.databind.JsonNode n : mapper.readTree(String.valueOf(row))) {
                    if (n.hasNonNull("outLineId")) doneOutLineIds.add(n.get("outLineId").asLong());
                }
            } catch (Exception ignore) { }
        }
        List<ShippedLine> pending = new ArrayList<>();
        for (ShippedLine sl : shipped) {
            if (sl.getOutLineId() != null && doneOutLineIds.contains(sl.getOutLineId())) continue;
            pending.add(sl);
        }
        if (pending.isEmpty()) {
            log.info("[collab] 出库单 {} 本次发货执行行均已回传过，跳过", salesOutId);
            return;
        }
        shipped = pending;

        UUID dealerTenant = resolveDealerTenant(mfrTenant, mfrDealerId);
        if (dealerTenant == null) {
            log.info("[collab] 厂家客户 {} 未绑定经销商租户，跳过 so={}", mfrDealerId, salesOutId);
            return;
        }

        Long sourceOrderId = lng(so.get("source_order_id"));
        String salesOrderCode = null;
        if (sourceOrderId != null) {
            List<Tuple> o = em.createNativeQuery("SELECT code FROM orders WHERE id=?1 AND tenant_id=?2", Tuple.class)
                    .setParameter(1, sourceOrderId).setParameter(2, mfrTenant).getResultList();
            if (!o.isEmpty()) salesOrderCode = str(o.get(0).get("code"));
        }

        StringBuilder missing = new StringBuilder();
        List<ShippedLine> mapped = new ArrayList<>();
        for (ShippedLine sl : shipped) {
            Tuple map = findMappingByMfrProduct(mfrTenant, dealerTenant, sl.getProductId());
            if (map == null) {
                if (missing.length() > 0) missing.append("、");
                missing.append("[").append(str(sl.getProductCode())).append("]");
                continue;
            }
            sl.setDealerProductId(lng(map.get("dealer_product_id")));
            sl.setDealerProductCode(str(map.get("dealer_product_code")));
            mapped.add(sl);
        }
        if (missing.length() > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "以下物料厂家尚未对码，出库回传失败，请厂家先完成对码后重新发货：" + missing);
        }

        UUID savedCtx = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(dealerTenant);

            Long poId;
            String poCode;
            // 路径A 台账存在 -> 经销商正式采购单（数量=订购量，不得累加）；否则回退复用路径B 已自动补建的采购单（数量需按批累计）
            Tuple linkRowA = linkRowBySalesOrder(sourceOrderId);
            Tuple linkRowB = linkRowA != null ? null : linkRowBySalesOut(sourceOrderId, salesOutId);
            boolean autoPoCreated = false;
            boolean autoPoAccumulate = false;
            if (linkRowA != null || linkRowB != null) {
                Tuple linkRow = linkRowA != null ? linkRowA : linkRowB;
                poId = lng(linkRow.get("po_id"));
                poCode = str(linkRow.get("po_code"));
                autoPoAccumulate = linkRowA == null;
            } else {
                poCode = docNoGenerator.next("PO");
                Long supplierId = resolvePlatformSupplierId(dealerTenant, mfrTenant);
                Object pid = em.createNativeQuery(
                        "INSERT INTO purchase_orders (tenant_id, code, order_type, supplier_id, supplier_name, " +
                        "amount_incl_tax, final_amount, status, vendor_order_code, remark, submitted_at, approved_at, created_at, updated_at) " +
                        "VALUES (?1,?2,'NORMAL',?3,'平台厂家',0,0,'APPROVED',?4,?5, now(), now(), now(), now()) RETURNING id")
                        .setParameter(1, dealerTenant).setParameter(2, poCode).setParameter(3, supplierId)
                        .setParameter(4, salesOrderCode)
                        .setParameter(5, "厂家销售出库 " + str(so.get("code")) + " 回传自动生成采购单（已审批）")
                        .getSingleResult();
                poId = ((Number) pid).longValue();
                autoPoCreated = true;
                autoPoAccumulate = true;
                log.info("[collab] 路径B：自动补建经销商采购单 {} (dealer={})", poCode, dealerTenant);
            }

            if (autoPoCreated) {
                int seq = 0;
                for (ShippedLine sl : mapped) {
                    em.createNativeQuery(
                            "INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal, created_at) " +
                            "VALUES (?1,?2,?3,?4,0,0,0.13,0, now())")
                            .setParameter(1, poId).setParameter(2, ++seq)
                            .setParameter(3, sl.getDealerProductId()).setParameter(4, sl.getQty())
                            .executeUpdate();
                }
            } else if (autoPoAccumulate) {
                int seq = nextPoLineSeq(poId);
                for (ShippedLine sl : mapped) {
                    upsertPoLineQty(poId, sl.getDealerProductId(), sl.getQty(), ++seq);
                }
            }

            // 收货入库必须落到经销商自身主体 + 具体仓库（confirm-full 用 receipt.dealer_id/warehouse_id 写库存，
            // 为空会产生无仓库归属的幽灵库存）；经销商租户缺少自身主体/仓库时自动补齐
            Long dealerSelfId = resolveOrCreateDealerSelf(dealerTenant);
            Long dealerWarehouseId = resolveOrCreateDefaultWarehouse(dealerTenant, dealerSelfId);

            String receiptCode = docNoGenerator.next("RK");
            Object rid = em.createNativeQuery(
                    "INSERT INTO receipts (tenant_id, code, receipt_type, ref_doc_type, ref_doc_id, source_po_id, " +
                    "dealer_id, warehouse_id, status, remark, created_at, updated_at) " +
                    "VALUES (?1,?2,'PURCHASE','sales_out',?3,?4,?5,?6,'PENDING',?7, now(), now()) RETURNING id")
                    .setParameter(1, dealerTenant).setParameter(2, receiptCode)
                    .setParameter(3, salesOutId).setParameter(4, poId)
                    .setParameter(5, dealerSelfId).setParameter(6, dealerWarehouseId)
                    .setParameter(7, "厂家销售出库 " + str(so.get("code"))
                            + (salesOrderCode != null ? "（销售单 " + salesOrderCode + "）" : "") + " 回传待收货")
                    .getSingleResult();
            Long receiptId = ((Number) rid).longValue();
            for (ShippedLine sl : mapped) {
                em.createNativeQuery(
                        "INSERT INTO receipt_lines (receipt_id, product_id, batch_no, serial_no, expected_qty, received_qty, created_at) " +
                        "VALUES (?1,?2,?3,?4,?5,0, now())")
                        .setParameter(1, receiptId).setParameter(2, sl.getDealerProductId())
                        .setParameter(3, sl.getBatchNo()).setParameter(4, sl.getSerialNo())
                        .setParameter(5, sl.getQty())
                        .executeUpdate();
            }

            List<Map<String, Object>> lineRefs = new ArrayList<>();
            for (ShippedLine sl : mapped) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("mfrProductCode", sl.getProductCode());
                ref.put("dealerProductCode", sl.getDealerProductCode());
                ref.put("qty", sl.getQty());
                ref.put("batchNo", sl.getBatchNo());
                ref.put("serialNo", sl.getSerialNo());
                ref.put("outLineId", sl.getOutLineId());
                lineRefs.add(ref);
            }
            em.createNativeQuery(
                    "INSERT INTO cross_tenant_doc_links (manufacturer_tenant_id, dealer_tenant_id, link_type, po_id, po_code, " +
                    "sales_order_id, sales_order_code, sales_out_id, sales_out_code, receipt_id, receipt_code, line_refs, status, created_at, updated_at) " +
                    "VALUES (?1,?2,'SALES_OUT_TO_RECEIPT',?3,?4,?5,?6,?7,?8,?9,?10, CAST(?11 AS jsonb),'linked',now(),now())")
                    .setParameter(1, mfrTenant).setParameter(2, dealerTenant)
                    .setParameter(3, poId).setParameter(4, poCode)
                    .setParameter(5, sourceOrderId).setParameter(6, salesOrderCode)
                    .setParameter(7, salesOutId).setParameter(8, str(so.get("code")))
                    .setParameter(9, receiptId).setParameter(10, receiptCode)
                    .setParameter(11, toJson(lineRefs))
                    .executeUpdate();

            log.info("[collab] 出库 {} 回传经销商收货单 {} (dealer={}, po={})",
                    str(so.get("code")), receiptCode, dealerTenant, poCode);
        } finally {
            TenantContext.setTenantId(savedCtx);
        }
    }

    /**
     * 路径C（反向）：经销商采退订单（红字采购单，供应商=平台厂家）提交后，
     * 在厂家租户生成一张红字销退订单草稿（orders.is_red=true，DRAFT），并回写厂家销退单号。
     * 非平台厂家供应商（不触发协同）返回 null。
     */
    public String onPurchaseReturnSubmitted(Long prPoId) {
        UUID dealerTenant = TenantContext.getTenantId();
        if (dealerTenant == null) return null;

        List<Tuple> poRows = em.createNativeQuery(
                "SELECT po.id, po.code, po.supplier_id, po.remark, po.return_reason, s.manufacturer_tenant_id " +
                "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
                "WHERE po.id = ?1 AND po.tenant_id = ?2 AND po.deleted_at IS NULL AND COALESCE(po.is_red,false)=true", Tuple.class)
                .setParameter(1, prPoId).setParameter(2, dealerTenant).getResultList();
        if (poRows.isEmpty()) return null;
        Tuple po = poRows.get(0);
        UUID mfrTenant = toUuid(po.get("manufacturer_tenant_id"));
        if (mfrTenant == null) return null;

        List<?> exist = em.createNativeQuery(
                "SELECT sales_order_code FROM cross_tenant_doc_links WHERE po_id = ?1 AND link_type='PR_TO_RED_SALES_ORDER'")
                .setParameter(1, prPoId).getResultList();
        if (!exist.isEmpty() && exist.get(0) != null) return String.valueOf(exist.get(0));

        Long mfrDealerId = resolveMfrDealerId(mfrTenant, dealerTenant);
        if (mfrDealerId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "经销商租户尚未绑定厂家客户主数据，采退无法转厂家销退单，请联系厂家完成绑定");
        }

        List<Tuple> lines = em.createNativeQuery(
                "SELECT pol.id, pol.seq, pol.product_id, pol.qty, p.code AS product_code, p.name_cn AS product_name " +
                "FROM purchase_order_lines pol LEFT JOIN products p ON p.id = pol.product_id " +
                "WHERE pol.po_id = ?1 ORDER BY pol.seq, pol.id", Tuple.class)
                .setParameter(1, prPoId).getResultList();
        if (lines.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "采退订单明细为空，无法转销退订单");

        List<Map<String, Object>> lineRefs = new ArrayList<>();
        List<Object[]> mapped = new ArrayList<>();
        StringBuilder missing = new StringBuilder();
        for (Tuple ln : lines) {
            Tuple map = findMappingByDealerProduct(mfrTenant, dealerTenant, lng(ln.get("product_id")));
            if (map == null) {
                if (missing.length() > 0) missing.append("、");
                missing.append("[").append(str(ln.get("product_code"))).append(" ")
                       .append(str(ln.get("product_name"))).append("]");
                continue;
            }
            mapped.add(new Object[]{lng(map.get("manufacturer_product_id")), bd(ln.get("qty")),
                    lng(ln.get("id")), lng(ln.get("seq"))});
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("prLineId", lng(ln.get("id")));
            ref.put("prSeq", lng(ln.get("seq")));
            ref.put("dealerProductCode", str(ln.get("product_code")));
            ref.put("mfrProductId", lng(map.get("manufacturer_product_id")));
            ref.put("mfrProductCode", str(map.get("manufacturer_product_code")));
            ref.put("qty", bd(ln.get("qty")));
            lineRefs.add(ref);
        }
        if (missing.length() > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "以下物料厂家尚未对码，采退无法转销退单，请厂家先完成对码：" + missing);
        }

        String prCode = str(po.get("code"));
        String reason = str(po.get("return_reason"));
        String prRemark = po.get("remark") == null ? "" : str(po.get("remark"));
        String redSoCode = docNoGenerator.next("SO");
        Long redSoId;
        UUID savedCtx = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(mfrTenant);
            Object ins = em.createNativeQuery(
                    "INSERT INTO orders (tenant_id, code, order_type, is_red, dealer_id, amount_incl_tax, discount_amount, final_amount, " +
                    "status, remark, customer_po_code, ship_snapshot, created_at, updated_at) " +
                    "VALUES (?1, ?2, 'NORMAL', true, ?3, 0, 0, 0, 'DRAFT', ?4, ?5, CAST('{}' AS jsonb), now(), now()) RETURNING id")
                    .setParameter(1, mfrTenant).setParameter(2, redSoCode).setParameter(3, mfrDealerId)
                    .setParameter(4, "经销商采退单 " + prCode + " 自动转入红字销退（草稿，待审批）"
                            + (reason == null || reason.isBlank() ? "" : "；退货原因：" + reason)
                            + (prRemark.isBlank() ? "" : "；采退备注：" + prRemark))
                    .setParameter(5, prCode)
                    .getSingleResult();
            redSoId = ((Number) ins).longValue();
            int seq = 1;
            for (Object[] ml : mapped) {
                em.createNativeQuery(
                        "INSERT INTO order_lines (order_id, seq, product_id, qty, unit_price, tax_rate, sub_total, " +
                        "line_discount_value, line_discount_amount, header_discount_amount, is_gift, created_at, updated_at) " +
                        "VALUES (?1, ?2, ?3, ?4, 0, 0.13, 0, 0, 0, 0, false, now(), now())")
                        .setParameter(1, redSoId).setParameter(2, seq++)
                        .setParameter(3, ml[0]).setParameter(4, (BigDecimal) ml[1])
                        .executeUpdate();
            }
        } finally {
            TenantContext.setTenantId(savedCtx);
        }

        em.createNativeQuery(
                "INSERT INTO cross_tenant_doc_links (manufacturer_tenant_id, dealer_tenant_id, link_type, po_id, po_code, " +
                "sales_order_id, sales_order_code, line_refs, status, created_at, updated_at) " +
                "VALUES (?1,?2,'PR_TO_RED_SALES_ORDER',?3,?4,?5,?6, CAST(?7 AS jsonb),'linked',now(),now())")
                .setParameter(1, mfrTenant).setParameter(2, dealerTenant)
                .setParameter(3, prPoId).setParameter(4, prCode)
                .setParameter(5, redSoId).setParameter(6, redSoCode).setParameter(7, toJson(lineRefs))
                .executeUpdate();
        em.createNativeQuery("UPDATE purchase_orders SET vendor_order_code=?1, updated_at=now() WHERE id=?2 AND tenant_id=?3")
                .setParameter(1, redSoCode).setParameter(2, prPoId).setParameter(3, dealerTenant)
                .executeUpdate();

        log.info("[collab] 采退单 {} 转厂家红字销退草稿 {} (mfr={}, dealer={})", prCode, redSoCode, mfrTenant, dealerTenant);
        return redSoCode;
    }
    /**
     * 路径D（反向）：经销商红字销售出库（采退 RGI 发货）后，在厂家租户生成一张红字销退入库单（待收货 PENDING）。
     * 仅当发货方为经销商租户、且该红字出库来源于一张已协同（路径C）的采退单时触发；否则静默跳过。
     * 幂等：按"红字出库单 + 本次发货执行行"粒度去重，分批发货每批一张红字入库单。
     */
    public void onRedSalesOutShipped(Long dealerSalesOutId, List<ShippedLine> shipped) {
        UUID dealerTenant = TenantContext.getTenantId();
        if (dealerTenant == null || shipped == null || shipped.isEmpty()) return;

        List<Tuple> soRows = em.createNativeQuery(
                "SELECT so.id, so.code, so.source_po_id FROM sales_outs so " +
                "WHERE so.id=?1 AND so.tenant_id=?2 AND so.deleted_at IS NULL AND COALESCE(so.is_red,false)=true", Tuple.class)
                .setParameter(1, dealerSalesOutId).setParameter(2, dealerTenant).getResultList();
        if (soRows.isEmpty()) return;
        Tuple so = soRows.get(0);
        Long prPoId = lng(so.get("source_po_id"));
        if (prPoId == null) return;

        List<?> doneRefRows = em.createNativeQuery(
                "SELECT COALESCE(CAST(line_refs AS text), '[]') FROM cross_tenant_doc_links " +
                "WHERE sales_out_id=?1 AND link_type='RED_OUT_TO_RED_RECEIPT'")
                .setParameter(1, dealerSalesOutId).getResultList();
        java.util.Set<Long> doneOutLineIds = new java.util.HashSet<>();
        for (Object row : doneRefRows) {
            try {
                for (com.fasterxml.jackson.databind.JsonNode n : mapper.readTree(String.valueOf(row))) {
                    if (n.hasNonNull("outLineId")) doneOutLineIds.add(n.get("outLineId").asLong());
                }
            } catch (Exception ignore) { }
        }
        List<ShippedLine> pending = new ArrayList<>();
        for (ShippedLine sl : shipped) {
            if (sl.getOutLineId() != null && doneOutLineIds.contains(sl.getOutLineId())) continue;
            pending.add(sl);
        }
        if (pending.isEmpty()) {
            log.info("[collab] 红字出库 {} 本次发货行均已回传过，跳过", dealerSalesOutId);
            return;
        }
        shipped = pending;

        Tuple linkRow = (Tuple) em.createNativeQuery(
                "SELECT manufacturer_tenant_id, sales_order_id, sales_order_code, po_code FROM cross_tenant_doc_links " +
                "WHERE po_id=?1 AND link_type='PR_TO_RED_SALES_ORDER' ORDER BY id DESC LIMIT 1", Tuple.class)
                .setParameter(1, prPoId).getResultList().stream().findFirst().orElse(null);
        if (linkRow == null) return;
        UUID mfrTenant = toUuid(linkRow.get("manufacturer_tenant_id"));
        if (mfrTenant == null) return;
        Long redSalesOrderId = lng(linkRow.get("sales_order_id"));
        String redSalesOrderCode = str(linkRow.get("sales_order_code"));
        String prCode = str(linkRow.get("po_code"));

        StringBuilder missing = new StringBuilder();
        List<ShippedLine> mapped = new ArrayList<>();
        for (ShippedLine sl : shipped) {
            Tuple map = findMappingByDealerProduct(mfrTenant, dealerTenant, sl.getProductId());
            if (map == null) {
                if (missing.length() > 0) missing.append("、");
                missing.append("[").append(str(sl.getProductCode())).append("]");
                continue;
            }
            // 经销商产品 -> 厂家产品（用 dealerProduct* 字段承载厂家产品信息）
            sl.setDealerProductId(lng(map.get("manufacturer_product_id")));
            sl.setDealerProductCode(str(map.get("manufacturer_product_code")));
            mapped.add(sl);
        }
        if (missing.length() > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "以下物料厂家尚未对码，红字出库回传失败，请厂家先完成对码后重新发货：" + missing);
        }

        UUID savedCtx = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(mfrTenant);

            Long mfrDealerId = resolveMfrDealerId(mfrTenant, dealerTenant);
            Long mfrWarehouseId = resolveOrCreateDefaultWarehouse(mfrTenant, mfrDealerId);

            String receiptCode = docNoGenerator.next("RGR");
            Object rid = em.createNativeQuery(
                    "INSERT INTO receipts (tenant_id, code, receipt_type, ref_doc_type, ref_doc_id, is_red, " +
                    "dealer_id, warehouse_id, status, auto_created, remark, created_at, updated_at) " +
                    "VALUES (?1,?2,'SALES_RETURN','sales_return',?3,true,?4,?5,'PENDING',true,?6, now(), now()) RETURNING id")
                    .setParameter(1, mfrTenant).setParameter(2, receiptCode)
                    .setParameter(3, redSalesOrderId)
                    .setParameter(4, mfrDealerId).setParameter(5, mfrWarehouseId)
                    .setParameter(6, "经销商红字采退出库 " + str(so.get("code"))
                            + (redSalesOrderCode != null ? "（红字销退单 " + redSalesOrderCode + "）" : "") + " 回传待收货")
                    .getSingleResult();
            Long receiptId = ((Number) rid).longValue();
            for (ShippedLine sl : mapped) {
                em.createNativeQuery(
                        "INSERT INTO receipt_lines (receipt_id, product_id, batch_no, serial_no, expected_qty, received_qty, created_at) " +
                        "VALUES (?1,?2,?3,?4,?5,0, now())")
                        .setParameter(1, receiptId).setParameter(2, sl.getDealerProductId())
                        .setParameter(3, sl.getBatchNo()).setParameter(4, sl.getSerialNo())
                        .setParameter(5, sl.getQty())
                        .executeUpdate();
            }

            List<Map<String, Object>> lineRefs = new ArrayList<>();
            for (ShippedLine sl : mapped) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("dealerProductCode", sl.getProductCode());
                ref.put("mfrProductCode", sl.getDealerProductCode());
                ref.put("qty", sl.getQty());
                ref.put("batchNo", sl.getBatchNo());
                ref.put("serialNo", sl.getSerialNo());
                ref.put("outLineId", sl.getOutLineId());
                lineRefs.add(ref);
            }
            em.createNativeQuery(
                    "INSERT INTO cross_tenant_doc_links (manufacturer_tenant_id, dealer_tenant_id, link_type, po_id, po_code, " +
                    "sales_order_id, sales_order_code, sales_out_id, sales_out_code, receipt_id, receipt_code, line_refs, status, created_at, updated_at) " +
                    "VALUES (?1,?2,'RED_OUT_TO_RED_RECEIPT',?3,?4,?5,?6,?7,?8,?9,?10, CAST(?11 AS jsonb),'linked',now(),now())")
                    .setParameter(1, mfrTenant).setParameter(2, dealerTenant)
                    .setParameter(3, prPoId).setParameter(4, prCode)
                    .setParameter(5, redSalesOrderId).setParameter(6, redSalesOrderCode)
                    .setParameter(7, dealerSalesOutId).setParameter(8, str(so.get("code")))
                    .setParameter(9, receiptId).setParameter(10, receiptCode)
                    .setParameter(11, toJson(lineRefs))
                    .executeUpdate();

            log.info("[collab] 红字出库 {} 回传厂家红字销退入库 {} (mfr={}, dealer={}, redSo={})",
                    str(so.get("code")), receiptCode, mfrTenant, dealerTenant, redSalesOrderCode);
        } finally {
            TenantContext.setTenantId(savedCtx);
        }
    }

    // ==================== 解析 / 对码辅助 ====================

    /** 厂家租户内：经销商租户 -> 厂家侧 dealer 主数据 id。 */
    private Long resolveMfrDealerId(UUID mfrTenant, UUID dealerTenant) {
        List<?> rs = em.createNativeQuery(
                "SELECT b.dealer_id FROM tenant_dealer_bindings b " +
                "WHERE b.manufacturer_tenant_id=?1 AND b.dealer_tenant_id=?2 AND b.status='active' AND b.deleted_at IS NULL")
                .setParameter(1, mfrTenant).setParameter(2, dealerTenant).getResultList();
        return rs.isEmpty() ? null : lng(rs.get(0));
    }

    /** 厂家租户内：厂家侧 dealer 主数据 -> 绑定的经销商租户。 */
    private UUID resolveDealerTenant(UUID mfrTenant, Long mfrDealerId) {
        List<?> rs = em.createNativeQuery(
                "SELECT b.dealer_tenant_id FROM tenant_dealer_bindings b " +
                "WHERE b.manufacturer_tenant_id=?1 AND b.dealer_id=?2 AND b.status='active' AND b.deleted_at IS NULL")
                .setParameter(1, mfrTenant).setParameter(2, mfrDealerId).getResultList();
        return rs.isEmpty() ? null : toUuid(rs.get(0));
    }

    /** 经销商租户内：平台厂家供应商 id（没有则返回 null，采购单仍可建，只是不触发回传链路补单）。 */
    private Long resolvePlatformSupplierId(UUID dealerTenant, UUID mfrTenant) {
        List<?> rs = em.createNativeQuery(
                "SELECT id FROM suppliers WHERE tenant_id=?1 AND manufacturer_tenant_id=?2 AND deleted_at IS NULL ORDER BY id LIMIT 1")
                .setParameter(1, dealerTenant).setParameter(2, mfrTenant).getResultList();
        return rs.isEmpty() ? null : lng(rs.get(0));
    }

    /** 经销商租户内：自身经销商主体 id（协同收货入库的归属主体）；缺失时自动补建。 */
    private Long resolveOrCreateDealerSelf(UUID dealerTenant) {
        List<?> rs = em.createNativeQuery(
                "SELECT id FROM dealers WHERE tenant_id=?1 AND deleted_at IS NULL ORDER BY id LIMIT 1")
                .setParameter(1, dealerTenant).getResultList();
        if (!rs.isEmpty()) return lng(rs.get(0));
        Object did = em.createNativeQuery(
                "INSERT INTO dealers (tenant_id, code, name, status, created_at, updated_at) " +
                "VALUES (?1,'DEALER-SELF','本企业','active', now(), now()) RETURNING id")
                .setParameter(1, dealerTenant).getSingleResult();
        Long id = ((Number) did).longValue();
        log.info("[collab] 经销商租户 {} 缺少自身主体，自动补建 dealer id={}", dealerTenant, id);
        return id;
    }

    /** 经销商租户内：默认入库仓库（协同收货落库仓库）；缺失时自动补建主仓库。 */
    private Long resolveOrCreateDefaultWarehouse(UUID dealerTenant, Long dealerSelfId) {
        List<?> rs = em.createNativeQuery(
                "SELECT id FROM warehouses WHERE tenant_id=?1 AND deleted_at IS NULL ORDER BY id LIMIT 1")
                .setParameter(1, dealerTenant).getResultList();
        if (!rs.isEmpty()) return lng(rs.get(0));
        Object wid = em.createNativeQuery(
                "INSERT INTO warehouses (tenant_id, dealer_id, code, name, type, status, created_at, updated_at) " +
                "VALUES (?1,?2,'COLLAB-DEFAULT-WH','协同默认仓','main','active', now(), now()) RETURNING id")
                .setParameter(1, dealerTenant).setParameter(2, dealerSelfId).getSingleResult();
        Long id = ((Number) wid).longValue();
        log.info("[collab] 经销商租户 {} 缺少仓库，自动补建默认仓 id={}", dealerTenant, id);
        return id;
    }

    /** 路径B：按厂家销售订单 id 找路径A台账中的经销商采购单。 */
    private Tuple linkRowBySalesOrder(Long salesOrderId) {
        if (salesOrderId == null) return null;
        List<Tuple> rs = em.createNativeQuery(
                "SELECT po_id, po_code FROM cross_tenant_doc_links " +
                "WHERE sales_order_id=?1 AND link_type='PO_TO_SALES_ORDER' AND po_id IS NOT NULL", Tuple.class)
                .setParameter(1, salesOrderId).getResultList();
        return rs.isEmpty() ? null : rs.get(0);
    }

    /** 路径B 回退：路径A台账缺失时，复用上一批出库回传自动补建的经销商采购单（避免分批发货重复补 PO）。 */
    private Tuple linkRowBySalesOut(Long salesOrderId, Long salesOutId) {
        List<Tuple> rs = em.createNativeQuery(
                "SELECT l.po_id, l.po_code FROM cross_tenant_doc_links l " +
                "WHERE l.link_type='SALES_OUT_TO_RECEIPT' AND l.po_id IS NOT NULL " +
                "AND ((?1 IS NOT NULL AND l.sales_order_id = ?1) OR l.sales_out_id = ?2) " +
                "ORDER BY l.id DESC LIMIT 1", Tuple.class)
                .setParameter(1, salesOrderId).setParameter(2, salesOutId).getResultList();
        return rs.isEmpty() ? null : rs.get(0);
    }

    /** 经销商采购单行序号最大值（自动补建 PO 分批追加用）。 */
    private int nextPoLineSeq(Long poId) {
        Object v = em.createNativeQuery("SELECT COALESCE(MAX(seq),0) FROM purchase_order_lines WHERE po_id=?1")
                .setParameter(1, poId).getSingleResult();
        return v == null ? 0 : ((Number) v).intValue();
    }

    /** 路径B 复用采购单：同产品行累加数量，无行则新增（累计到自动补建的采购单上）。 */
    private void upsertPoLineQty(Long poId, Long dealerProductId, BigDecimal qty, int nextSeq) {
        List<?> exist = em.createNativeQuery(
                "SELECT id FROM purchase_order_lines WHERE po_id=?1 AND product_id=?2")
                .setParameter(1, poId).setParameter(2, dealerProductId).getResultList();
        if (!exist.isEmpty()) {
            em.createNativeQuery(
                    "UPDATE purchase_order_lines SET qty = COALESCE(qty,0) + ?3 " +
                    "WHERE po_id=?1 AND product_id=?2")
                    .setParameter(1, poId).setParameter(2, dealerProductId).setParameter(3, qty)
                    .executeUpdate();
        } else {
            em.createNativeQuery(
                    "INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal, created_at) " +
                    "VALUES (?1,?2,?3,?4,0,0,0.13,0, now())")
                    .setParameter(1, poId).setParameter(2, nextSeq)
                    .setParameter(3, dealerProductId).setParameter(4, qty)
                    .executeUpdate();
        }
    }

    /** 对码：经销商产品 -> 厂家产品行。 */
    private Tuple findMappingByDealerProduct(UUID mfrTenant, UUID dealerTenant, Long dealerProductId) {
        if (dealerProductId == null) return null;
        List<Tuple> rs = em.createNativeQuery(
                "SELECT manufacturer_product_id, manufacturer_product_code, dealer_product_id, dealer_product_code " +
                "FROM product_mappings WHERE manufacturer_tenant_id=?1 AND dealer_tenant_id=?2 " +
                "AND dealer_product_id=?3 AND status='active' AND deleted_at IS NULL LIMIT 1", Tuple.class)
                .setParameter(1, mfrTenant).setParameter(2, dealerTenant).setParameter(3, dealerProductId)
                .getResultList();
        return rs.isEmpty() ? null : rs.get(0);
    }

    /** 对码：厂家产品 -> 经销商产品行。 */
    private Tuple findMappingByMfrProduct(UUID mfrTenant, UUID dealerTenant, Long mfrProductId) {
        if (mfrProductId == null) return null;
        List<Tuple> rs = em.createNativeQuery(
                "SELECT manufacturer_product_id, manufacturer_product_code, dealer_product_id, dealer_product_code " +
                "FROM product_mappings WHERE manufacturer_tenant_id=?1 AND dealer_tenant_id=?2 " +
                "AND manufacturer_product_id=?3 AND status='active' AND deleted_at IS NULL LIMIT 1", Tuple.class)
                .setParameter(1, mfrTenant).setParameter(2, dealerTenant).setParameter(3, mfrProductId)
                .getResultList();
        return rs.isEmpty() ? null : rs.get(0);
    }

    private String toJson(Object o) {
        try { return mapper.writeValueAsString(o); } catch (Exception e) { return "[]"; }
    }

    private static Long lng(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static BigDecimal bd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static UUID toUuid(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u;
        try { return UUID.fromString(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}