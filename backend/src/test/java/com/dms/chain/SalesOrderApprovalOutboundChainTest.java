package com.dms.chain;

import com.dms.BaseIntegrationTest;
import com.dms.inventory.entity.Inventory;
import com.dms.inventory.repository.InventoryRepository;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.entity.Product;
import com.dms.masterdata.repository.DealerRepository;
import com.dms.rbac.entity.Role;
import com.dms.tenant.entity.Tenant;
import com.dms.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end chain test for the highest-risk flow:
 *   create sales order (DRAFT) -> submit -> auto-approve (APPROVED) ->
 *   sales out (COMPLETED) -> inventory deducted.
 *
 * Uses the real Spring stack against embedded PostgreSQL with the full Flyway
 * schema. Each step re-reads state from the database / API instead of trusting
 * HTTP 200, per .memory/requirement-closure.md.
 */
class SalesOrderApprovalOutboundChainTest extends BaseIntegrationTest {

    @Autowired EntityManager em;
    @Autowired DealerRepository dealerRepository;
    @Autowired InventoryRepository inventoryRepository;

    private static final String TENANT_CODE = "CHAIN-" + UUID.randomUUID().toString().substring(0, 6);

    @Test
    void orderGoesDraftToApprovedToShipped_andInventoryIsDeducted() throws Exception {
        // ---- Arrange master data ----
        Tenant tenant = createTestTenant(TENANT_CODE);
        User user = createTestUser(tenant.getId(), "chainuser", "Admin@1234");
        Product product = createTestProduct(tenant.getId(), "CHAIN-P1", "链路测试产品");
        Dealer dealer = dealerRepository.saveAndFlush(Dealer.builder()
                .tenantId(tenant.getId()).code("CHAIN-D1").name("链路经销商").level("A").status("active")
                .updatedAt(java.time.OffsetDateTime.now()).build());
        Long dealerId = dealer.getId();
        Long productId = product.getId();

        // Warehouse + qualified stock of 100 units
        Object whId = em.createNativeQuery(
                "INSERT INTO warehouses (tenant_id, dealer_id, code, name, type, status, created_at, updated_at) " +
                "VALUES (?1, ?2, 'WH-CHAIN', '链路仓库', 'main', 'active', now(), now()) RETURNING id")
                .setParameter(1, tenant.getId()).setParameter(2, dealerId).getSingleResult();
        Long warehouseId = ((Number) whId).longValue();
        em.createNativeQuery(
                "INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, qty, stock_status, in_source, updated_at, version) " +
                "VALUES (?1, ?2, ?3, ?4, 100, 'QUALIFIED', 'INIT', now(), 0)")
                .setParameter(1, tenant.getId()).setParameter(2, dealerId).setParameter(3, warehouseId)
                .setParameter(4, productId).executeUpdate();

        // A valid sales price for this dealer (STANDALONE / SALE / active) so submit/preview does not reject.
        em.createNativeQuery(
                "INSERT INTO product_prices (tenant_id, product_id, partner_type, partner_id, price_scope, price_context, " +
                "sales_price_excl_tax, sales_price, tax_rate, status, valid_from, valid_to, created_at, updated_at) " +
                "VALUES (?1, ?2, 'DEALER', ?3, 'SALE', 'STANDALONE', 88.4956, 100.0000, 0.13, 'active', ?4, NULL, now(), now())")
                .setParameter(1, tenant.getId()).setParameter(2, productId).setParameter(3, dealerId)
                .setParameter(4, java.sql.Date.valueOf(LocalDate.now().minusDays(1))).executeUpdate();

        // A sales-to-dealer authorization covering the product, valid now, so outbound auth check passes.
        em.createNativeQuery(
                "INSERT INTO authorizations (tenant_id, dealer_id, auth_type, product_id, valid_from, valid_to, status, created_at, updated_at) " +
                "VALUES (?1, ?2, 'SALES_TO_HOSPITAL', ?3, ?4, ?5, 'active', now(), now())")
                .setParameter(1, tenant.getId()).setParameter(2, dealerId).setParameter(3, productId)
                .setParameter(4, java.sql.Date.valueOf(LocalDate.now().minusDays(1)))
                .setParameter(5, java.sql.Date.valueOf(LocalDate.now().plusDays(30))).executeUpdate();

                Role role = grantPermissions(user, "sales_order:create", "sales_order:submit", "sales_order:view",
                "sales_out:create", "inventory:view", "approval:view");
        String token = loginAndGetToken(TENANT_CODE, "chainuser", "Admin@1234");
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();

        // ---- Step 1: create order (DRAFT), 5 units @ 100 = 500 ----
        Map<String, Object> line = new HashMap<>();
        line.put("productId", productId);
        line.put("qty", 5);
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("dealerId", dealerId);
        createBody.put("orderType", "NORMAL");
        createBody.put("lines", List.of(line));

        MvcResult created = mockMvc.perform(post("/api/sales-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode createdJson = objectMapper.readTree(created.getResponse().getContentAsString());
        long orderId = createdJson.path("data").path("id").asLong();
        assertThat(orderId).isPositive();

        String statusAfterCreate = (String) em.createNativeQuery("SELECT status FROM orders WHERE id=?1")
                .setParameter(1, orderId).getSingleResult();
        assertThat(statusAfterCreate).isEqualTo("DRAFT");

        // ---- Step 2: submit -> no approval template configured -> AUTO_APPROVED -> order APPROVED ----
        MvcResult submitted = mockMvc.perform(post("/api/sales-orders/" + orderId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode submitJson = objectMapper.readTree(submitted.getResponse().getContentAsString());
        assertThat(submitJson.path("data").path("newStatus").asText()).isEqualTo("APPROVED");
        assertThat(submitJson.path("data").path("autoApproved").asBoolean()).isTrue();
        assertThat(submitJson.path("data").path("approvalInstanceId").canConvertToLong()).isTrue();

        String statusAfterSubmit = (String) em.createNativeQuery("SELECT status FROM orders WHERE id=?1")
                .setParameter(1, orderId).getSingleResult();
        assertThat(statusAfterSubmit).isEqualTo("APPROVED");

        // Approval instance must exist and be APPROVED/AUTO_APPROVED with non-null assignee trail (AGENTS.md 5.2).
        Object[] instRow = (Object[]) em.createNativeQuery(
                "SELECT status, current_node_name FROM approval_instances WHERE id=?1")
                .setParameter(1, submitJson.path("data").path("approvalInstanceId").asLong()).getSingleResult();
        assertThat(String.valueOf(instRow[0])).isIn("APPROVED", "AUTO_APPROVED");

        // Amounts persisted: 5 * 100 = 500 final
        Object[] amounts = (Object[]) em.createNativeQuery(
                "SELECT amount_incl_tax, final_amount FROM orders WHERE id=?1").setParameter(1, orderId).getSingleResult();
        assertThat(((Number) amounts[1]).doubleValue()).isEqualTo(500.0);

        // ---- Step 3: sales out 5 units -> COMPLETED, deducts inventory ----
        Map<String, Object> salesOut = new HashMap<>();
        salesOut.put("dealerId", dealerId);
        salesOut.put("warehouseId", warehouseId);
        salesOut.put("orderId", orderId);
        salesOut.put("salesDate", LocalDate.now().toString());
        Map<String, Object> outLine = new HashMap<>();
        outLine.put("productId", productId);
        outLine.put("warehouseId", warehouseId);
        outLine.put("qty", 5);
        outLine.put("unitPrice", new BigDecimal("100"));
        Map<String, Object> outBody = new HashMap<>();
        outBody.put("salesOut", salesOut);
        outBody.put("lines", List.of(outLine));

        MvcResult shipped = mockMvc.perform(post("/api/sales-outs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode shippedJson = objectMapper.readTree(shipped.getResponse().getContentAsString());
        long salesOutId = shippedJson.path("data").path("id").asLong();
        assertThat(shippedJson.path("data").path("status").asText()).isEqualTo("COMPLETED");

        // ---- Step 4: inventory re-read: 100 - 5 = 95, with a SALES_OUT transaction ----
        Inventory inv = inventoryRepository
                .lockKeyed(tenant.getId(), warehouseId, productId, null, null).orElseThrow();
        assertThat(inv.getQty()).isEqualByComparingTo("95.0000");

        Long txnCount = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM inventory_transactions WHERE tenant_id=?1 AND product_id=?2 AND txn_type='SALES_OUT' AND ref_doc_id=?3")
                .setParameter(1, tenant.getId()).setParameter(2, productId).setParameter(3, salesOutId).getSingleResult();
        assertThat(txnCount).isEqualTo(1L);
    }

    @Test
    void outboundFailsAndInventoryUntouched_when_stockInsufficient() throws Exception {
        Tenant tenant = createTestTenant(TENANT_CODE + "2");
        User user = createTestUser(tenant.getId(), "chainuser2", "Admin@1234");
        Product product = createTestProduct(tenant.getId(), "CHAIN-P2", "缺货产品");
        Dealer dealer = dealerRepository.saveAndFlush(Dealer.builder()
                .tenantId(tenant.getId()).code("CHAIN-D2").name("缺货经销商").level("A").status("active")
                .updatedAt(java.time.OffsetDateTime.now()).build());

        Object whId = em.createNativeQuery(
                "INSERT INTO warehouses (tenant_id, dealer_id, code, name, type, status, created_at, updated_at) " +
                "VALUES (?1, ?2, 'WH-CHAIN2', '仓库2', 'main', 'active', now(), now()) RETURNING id")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).getSingleResult();
        Long warehouseId = ((Number) whId).longValue();
        em.createNativeQuery(
                "INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, qty, stock_status, in_source, updated_at, version) " +
                "VALUES (?1, ?2, ?3, ?4, 2, 'QUALIFIED', 'INIT', now(), 0)")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).setParameter(3, warehouseId)
                .setParameter(4, product.getId()).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO authorizations (tenant_id, dealer_id, auth_type, product_id, valid_from, valid_to, status, created_at, updated_at) " +
                "VALUES (?1, ?2, 'SALES_TO_HOSPITAL', ?3, ?4, ?5, 'active', now(), now())")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).setParameter(3, product.getId())
                .setParameter(4, java.sql.Date.valueOf(LocalDate.now().minusDays(1)))
                .setParameter(5, java.sql.Date.valueOf(LocalDate.now().plusDays(30))).executeUpdate();
        grantPermissions(user, "sales_out:create");
        String token = loginAndGetToken(TENANT_CODE + "2", "chainuser2", "Admin@1234");

        Map<String, Object> outLine = new HashMap<>();
        outLine.put("productId", product.getId());
        outLine.put("warehouseId", warehouseId);
        outLine.put("qty", 5);
        outLine.put("unitPrice", new BigDecimal("100"));
        Map<String, Object> salesOut = new HashMap<>();
        salesOut.put("dealerId", dealer.getId());
        salesOut.put("warehouseId", warehouseId);
        salesOut.put("salesDate", LocalDate.now().toString());
        Map<String, Object> body = new HashMap<>();
        body.put("salesOut", salesOut);
        body.put("lines", List.of(outLine));

        mockMvc.perform(post("/api/sales-outs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40006))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("库存不足")));

        // Inventory untouched on failure
        Inventory inv = inventoryRepository
                .lockKeyed(tenant.getId(), warehouseId, product.getId(), null, null).orElseThrow();
        assertThat(inv.getQty()).isEqualByComparingTo("2.0000");
    }
    @Test
    void orderWithGiftLine_completesAfterShipment_notPartial() throws Exception {
        // Regression: refreshOrderStatus used to count promotion gift order lines (is_gift=true,
        // never shipped because gifts are non-physical) as unmet, leaving the order in
        // PARTIAL_OUTBOUND even after every physical line shipped. Gifts and BOM parents must
        // never block COMPLETED.
        // v4.1.6: physical gifts DO ship; this test now also asserts the gift IS in the sales-out
        // and the gift shipment is what unblocks COMPLETED.
        Tenant tenant = createTestTenant(TENANT_CODE + "3");
        User user = createTestUser(tenant.getId(), "chainuser3", "Admin@1234");
        Product product = createTestProduct(tenant.getId(), "CHAIN-PG", "含赠品订单产品");
        Product gift = createTestProduct(tenant.getId(), "CHAIN-GIFT", "促销赠品");
        Dealer dealer = dealerRepository.saveAndFlush(Dealer.builder()
                .tenantId(tenant.getId()).code("CHAIN-D3").name("含赠品经销商").level("A").status("active")
                .updatedAt(java.time.OffsetDateTime.now()).build());

        Object whId = em.createNativeQuery(
                "INSERT INTO warehouses (tenant_id, dealer_id, code, name, type, status, created_at, updated_at) " +
                "VALUES (?1, ?2, 'WH-CHAIN3', '仓库3', 'main', 'active', now(), now()) RETURNING id")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).getSingleResult();
        Long warehouseId = ((Number) whId).longValue();
        em.createNativeQuery(
                "INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, qty, stock_status, in_source, updated_at, version) " +
                "VALUES (?1, ?2, ?3, ?4, 100, 'QUALIFIED', 'INIT', now(), 0)")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).setParameter(3, warehouseId)
                .setParameter(4, product.getId()).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, qty, stock_status, in_source, updated_at, version) " +
                "VALUES (?1, ?2, ?3, ?4, 100, 'QUALIFIED', 'INIT', now(), 0)")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).setParameter(3, warehouseId)
                .setParameter(4, gift.getId()).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO product_prices (tenant_id, product_id, partner_type, partner_id, price_scope, price_context, " +
                "sales_price_excl_tax, sales_price, tax_rate, status, valid_from, valid_to, created_at, updated_at) " +
                "VALUES (?1, ?2, 'DEALER', ?3, 'SALE', 'STANDALONE', 88.4956, 100.0000, 0.13, 'active', ?4, NULL, now(), now())")
                .setParameter(1, tenant.getId()).setParameter(2, product.getId()).setParameter(3, dealer.getId())
                .setParameter(4, java.sql.Date.valueOf(LocalDate.now().minusDays(1))).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO product_prices (tenant_id, product_id, partner_type, partner_id, price_scope, price_context, " +
                "sales_price_excl_tax, sales_price, tax_rate, status, valid_from, valid_to, created_at, updated_at) " +
                "VALUES (?1, ?2, 'DEALER', ?3, 'SALE', 'STANDALONE', 88.4956, 100.0000, 0.13, 'active', ?4, NULL, now(), now())")
                .setParameter(1, tenant.getId()).setParameter(2, gift.getId()).setParameter(3, dealer.getId())
                .setParameter(4, java.sql.Date.valueOf(LocalDate.now().minusDays(1))).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO authorizations (tenant_id, dealer_id, auth_type, product_id, valid_from, valid_to, status, created_at, updated_at) " +
                "VALUES (?1, ?2, 'SALES_TO_HOSPITAL', ?3, ?4, ?5, 'active', now(), now())")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).setParameter(3, product.getId())
                .setParameter(4, java.sql.Date.valueOf(LocalDate.now().minusDays(1)))
                .setParameter(5, java.sql.Date.valueOf(LocalDate.now().plusDays(30))).executeUpdate();
        em.createNativeQuery(
                "INSERT INTO authorizations (tenant_id, dealer_id, auth_type, product_id, valid_from, valid_to, status, created_at, updated_at) " +
                "VALUES (?1, ?2, 'SALES_TO_HOSPITAL', ?3, ?4, ?5, 'active', now(), now())")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).setParameter(3, gift.getId())
                .setParameter(4, java.sql.Date.valueOf(LocalDate.now().minusDays(1)))
                .setParameter(5, java.sql.Date.valueOf(LocalDate.now().plusDays(30))).executeUpdate();
        grantPermissions(user, "sales_order:create", "sales_order:submit", "sales_order:view", "sales_out:create");
        String token = loginAndGetToken(TENANT_CODE + "3", "chainuser3", "Admin@1234");

        Map<String, Object> line = new HashMap<>();
        line.put("productId", product.getId());
        line.put("qty", 2);
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("dealerId", dealer.getId());
        createBody.put("orderType", "NORMAL");
        createBody.put("warehouseId", warehouseId);
        createBody.put("lines", List.of(line));
        createBody.put("applyPromotions", false);

        MvcResult created = mockMvc.perform(post("/api/sales-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        long orderId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/sales-orders/" + orderId + "/submit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        // Insert a physical gift order line AFTER submit (mimics promotion engine appending the gift).
        // v4.1.6: gifts are physical and must ship. We DO NOT skip gift lines anymore.
        em.createNativeQuery(
                "INSERT INTO order_lines (order_id, seq, product_id, qty, unit_price, tax_rate, sub_total, final_amount, " +
                "line_level, is_gift, component_qty, created_at) " +
                "VALUES (?1, 99, ?2, 5, 0, 0, 0, 0, 'NORMAL', true, 1, now())")
                .setParameter(1, orderId).setParameter(2, gift.getId()).executeUpdate();

        MvcResult shipped = mockMvc.perform(post("/api/sales-orders/" + orderId + "/simulate-ship").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        long outId = objectMapper.readTree(shipped.getResponse().getContentAsString()).path("data").path("id").asLong();

        // v4.1.6: the gift line is now expected in the sales-out.
        long giftOutLines = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM sales_out_lines WHERE sales_out_id=?1 AND product_id=?2")
                .setParameter(1, outId).setParameter(2, gift.getId()).getSingleResult()).longValue();
        assertThat(giftOutLines).as("physical gift must be in sales-out").isEqualTo(1L);

        String orderStatus = (String) em.createNativeQuery("SELECT status FROM orders WHERE id=?1")
                .setParameter(1, orderId).getSingleResult();
        assertThat(orderStatus).isEqualTo("COMPLETED");
    }

    @Test
    void physicalGift_isShippedBySimulateShip() throws Exception {
        // v4.1.6 regression: physical gift (e.g. bone screw) must appear in the sales-out and the
        // CONFIRMED batch. The earlier orderWithGiftLine_completesAfterShipment_notPartial test
        // already exercises the COMPLETED path; here we additionally assert the gift IS in the out.
        Tenant tenant = createTestTenant(TENANT_CODE + "S");
        User user = createTestUser(tenant.getId(), "sgiftuser", "Admin@1234");
        Product paid = createTestProduct(tenant.getId(), "S-PAID", "付费");
        Product gift = createTestProduct(tenant.getId(), "S-GIFT", "实物赠品");
        Dealer dealer = dealerRepository.saveAndFlush(Dealer.builder()
                .tenantId(tenant.getId()).code("S-DLR").name("S经销商").level("A").status("active")
                .updatedAt(java.time.OffsetDateTime.now()).build());

        Object whId = em.createNativeQuery(
                "INSERT INTO warehouses (tenant_id, dealer_id, code, name, type, status, created_at, updated_at) " +
                "VALUES (?1, ?2, 'WH-SG', 'S仓', 'main', 'active', now(), now()) RETURNING id")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).getSingleResult();
        Long warehouseId = ((Number) whId).longValue();
        for (Product p : new Product[]{paid, gift}) {
            em.createNativeQuery(
                    "INSERT INTO inventory (tenant_id, dealer_id, warehouse_id, product_id, qty, stock_status, in_source, updated_at, version) " +
                    "VALUES (?1, ?2, ?3, ?4, 50, 'QUALIFIED', 'INIT', now(), 0)")
                    .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).setParameter(3, warehouseId)
                    .setParameter(4, p.getId()).executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO product_prices (tenant_id, product_id, partner_type, partner_id, price_scope, price_context, " +
                    "sales_price_excl_tax, sales_price, tax_rate, status, valid_from, valid_to, created_at, updated_at) " +
                    "VALUES (?1, ?2, 'DEALER', ?3, 'SALE', 'STANDALONE', 88.4956, 100.0000, 0.13, 'active', ?4, NULL, now(), now())")
                    .setParameter(1, tenant.getId()).setParameter(2, p.getId()).setParameter(3, dealer.getId())
                    .setParameter(4, java.sql.Date.valueOf(LocalDate.now().minusDays(1))).executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO authorizations (tenant_id, dealer_id, auth_type, product_id, valid_from, valid_to, status, created_at, updated_at) " +
                    "VALUES (?1, ?2, 'SALES_TO_HOSPITAL', ?3, ?4, ?5, 'active', now(), now())")
                    .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).setParameter(3, p.getId())
                    .setParameter(4, java.sql.Date.valueOf(LocalDate.now().minusDays(1)))
                    .setParameter(5, java.sql.Date.valueOf(LocalDate.now().plusDays(30))).executeUpdate();
        }
        grantPermissions(user, "sales_order:create", "sales_order:submit", "sales_order:view", "sales_out:create");
        String token = loginAndGetToken(TENANT_CODE + "S", "sgiftuser", "Admin@1234");

        Map<String, Object> line = new HashMap<>();
        line.put("productId", paid.getId()); line.put("qty", 2);
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("dealerId", dealer.getId()); createBody.put("orderType", "NORMAL");
        createBody.put("warehouseId", warehouseId); createBody.put("applyPromotions", false);
        createBody.put("lines", List.of(line));
        MvcResult created = mockMvc.perform(post("/api/sales-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        long orderId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/sales-orders/" + orderId + "/submit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        // Insert a physical gift order line AFTER submit (mimics promotion engine appending the gift)
        em.createNativeQuery(
                "INSERT INTO order_lines (order_id, seq, product_id, qty, unit_price, tax_rate, sub_total, final_amount, " +
                "line_level, is_gift, component_qty, created_at) " +
                "VALUES (?1, 99, ?2, 4, 0, 0, 0, 0, 'NORMAL', true, 1, now())")
                .setParameter(1, orderId).setParameter(2, gift.getId()).executeUpdate();

        MvcResult shipped = mockMvc.perform(post("/api/sales-orders/" + orderId + "/simulate-ship").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        long outId = objectMapper.readTree(shipped.getResponse().getContentAsString()).path("data").path("id").asLong();

        long giftOutLines = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM sales_out_lines WHERE sales_out_id=?1 AND product_id=?2")
                .setParameter(1, outId).setParameter(2, gift.getId()).getSingleResult()).longValue();
        assertThat(giftOutLines).as("physical gift must be in sales-out (v4.1.6)").isEqualTo(1L);

        Long giftBatchLines = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM sales_out_batch_lines bl JOIN sales_out_batches b ON b.id=bl.batch_id " +
                "WHERE b.sales_out_id=?1 AND bl.product_id=?2")
                .setParameter(1, outId).setParameter(2, gift.getId()).getSingleResult()).longValue();
        assertThat(giftBatchLines).as("physical gift must be in CONFIRMED batch_lines").isEqualTo(1L);
    }
}
