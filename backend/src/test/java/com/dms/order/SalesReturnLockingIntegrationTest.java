package com.dms.order;

import com.dms.BaseIntegrationTest;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.entity.Product;
import com.dms.masterdata.repository.DealerRepository;
import com.dms.tenant.entity.Tenant;
import com.dms.user.entity.User;
import com.dms.order.service.SalesReturnService;
import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import com.dms.common.util.TenantContext;
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
 * Regression for RMA returnable-quantity locking:
 *  - submitting an RMA locks quantity on the source sales-out line at submit time;
 *  - a second RMA exceeding the remaining returnable quantity must be rejected;
 *  - rejecting the first RMA releases the lock exactly once (no double-release).
 */
class SalesReturnLockingIntegrationTest extends BaseIntegrationTest {

    @Autowired EntityManager em;
    @Autowired DealerRepository dealerRepository;

    private static final String TENANT_CODE = "RMALOCK-" + UUID.randomUUID().toString().substring(0, 6);

    private long setupShippedLine(Tenant tenant, User user, Dealer dealer, Product product) {
        Long warehouseId;
        Object wh = em.createNativeQuery(
                "INSERT INTO warehouses (tenant_id, dealer_id, code, name, type, status, created_at, updated_at) " +
                "VALUES (?1, ?2, 'WH-RL', '锁仓', 'main', 'active', now(), now()) RETURNING id")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).getSingleResult();
        warehouseId = ((Number) wh).longValue();

