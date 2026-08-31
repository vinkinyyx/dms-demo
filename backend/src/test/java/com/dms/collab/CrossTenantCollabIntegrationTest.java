/*
 * v4.5.0 跨租户协同服务集成测试。
 * 路径A：经销商采购单提交 -> 厂家草稿销售订单（对码转换、台账、vendor_order_code 回写、幂等）。
 * 路径B：厂家销售出库发货 -> 经销商待收货单（自动补建已审批采购单、分批累计、outLineId 幂等、
 *        路径A 台账存在时复用正式采购单不累加、DEALER-SELF/COLLAB-DEFAULT-WH 自动补建）。
 * 基于内嵌 PostgreSQL + 全量 Flyway 迁移，@Transactional 每方法回滚。
 */
package com.dms.collab;

import com.dms.BaseIntegrationTest;
import com.dms.common.BusinessException;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.entity.Product;
import com.dms.tenant.entity.Tenant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrossTenantCollabIntegrationTest extends BaseIntegrationTest {

    @Autowired
    EntityManager em;

    private CrossTenantCollabService collab() {
        return applicationContext.getBean(CrossTenantCollabService.class);
    }

    private static final class Ctx {
        UUID mfrTid;
        UUID dealerTid;
        Long mfrProductId;
        Long dealerProductId;
        String mfrProductCode;
        String dealerProductCode;
        Long mfrDealerId;
        Long supplierId;
    }

    // ==================== 场景搭建 ====================

    private Ctx setupPair(boolean binding, boolean mapping) {
        Ctx c = new Ctx();
        Tenant mfr = createTestTenant("CLBM" + UUID.randomUUID().toString().substring(0, 8));
        Tenant dealer = createTestTenant("CLBD" + UUID.randomUUID().toString().substring(0, 8));
        c.mfrTid = mfr.getId();
        c.dealerTid = dealer.getId();

        Product mp = createTestProduct(c.mfrTid, "MFR-P" + UUID.randomUUID().toString().substring(0, 6), "Mfr Product");
        Product dp = createTestProduct(c.dealerTid, "D-P" + UUID.randomUUID().toString().substring(0, 6), "Dealer Product");
        c.mfrProductId = mp.getId();
        c.dealerProductId = dp.getId();
        c.mfrProductCode = mp.getCode();
        c.dealerProductCode = dp.getCode();

        Dealer md = createTestDealer(c.mfrTid, "D-CLB" + UUID.randomUUID().toString().substring(0, 6), "Mfr-side dealer");
        c.mfrDealerId = md.getId();

        if (binding) {
            em.createNativeQuery("INSERT INTO tenant_dealer_bindings " +
                            "(dealer_tenant_id, manufacturer_tenant_id, dealer_id, status, created_at, updated_at) " +
                            "VALUES (?1,?2,?3,'active',now(),now())")
                    .setParameter(1, c.dealerTid).setParameter(2, c.mfrTid).setParameter(3, c.mfrDealerId)
                    .executeUpdate();
        }
        if (mapping) {
            em.createNativeQuery("INSERT INTO product_mappings " +
                            "(manufacturer_tenant_id, dealer_tenant_id, manufacturer_product_id, dealer_product_id, " +
                            "manufacturer_product_code, dealer_product_code, package_unit, conversion_rate, status, created_at, updated_at) " +
                            "VALUES (?1,?2,?3,?4,?5,?6,'box',1,'active',now(),now())")
                    .setParameter(1, c.mfrTid).setParameter(2, c.dealerTid)
                    .setParameter(3, c.mfrProductId).setParameter(4, c.dealerProductId)
                    .setParameter(5, c.mfrProductCode).setParameter(6, c.dealerProductCode)
                    .executeUpdate();
        }
        c.supplierId = ins("INSERT INTO suppliers (tenant_id, code, name, status, manufacturer_tenant_id, created_at, updated_at) " +
                        "VALUES (?1,?2,'平台厂家','active',?3,now(),now()) RETURNING id",
                c.dealerTid, "SUP-CLB" + UUID.randomUUID().toString().substring(0, 6), c.mfrTid);
        return c;
    }

    private Long createPo(Ctx c, Long productId, BigDecimal qty, Long supplierId) {
        String code = "PO-CLB" + UUID.randomUUID().toString().substring(0, 8);
        Long poId = ins("INSERT INTO purchase_orders (tenant_id, code, order_type, supplier_id, supplier_name, status, " +
                        "amount_incl_tax, final_amount, remark, created_at, updated_at) " +
                        "VALUES (?1,?2,'NORMAL',?3,'平台厂家','DRAFT',0,0,'collab-test',now(),now()) RETURNING id",
                c.dealerTid, code, supplierId);
        ins("INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal, created_at) " +
                        "VALUES (?1,1,?2,?3,0,0,0.13,0,now()) RETURNING id",
                poId, productId, qty);
        return poId;
    }

    private Long createMfrSalesOrder(Ctx c) {
        return ins("INSERT INTO orders (tenant_id, code, order_type, dealer_id, status, amount_incl_tax, discount_amount, final_amount, " +
                        "ship_snapshot, created_at, updated_at) " +
                        "VALUES (?1,?2,'NORMAL',?3,'APPROVED',0,0,0,CAST('{}' AS jsonb),now(),now()) RETURNING id",
                c.mfrTid, "SO-CLB" + UUID.randomUUID().toString().substring(0, 8), c.mfrDealerId);
    }

    private Long createSalesOut(Ctx c, Long sourceOrderId) {
        String code = "GI-CLB" + UUID.randomUUID().toString().substring(0, 8);
        return ins("INSERT INTO sales_outs (tenant_id, code, dealer_id, business_type, sales_date, status, is_red, " +
                        "source_order_id, amount_incl_tax, created_at, updated_at, version) " +
                        "VALUES (?1,?2,?3,'sales',CURRENT_DATE,'SHIPPED',false,CAST(?4 AS BIGINT),0,now(),now(),0) RETURNING id",
                c.mfrTid, code, c.mfrDealerId, sourceOrderId);
    }

    private Long createSalesOutLine(Long salesOutId, Long productId, String batch, String serial, BigDecimal qty) {
        return ins("INSERT INTO sales_out_lines (sales_out_id, product_id, batch_no, serial_no, qty, created_at) " +
                        "VALUES (?1,?2,?3,?4,?5,now()) RETURNING id",
                salesOutId, productId, batch, serial, qty);
    }

    private ShippedLine shipped(Ctx c, Long outLineId, String batch, String serial, BigDecimal qty) {
        ShippedLine sl = new ShippedLine();
        sl.setProductId(c.mfrProductId);
        sl.setProductCode(c.mfrProductCode);
        sl.setQty(qty);
        sl.setBatchNo(batch);
        sl.setSerialNo(serial);
        sl.setOutLineId(outLineId);
        return sl;
    }

    // ==================== 路径A：采购单 -> 厂家草稿销售订单 ====================

    @Test
    void pathA_poSubmitted_createsDraftSalesOrderAndLink() {
        Ctx c = setupPair(true, true);
        Long poId = createPo(c, c.dealerProductId, new BigDecimal("10"), c.supplierId);

        TenantContext.setTenantId(c.dealerTid);
        try {
            String soCode = collab().onPurchaseOrderSubmitted(poId);
            assertThat(soCode).startsWith("SO-");

            assertThat(cnt("SELECT COUNT(1) FROM orders WHERE tenant_id=?1 AND code=?2", c.mfrTid, soCode)).isEqualTo(1);
            String status = one("SELECT status FROM orders WHERE tenant_id=?1 AND code=?2", c.mfrTid, soCode);
            assertThat(status).isEqualTo("DRAFT");
            String custPo = one("SELECT customer_po_code FROM orders WHERE tenant_id=?1 AND code=?2", c.mfrTid, soCode);
            assertThat(custPo).isNotNull();
            assertThat(cnt("SELECT COUNT(1) FROM order_lines ol JOIN orders o ON o.id=ol.order_id " +
                            "WHERE o.tenant_id=?1 AND o.code=?2 AND ol.product_id=?3 AND ol.qty=?4",
                    c.mfrTid, soCode, c.mfrProductId, new BigDecimal("10"))).isEqualTo(1);

            assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links " +
                            "WHERE po_id=?1 AND link_type='PO_TO_SALES_ORDER' AND sales_order_code=?2",
                    poId, soCode)).isEqualTo(1);

            String vendorCode = one("SELECT vendor_order_code FROM purchase_orders WHERE id=?1 AND tenant_id=?2",
                    poId, c.dealerTid);
            assertThat(vendorCode).isEqualTo(soCode);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void pathA_duplicateSubmit_returnsSameOrderWithoutDuplicate() {
        Ctx c = setupPair(true, true);
        Long poId = createPo(c, c.dealerProductId, new BigDecimal("3"), c.supplierId);

        TenantContext.setTenantId(c.dealerTid);
        try {
            String first = collab().onPurchaseOrderSubmitted(poId);
            String second = collab().onPurchaseOrderSubmitted(poId);
            assertThat(second).isEqualTo(first);

            assertThat(cnt("SELECT COUNT(1) FROM orders WHERE tenant_id=?1 AND customer_po_code=" +
                            "(SELECT code FROM purchase_orders WHERE id=?2)",
                    c.mfrTid, poId)).isEqualTo(1);
            assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links WHERE po_id=?1 AND link_type='PO_TO_SALES_ORDER'",
                    poId)).isEqualTo(1);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void pathA_missingMapping_throwsAndNoOrderCreated() {
        Ctx c = setupPair(true, false);
        Long poId = createPo(c, c.dealerProductId, new BigDecimal("2"), c.supplierId);

        TenantContext.setTenantId(c.dealerTid);
        try {
            assertThatThrownBy(() -> collab().onPurchaseOrderSubmitted(poId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("对码");
            assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links WHERE po_id=?1", poId)).isZero();
            assertThat(cnt("SELECT COUNT(1) FROM orders WHERE tenant_id=?1", c.mfrTid)).isZero();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void pathA_noBinding_throwsAndNoOrderCreated() {
        Ctx c = setupPair(false, true);
        Long poId = createPo(c, c.dealerProductId, new BigDecimal("2"), c.supplierId);

        TenantContext.setTenantId(c.dealerTid);
        try {
            assertThatThrownBy(() -> collab().onPurchaseOrderSubmitted(poId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("尚未绑定厂家客户主数据");
            assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links WHERE po_id=?1", poId)).isZero();
            assertThat(cnt("SELECT COUNT(1) FROM orders WHERE tenant_id=?1", c.mfrTid)).isZero();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void pathA_plainSupplier_returnsNullAndNoLink() {
        Ctx c = setupPair(true, true);
        Long poId = createPo(c, c.dealerProductId, new BigDecimal("1"), null);

        TenantContext.setTenantId(c.dealerTid);
        try {
            assertThat(collab().onPurchaseOrderSubmitted(poId)).isNull();
            assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links WHERE po_id=?1", poId)).isZero();
        } finally {
            TenantContext.clear();
        }
    }

    // ==================== 路径B：厂家出库 -> 经销商收货单 ====================

    @Test
    void pathB_firstShip_autoCreatesApprovedPoReceiptAndSelfEntities() {
        Ctx c = setupPair(true, true);
        Long mfrOrderId = createMfrSalesOrder(c);
        Long so1 = createSalesOut(c, mfrOrderId);
        Long line1 = createSalesOutLine(so1, c.mfrProductId, "B1", null, new BigDecimal("5"));

        TenantContext.setTenantId(c.mfrTid);
        try {
            collab().onSalesOutShipped(so1, List.of(shipped(c, line1, "B1", null, new BigDecimal("5"))));
        } finally {
            TenantContext.clear();
        }

        Long autoPoId = one("SELECT id FROM purchase_orders WHERE tenant_id=?1 AND status='APPROVED'", c.dealerTid);
        assertThat(autoPoId).isNotNull();
        String vendorCode = one("SELECT vendor_order_code FROM purchase_orders WHERE id=?1", autoPoId);
        assertThat(vendorCode).startsWith("SO-");
        BigDecimal poQty = one("SELECT qty FROM purchase_order_lines WHERE po_id=?1 AND product_id=?2",
                autoPoId, c.dealerProductId);
        assertThat(poQty).isEqualByComparingTo("5");

        Long receiptId = one("SELECT id FROM receipts WHERE tenant_id=?1 AND ref_doc_type='sales_out' AND status='PENDING'",
                c.dealerTid);
        assertThat(receiptId).isNotNull();
        Long receiptDealer = one("SELECT dealer_id FROM receipts WHERE id=?1", receiptId);
        Long receiptWh = one("SELECT warehouse_id FROM receipts WHERE id=?1", receiptId);
        assertThat(receiptDealer).isNotNull();
        assertThat(receiptWh).isNotNull();
        Long receiptPo = one("SELECT source_po_id FROM receipts WHERE id=?1", receiptId);
        assertThat(receiptPo).isEqualTo(autoPoId);
        assertThat(cnt("SELECT COUNT(1) FROM receipt_lines WHERE receipt_id=?1 AND product_id=?2 AND batch_no='B1' AND expected_qty=?3",
                receiptId, c.dealerProductId, new BigDecimal("5"))).isEqualTo(1);

        Long selfDealerId = one("SELECT id FROM dealers WHERE tenant_id=?1 AND code='DEALER-SELF'", c.dealerTid);
        assertThat(selfDealerId).isNotNull();
        assertThat(cnt("SELECT COUNT(1) FROM warehouses WHERE tenant_id=?1 AND code='COLLAB-DEFAULT-WH' AND dealer_id=?2",
                c.dealerTid, selfDealerId)).isEqualTo(1);
        assertThat(receiptDealer).isEqualTo(selfDealerId);

        assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links " +
                        "WHERE sales_out_id=?1 AND link_type='SALES_OUT_TO_RECEIPT' AND po_id=?2",
                so1, autoPoId)).isEqualTo(1);
    }

    @Test
    void pathB_secondBatch_reusesAutoPoAndAccumulatesQty() {
        Ctx c = setupPair(true, true);
        Long mfrOrderId = createMfrSalesOrder(c);
        Long so1 = createSalesOut(c, mfrOrderId);
        Long line1 = createSalesOutLine(so1, c.mfrProductId, "B1", null, new BigDecimal("5"));
        Long so2 = createSalesOut(c, mfrOrderId);
        Long line2 = createSalesOutLine(so2, c.mfrProductId, "B2", null, new BigDecimal("3"));

        TenantContext.setTenantId(c.mfrTid);
        try {
            collab().onSalesOutShipped(so1, List.of(shipped(c, line1, "B1", null, new BigDecimal("5"))));
            collab().onSalesOutShipped(so2, List.of(shipped(c, line2, "B2", null, new BigDecimal("3"))));
        } finally {
            TenantContext.clear();
        }

        assertThat(cnt("SELECT COUNT(1) FROM purchase_orders WHERE tenant_id=?1", c.dealerTid)).isEqualTo(1);
        Long poId = one("SELECT id FROM purchase_orders WHERE tenant_id=?1", c.dealerTid);
        BigDecimal qty = one("SELECT qty FROM purchase_order_lines WHERE po_id=?1 AND product_id=?2",
                poId, c.dealerProductId);
        assertThat(qty).isEqualByComparingTo("8");
        assertThat(cnt("SELECT COUNT(1) FROM receipts WHERE tenant_id=?1 AND ref_doc_type='sales_out'",
                c.dealerTid)).isEqualTo(2);
        assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links " +
                        "WHERE link_type='SALES_OUT_TO_RECEIPT' AND dealer_tenant_id=?1",
                c.dealerTid)).isEqualTo(2);
        assertThat(cnt("SELECT COUNT(1) FROM dealers WHERE tenant_id=?1 AND code='DEALER-SELF'", c.dealerTid)).isEqualTo(1);
        assertThat(cnt("SELECT COUNT(1) FROM warehouses WHERE tenant_id=?1 AND code='COLLAB-DEFAULT-WH'",
                c.dealerTid)).isEqualTo(1);
    }

    @Test
    void pathB_resendSameBatch_isIdempotent() {
        Ctx c = setupPair(true, true);
        Long mfrOrderId = createMfrSalesOrder(c);
        Long so1 = createSalesOut(c, mfrOrderId);
        Long line1 = createSalesOutLine(so1, c.mfrProductId, "B1", null, new BigDecimal("5"));

        TenantContext.setTenantId(c.mfrTid);
        try {
            collab().onSalesOutShipped(so1, List.of(shipped(c, line1, "B1", null, new BigDecimal("5"))));
            collab().onSalesOutShipped(so1, List.of(shipped(c, line1, "B1", null, new BigDecimal("5"))));
        } finally {
            TenantContext.clear();
        }

        assertThat(cnt("SELECT COUNT(1) FROM receipts WHERE tenant_id=?1 AND ref_doc_type='sales_out' AND ref_doc_id=?2",
                c.dealerTid, so1)).isEqualTo(1);
        assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links " +
                        "WHERE sales_out_id=?1 AND link_type='SALES_OUT_TO_RECEIPT'", so1)).isEqualTo(1);
        Long poId = one("SELECT id FROM purchase_orders WHERE tenant_id=?1", c.dealerTid);
        BigDecimal qty = one("SELECT qty FROM purchase_order_lines WHERE po_id=?1 AND product_id=?2",
                poId, c.dealerProductId);
        assertThat(qty).isEqualByComparingTo("5");
    }

    @Test
    void pathB_pathALinkExists_reusesFormalPoWithoutQtyChange() {
        Ctx c = setupPair(true, true);
        Long poId = createPo(c, c.dealerProductId, new BigDecimal("10"), c.supplierId);

        TenantContext.setTenantId(c.dealerTid);
        String soCode;
        try {
            soCode = collab().onPurchaseOrderSubmitted(poId);
        } finally {
            TenantContext.clear();
        }
        Long mfrOrderId = one("SELECT id FROM orders WHERE tenant_id=?1 AND code=?2", c.mfrTid, soCode);

        Long so1 = createSalesOut(c, mfrOrderId);
        Long line1 = createSalesOutLine(so1, c.mfrProductId, "B1", null, new BigDecimal("4"));

        TenantContext.setTenantId(c.mfrTid);
        try {
            collab().onSalesOutShipped(so1, List.of(shipped(c, line1, "B1", null, new BigDecimal("4"))));
        } finally {
            TenantContext.clear();
        }

        assertThat(cnt("SELECT COUNT(1) FROM purchase_orders WHERE tenant_id=?1", c.dealerTid)).isEqualTo(1);
        BigDecimal qty = one("SELECT qty FROM purchase_order_lines WHERE po_id=?1 AND product_id=?2",
                poId, c.dealerProductId);
        assertThat(qty).isEqualByComparingTo("10");
        Long receiptPo = one("SELECT source_po_id FROM receipts WHERE tenant_id=?1 AND ref_doc_id=?2",
                c.dealerTid, so1);
        assertThat(receiptPo).isEqualTo(poId);
        assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links " +
                        "WHERE sales_out_id=?1 AND link_type='SALES_OUT_TO_RECEIPT' AND po_id=?2",
                so1, poId)).isEqualTo(1);
    }

    @Test
    void pathB_unboundDealer_silentlySkips() {
        Ctx c = setupPair(false, true);
        Long mfrOrderId = createMfrSalesOrder(c);
        Long so1 = createSalesOut(c, mfrOrderId);
        Long line1 = createSalesOutLine(so1, c.mfrProductId, "B1", null, new BigDecimal("1"));

        TenantContext.setTenantId(c.mfrTid);
        try {
            collab().onSalesOutShipped(so1, List.of(shipped(c, line1, "B1", null, new BigDecimal("1"))));
        } finally {
            TenantContext.clear();
        }

        assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links " +
                "WHERE sales_out_id=?1 AND link_type='SALES_OUT_TO_RECEIPT'", so1)).isZero();
        assertThat(cnt("SELECT COUNT(1) FROM receipts WHERE tenant_id=?1", c.dealerTid)).isZero();
        assertThat(cnt("SELECT COUNT(1) FROM purchase_orders WHERE tenant_id=?1", c.dealerTid)).isZero();
    }

    @Test
    void pathB_missingMapping_throwsAndNothingCreated() {
        Ctx c = setupPair(true, false);
        Long mfrOrderId = createMfrSalesOrder(c);
        Long so1 = createSalesOut(c, mfrOrderId);
        Long line1 = createSalesOutLine(so1, c.mfrProductId, "B1", null, new BigDecimal("1"));

        TenantContext.setTenantId(c.mfrTid);
        try {
            assertThatThrownBy(() ->
                    collab().onSalesOutShipped(so1, List.of(shipped(c, line1, "B1", null, new BigDecimal("1")))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("对码");
        } finally {
            TenantContext.clear();
        }

        assertThat(cnt("SELECT COUNT(1) FROM receipts WHERE tenant_id=?1", c.dealerTid)).isZero();
        assertThat(cnt("SELECT COUNT(1) FROM purchase_orders WHERE tenant_id=?1", c.dealerTid)).isZero();
        assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links WHERE sales_out_id=?1", so1)).isZero();
    }

    // ==================== 原生 SQL 辅助 ====================

    private Long ins(String sql, Object... params) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    private long cnt(String sql, Object... params) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    private <T> T one(String sql, Object... params) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        List<?> rs = q.getResultList();
        return rs.isEmpty() ? null : (T) rs.get(0);
    }

    // ==================== 反向 路径C：采退单 -> 厂家红字销退草稿 ====================

    private Long createPurchaseReturn(Ctx c, Long productId, BigDecimal qty, Long supplierId) {
        String code = "PR-CLB" + UUID.randomUUID().toString().substring(0, 8);
        Long poId = ins("INSERT INTO purchase_orders (tenant_id, code, order_type, is_red, supplier_id, supplier_name, status, " +
                        "return_reason, amount_incl_tax, final_amount, remark, created_at, updated_at) " +
                        "VALUES (?1,?2,'NORMAL',true,?3,'平台厂家','DRAFT','质量原因',0,0,'collab-return-test',now(),now()) RETURNING id",
                c.dealerTid, code, supplierId);
        ins("INSERT INTO purchase_order_lines (po_id, seq, product_id, qty, received_qty, unit_price, tax_rate, subtotal, created_at) " +
                        "VALUES (?1,1,?2,?3,0,0,0.13,0,now()) RETURNING id",
                poId, productId, qty);
        return poId;
    }

    private Long createRedSalesOut(Ctx c, Long sourcePoId) {
        String code = "GIR-CLB" + UUID.randomUUID().toString().substring(0, 8);
        return ins("INSERT INTO sales_outs (tenant_id, code, business_type, sales_date, status, is_red, " +
                        "source_po_id, amount_incl_tax, created_at, updated_at, version) " +
                        "VALUES (?1,?2,'sales',CURRENT_DATE,'SHIPPED',true,CAST(?3 AS BIGINT),0,now(),now(),0) RETURNING id",
                c.dealerTid, code, sourcePoId);
    }

    private ShippedLine redShipped(Ctx c, Long outLineId, String batch, String serial, BigDecimal qty) {
        ShippedLine sl = new ShippedLine();
        sl.setProductId(c.dealerProductId);
        sl.setProductCode(c.dealerProductCode);
        sl.setQty(qty);
        sl.setBatchNo(batch);
        sl.setSerialNo(serial);
        sl.setOutLineId(outLineId);
        return sl;
    }

    @Test
    void pathC_purchaseReturnSubmitted_createsRedDraftSalesOrderAndLink() {
        Ctx c = setupPair(true, true);
        Long prId = createPurchaseReturn(c, c.dealerProductId, new BigDecimal("6"), c.supplierId);

        TenantContext.setTenantId(c.dealerTid);
        try {
            String redSoCode = collab().onPurchaseReturnSubmitted(prId);
            assertThat(redSoCode).startsWith("SO-");

            assertThat(cnt("SELECT COUNT(1) FROM orders WHERE tenant_id=?1 AND code=?2 AND is_red=true",
                    c.mfrTid, redSoCode)).isEqualTo(1);
            assertThat(this.<String>one("SELECT status FROM orders WHERE tenant_id=?1 AND code=?2", c.mfrTid, redSoCode))
                    .isEqualTo("DRAFT");
            assertThat(this.<String>one("SELECT customer_po_code FROM orders WHERE tenant_id=?1 AND code=?2", c.mfrTid, redSoCode))
                    .isNotNull();
            // 明细产品为厂家产品（对码转换后）
            assertThat(cnt("SELECT COUNT(1) FROM order_lines ol JOIN orders o ON o.id=ol.order_id " +
                            "WHERE o.tenant_id=?1 AND o.code=?2 AND ol.product_id=?3 AND ol.qty=?4",
                    c.mfrTid, redSoCode, c.mfrProductId, new BigDecimal("6"))).isEqualTo(1);

            assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links " +
                            "WHERE po_id=?1 AND link_type='PR_TO_RED_SALES_ORDER' AND sales_order_code=?2",
                    prId, redSoCode)).isEqualTo(1);
            // 厂家销退单号回写到采退单
            assertThat(this.<String>one("SELECT vendor_order_code FROM purchase_orders WHERE id=?1 AND tenant_id=?2",
                    prId, c.dealerTid)).isEqualTo(redSoCode);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void pathC_duplicateSubmit_returnsSameOrderWithoutDuplicate() {
        Ctx c = setupPair(true, true);
        Long prId = createPurchaseReturn(c, c.dealerProductId, new BigDecimal("2"), c.supplierId);

        TenantContext.setTenantId(c.dealerTid);
        try {
            String first = collab().onPurchaseReturnSubmitted(prId);
            String second = collab().onPurchaseReturnSubmitted(prId);
            assertThat(second).isEqualTo(first);
            assertThat(cnt("SELECT COUNT(1) FROM orders WHERE tenant_id=?1 AND is_red=true", c.mfrTid)).isEqualTo(1);
            assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links WHERE po_id=?1 AND link_type='PR_TO_RED_SALES_ORDER'",
                    prId)).isEqualTo(1);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void pathC_missingMapping_throwsAndNothingCreated() {
        Ctx c = setupPair(true, false);
        Long prId = createPurchaseReturn(c, c.dealerProductId, new BigDecimal("2"), c.supplierId);

        TenantContext.setTenantId(c.dealerTid);
        try {
            assertThatThrownBy(() -> collab().onPurchaseReturnSubmitted(prId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("对码");
            assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links WHERE po_id=?1", prId)).isZero();
            assertThat(cnt("SELECT COUNT(1) FROM orders WHERE tenant_id=?1 AND is_red=true", c.mfrTid)).isZero();
        } finally {
            TenantContext.clear();
        }
    }

    // ==================== 反向 路径D：红字出库发货 -> 厂家红字销退入库（待收货） ====================

    @Test
    void pathD_redOutShipped_createsMfrPendingRedReceipt() {
        Ctx c = setupPair(true, true);
        // 路径C 先生成厂家红字销退草稿
        Long prId = createPurchaseReturn(c, c.dealerProductId, new BigDecimal("5"), c.supplierId);
        TenantContext.setTenantId(c.dealerTid);
        String redSoCode;
        try {
            redSoCode = collab().onPurchaseReturnSubmitted(prId);
        } finally {
            TenantContext.clear();
        }
        Long redSoId = one("SELECT id FROM orders WHERE tenant_id=?1 AND code=?2", c.mfrTid, redSoCode);

        // 经销商红字采退出库发货
        Long redOutId = createRedSalesOut(c, prId);
        Long outLineId = ins("INSERT INTO sales_out_lines (sales_out_id, product_id, batch_no, serial_no, qty, created_at) " +
                        "VALUES (?1,?2,'RB1',NULL,?3,now()) RETURNING id",
                redOutId, c.dealerProductId, new BigDecimal("5"));

        TenantContext.setTenantId(c.dealerTid);
        try {
            collab().onRedSalesOutShipped(redOutId, List.of(redShipped(c, outLineId, "RB1", null, new BigDecimal("5"))));
        } finally {
            TenantContext.clear();
        }

        // 厂家租户出现红字销退入库单，待收货
        Long receiptId = one("SELECT id FROM receipts WHERE tenant_id=?1 AND is_red=true AND receipt_type='SALES_RETURN' AND status='PENDING'",
                c.mfrTid);
        assertThat(receiptId).isNotNull();
        assertThat(this.<Long>one("SELECT ref_doc_id FROM receipts WHERE id=?1", receiptId)).isEqualTo(redSoId);
        assertThat(this.<Long>one("SELECT dealer_id FROM receipts WHERE id=?1", receiptId)).isEqualTo(c.mfrDealerId);
        assertThat(this.<Long>one("SELECT warehouse_id FROM receipts WHERE id=?1", receiptId)).isNotNull();
        // 入库行产品已转为厂家产品
        assertThat(cnt("SELECT COUNT(1) FROM receipt_lines WHERE receipt_id=?1 AND product_id=?2 AND batch_no='RB1' AND expected_qty=?3",
                receiptId, c.mfrProductId, new BigDecimal("5"))).isEqualTo(1);

        assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links " +
                        "WHERE sales_out_id=?1 AND link_type='RED_OUT_TO_RED_RECEIPT' AND receipt_id=?2",
                redOutId, receiptId)).isEqualTo(1);
    }

    @Test
    void pathD_resendSameBatch_isIdempotent() {
        Ctx c = setupPair(true, true);
        Long prId = createPurchaseReturn(c, c.dealerProductId, new BigDecimal("3"), c.supplierId);
        TenantContext.setTenantId(c.dealerTid);
        try {
            collab().onPurchaseReturnSubmitted(prId);
        } finally {
            TenantContext.clear();
        }
        Long redOutId = createRedSalesOut(c, prId);
        Long outLineId = ins("INSERT INTO sales_out_lines (sales_out_id, product_id, batch_no, qty, created_at) " +
                        "VALUES (?1,?2,'RB2',?3,now()) RETURNING id",
                redOutId, c.dealerProductId, new BigDecimal("3"));

        TenantContext.setTenantId(c.dealerTid);
        try {
            collab().onRedSalesOutShipped(redOutId, List.of(redShipped(c, outLineId, "RB2", null, new BigDecimal("3"))));
            collab().onRedSalesOutShipped(redOutId, List.of(redShipped(c, outLineId, "RB2", null, new BigDecimal("3"))));
        } finally {
            TenantContext.clear();
        }

        assertThat(cnt("SELECT COUNT(1) FROM receipts WHERE tenant_id=?1 AND is_red=true", c.mfrTid)).isEqualTo(1);
        assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links WHERE sales_out_id=?1 AND link_type='RED_OUT_TO_RED_RECEIPT'",
                redOutId)).isEqualTo(1);
    }

    @Test
    void pathD_noPathCLink_silentlySkips() {
        Ctx c = setupPair(true, true);
        // 直接造一张红字出库（未经过路径C），不触发协同
        Long redOutId = createRedSalesOut(c, 999999L);
        Long outLineId = ins("INSERT INTO sales_out_lines (sales_out_id, product_id, batch_no, qty, created_at) " +
                        "VALUES (?1,?2,'RB3',?3,now()) RETURNING id",
                redOutId, c.dealerProductId, new BigDecimal("1"));

        TenantContext.setTenantId(c.dealerTid);
        try {
            collab().onRedSalesOutShipped(redOutId, List.of(redShipped(c, outLineId, "RB3", null, new BigDecimal("1"))));
        } finally {
            TenantContext.clear();
        }

        assertThat(cnt("SELECT COUNT(1) FROM receipts WHERE tenant_id=?1 AND is_red=true", c.mfrTid)).isZero();
        assertThat(cnt("SELECT COUNT(1) FROM cross_tenant_doc_links WHERE sales_out_id=?1", redOutId)).isZero();
    }
}