        Long salesOutId;
        Object so = em.createNativeQuery(
                "INSERT INTO sales_outs (tenant_id, code, dealer_id, warehouse_id, is_red, status, sales_date, amount_incl_tax, shipped_at, completed_at, created_at, updated_at) " +
                "VALUES (?1, 'GI-RL-1', ?2, ?3, false, 'COMPLETED', CURRENT_DATE, 1000, now(), now(), now(), now()) RETURNING id")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).setParameter(3, warehouseId).getSingleResult();
        salesOutId = ((Number) so).longValue();

        Object sol = em.createNativeQuery(
                "INSERT INTO sales_out_lines (sales_out_id, seq, product_id, warehouse_id, expected_qty, shipped_qty, qty, unit_price, tax_rate, subtotal, final_amount, created_at) " +
                "VALUES (?1, 1, ?2, ?3, 10, 10, 10, 100, 0.13, 1000, 1000, now()) RETURNING id")
                .setParameter(1, salesOutId).setParameter(2, product.getId()).setParameter(3, warehouseId).getSingleResult();
        return ((Number) sol).longValue();
    }

    private Map<String, Object> rmaBody(long outLineId, long productId, long salesOutId, long dealerId, long warehouseId, int qty) {
        Map<String, Object> line = new HashMap<>();
        line.put("sourceOutLineId", outLineId);
        line.put("productId", productId);
        line.put("productCode", "RL-P1");
        line.put("productName", "锁测试产品");
        line.put("qty", qty);
        line.put("unitPrice", new BigDecimal("100"));
        line.put("taxRate", new BigDecimal("0.13"));
        Map<String, Object> body = new HashMap<>();
        body.put("refSalesOutId", salesOutId);
        body.put("dealerId", dealerId);
        body.put("warehouseId", warehouseId);
        body.put("expectedDate", LocalDate.now().toString());
        body.put("reasonCode", "NORMAL");
        body.put("reason", "测试退货");
        body.put("lines", List.of(line));
        return body;
    }

    private long createRma(String token, long outLineId, long productId, int qty) throws Exception {
        Map<String, Object> body = rmaBody(outLineId, productId, resolveSalesOutId(outLineId), resolveDealerId(outLineId), resolveWarehouseId(outLineId), qty);
        MvcResult res = mockMvc.perform(post("/api/sales-returns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode j = objectMapper.readTree(res.getResponse().getContentAsString());
        return j.path("data").path("id").asLong();
    }

    private long resolveSalesOutId(long outLineId) {
        return ((Number) em.createNativeQuery("SELECT sales_out_id FROM sales_out_lines WHERE id=?1").setParameter(1, outLineId).getSingleResult()).longValue();
    }
    private long resolveDealerId(long outLineId) {
        return ((Number) em.createNativeQuery("SELECT so.dealer_id FROM sales_out_lines sol JOIN sales_outs so ON so.id=sol.sales_out_id WHERE sol.id=?1").setParameter(1, outLineId).getSingleResult()).longValue();
    }
    private long resolveWarehouseId(long outLineId) {
        return ((Number) em.createNativeQuery("SELECT so.warehouse_id FROM sales_out_lines sol JOIN sales_outs so ON so.id=sol.sales_out_id WHERE sol.id=?1").setParameter(1, outLineId).getSingleResult()).longValue();
    }

    private BigDecimal lockedOf(long outLineId) {
        return new BigDecimal(String.valueOf(em.createNativeQuery("SELECT COALESCE(return_locked_qty,0) FROM sales_out_lines WHERE id=?1").setParameter(1, outLineId).getSingleResult()));
    }

    @Test
    void shippedOutLines_includesGiftLine() throws Exception {
        // v4.1.7: physical gifts in the sales-out must be available for return (RMA picker).
        // The picker must not silently drop zero-priced gift lines.
        Tenant tenant = createTestTenant(TENANT_CODE + "G2");
        User user = createTestUser(tenant.getId(), "giftshipuser", "Admin@1234");
        Product paid = createTestProduct(tenant.getId(), "PAY2", "付费品");
        Product gift = createTestProduct(tenant.getId(), "GFT2", "赠品");
        Dealer dealer = dealerRepository.saveAndFlush(Dealer.builder()
                .tenantId(tenant.getId()).code("DLR-G2").name("G2经销商").level("A").status("active")
                .updatedAt(java.time.OffsetDateTime.now()).build());

        Object whId = em.createNativeQuery(
                "INSERT INTO warehouses (tenant_id, dealer_id, code, name, type, status, created_at, updated_at) " +
                "VALUES (?1, ?2, 'WH-G2', 'G2仓', 'main', 'active', now(), now()) RETURNING id")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).getSingleResult();
        Long warehouseId = ((Number) whId).longValue();
        // A completed sales-out with 2 lines: one paid, one gift (zero-priced physical)
        Object soId = em.createNativeQuery(
                "INSERT INTO sales_outs (tenant_id, code, dealer_id, warehouse_id, is_red, status, sales_date, amount_incl_tax, shipped_at, completed_at, created_at, updated_at) " +
                "VALUES (?1, 'GI-G2-1', ?2, ?3, false, 'COMPLETED', CURRENT_DATE, 100, now(), now(), now(), now()) RETURNING id")
                .setParameter(1, tenant.getId()).setParameter(2, dealer.getId()).setParameter(3, warehouseId).getSingleResult();
        long salesOutId = ((Number) soId).longValue();
        em.createNativeQuery(
                "INSERT INTO sales_out_lines (sales_out_id, seq, product_id, warehouse_id, expected_qty, shipped_qty, qty, unit_price, tax_rate, subtotal, final_amount, created_at) " +
                "VALUES (?1, 1, ?2, ?3, 3, 3, 3, 100, 0.13, 300, 300, now()), (?1, 2, ?4, ?3, 2, 2, 2, 0, 0, 0, 0, now())")
                .setParameter(1, salesOutId).setParameter(2, paid.getId()).setParameter(3, warehouseId).setParameter(4, gift.getId()).executeUpdate();

        grantPermissions(user, "sales_return:view");
        String token = loginAndGetToken(TENANT_CODE + "G2", "giftshipuser", "Admin@1234");

        // Manually invoke the service layer (the controller path requires a more complex setup)
        // TenantContext is normally populated by TenantInterceptor during a real HTTP request;
        // set it here so the service can resolve the tenant_id filter on the sales-out header.
        TenantContext.setTenantId(tenant.getId());
        try {
            SalesReturnService svc = applicationContext.getBean(SalesReturnService.class);
            var resp = svc.shippedOutLines(salesOutId);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resp.getData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lines = (List<Map<String, Object>>) data.get("lines");
            assertThat(lines).hasSize(2);
            Map<String, Object> giftLine = lines.stream().filter(l -> Objects.equals(l.get("productId"), gift.getId())).findFirst().orElseThrow();
            assertThat(((Number) giftLine.get("qty")).intValue()).isEqualTo(2);
            assertThat(((Number) giftLine.get("unitPrice")).doubleValue()).isEqualTo(0.0);
            assertThat(((Number) giftLine.get("returnableQty")).intValue()).isEqualTo(2);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void submitLocksQuantity_secondOverLimitRejected_rejectReleasesOnce() throws Exception {
        Tenant tenant = createTestTenant(TENANT_CODE);
        User user = createTestUser(tenant.getId(), "rluser", "Admin@1234");
        Product product = createTestProduct(tenant.getId(), "RL-P1", "锁测试产品");
        Dealer dealer = dealerRepository.saveAndFlush(Dealer.builder()
                .tenantId(tenant.getId()).code("RL-D1").name("锁经销商").level("A").status("active")
                .updatedAt(java.time.OffsetDateTime.now()).build());
        long outLineId = setupShippedLine(tenant, user, dealer, product);
        // Manual approval template for SALES_RETURN so submit leaves the RMA PENDING_APPROVAL and holds the lock
        Object tplId = em.createNativeQuery(
                "INSERT INTO approval_templates (tenant_id,business_type,code,name,version_no,template_type,status,priority,reject_policy,condition_config,created_at,updated_at) " +
                "VALUES (?1,'SALES_RETURN','RLTPL','RL模板',1,'MANUAL','ENABLED',1,'RETURN_TO_SUBMITTER',NULL,now(),now()) RETURNING id")
                .setParameter(1, tenant.getId()).getSingleResult();
        Object nodeId = em.createNativeQuery(
                "INSERT INTO approval_template_nodes (template_id,tenant_id,node_order,name,approve_mode,created_at,updated_at) " +
                "VALUES (?1,?2,1,'审批','ANY',now(),now()) RETURNING id")
                .setParameter(1, ((Number)tplId).longValue()).setParameter(2, tenant.getId()).getSingleResult();
        em.createNativeQuery(
                "INSERT INTO approval_node_assignees (node_id,tenant_id,assignee_type,ref_id,display_name,created_at) " +
                "VALUES (?1,?2,'USER',?3,'审批人',now())")
                .setParameter(1, ((Number)nodeId).longValue()).setParameter(2, tenant.getId()).setParameter(3, user.getId()).executeUpdate();

        grantPermissions(user, "sales_return:create", "sales_return:submit", "sales_return:view", "sales_return:approve", "sales_return:reject", "approval:view");
        String token = loginAndGetToken(TENANT_CODE, "rluser", "Admin@1234");

        // First RMA returns 6 of 10 -> submit locks 6
        long rma1 = createRma(token, outLineId, product.getId(), 6);
        mockMvc.perform(post("/api/sales-returns/" + rma1 + "/submit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertThat(lockedOf(outLineId)).isEqualByComparingTo("6");

        // Second RMA tries 5 but only 4 remain -> creating the draft itself must be rejected (40001),
        // because create() validates available quantity against other pending RMAs.
        MvcResult over = mockMvc.perform(post("/api/sales-returns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rmaBody(outLineId, product.getId(), resolveSalesOutId(outLineId), resolveDealerId(outLineId), resolveWarehouseId(outLineId), 5))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40001)).andReturn();
        assertThat(lockedOf(outLineId)).isEqualByComparingTo("6");

        // A second RMA within the remaining 4 can be created and submitted, raising lock to 10.
        long rma2 = createRma(token, outLineId, product.getId(), 4);
        mockMvc.perform(post("/api/sales-returns/" + rma2 + "/submit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertThat(lockedOf(outLineId)).isEqualByComparingTo("10");

        // Reject first RMA -> releases only its 6 (not doubled), lock drops to 4.
        mockMvc.perform(post("/api/sales-returns/" + rma1 + "/reject").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertThat(lockedOf(outLineId)).isEqualByComparingTo("4");
    }
}
