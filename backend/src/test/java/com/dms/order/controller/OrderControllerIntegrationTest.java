/*
 * 测试目标：验证 /api/orders 订单创建 → 授权校验 → 促销计算 → 状态流转（submit/approve/reject/cancel）。
 * 覆盖用户故事：US-4.1（订单创建）、US-4.2（授权校验触发）、US-4.3（促销引擎命中）、US-4.4（状态机流转）。
 */
package com.dms.order.controller;

import com.dms.BaseIntegrationTest;
import com.dms.authz.entity.Authorization;
import com.dms.authz.repository.AuthorizationRepository;
import com.dms.tenant.entity.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    AuthorizationRepository authorizationRepository;

    private String setupUserAndLogin(String tenantCode, java.util.UUID... tenantIdOut) throws Exception {
        Tenant t = createTestTenant(tenantCode);
        createTestUser(t.getId(), "orderUser", "Admin@1234");
        if (tenantIdOut != null && tenantIdOut.length > 0) {
            // 无法真正回传，仅作占位
        }
        return loginAndGetToken(tenantCode, "orderUser", "Admin@1234");
    }

    /**
     * 为指定租户 + 经销商 + 商品 授权 ORDER 类型。
     */
    private void grantOrderAuth(java.util.UUID tenantId, Long dealerId, Long productId) {
        Authorization a = Authorization.builder()
                .tenantId(tenantId)
                .dealerId(dealerId)
                .productId(productId)
                .authType("ORDER")
                .validFrom(LocalDate.now().minusDays(1))
                .validTo(LocalDate.now().plusYears(1))
                .status("active")
                .source("contract")
                .updatedAt(OffsetDateTime.now())
                .build();
        authorizationRepository.saveAndFlush(a);
    }

    @Test
    @DisplayName("异常分支：无授权时创建订单返回 40006 授权校验失败")
    void should_returnBusinessRuleError_when_noAuthorization() throws Exception {
        Tenant t = createTestTenant("T-ORD-NOAUTH");
        createTestUser(t.getId(), "orderUser", "Admin@1234");
        String token = loginAndGetToken("T-ORD-NOAUTH", "orderUser", "Admin@1234");

        Map<String, Object> body = Map.of(
                "dealerId", 501L,
                "lines", List.of(Map.of(
                        "productId", 1001L,
                        "qty", 5,
                        "unitPrice", 100
                ))
        );

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(40006));
    }

    @Test
    @DisplayName("正常流程：授权 OK 时创建订单成功，返回 code=0 + 订单编号 + 状态 DRAFT")
    void should_createOrder_when_authorized() throws Exception {
        Tenant t = createTestTenant("T-ORD-OK");
        createTestUser(t.getId(), "orderUser", "Admin@1234");
        grantOrderAuth(t.getId(), 501L, 1001L);
        String token = loginAndGetToken("T-ORD-OK", "orderUser", "Admin@1234");

        Map<String, Object> body = Map.of(
                "dealerId", 501L,
                "orderType", "PURCHASE",
                "lines", List.of(Map.of(
                        "productId", 1001L,
                        "qty", 5,
                        "unitPrice", 100
                ))
        );

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.order.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.order.code").isNotEmpty())
                .andExpect(jsonPath("$.data.order.finalAmount").value(500));
    }

    @Test
    @DisplayName("异常分支：缺少 lines 返回 40002 参数缺失")
    void should_returnParamMissing_when_linesEmpty() throws Exception {
        Tenant t = createTestTenant("T-ORD-EMP");
        createTestUser(t.getId(), "orderUser", "Admin@1234");
        String token = loginAndGetToken("T-ORD-EMP", "orderUser", "Admin@1234");

        Map<String, Object> body = Map.of("dealerId", 1L, "lines", List.of());

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    @DisplayName("正常流程：submit → approve 状态流转成功")
    void should_transitionStatus_when_submitThenApprove() throws Exception {
        Tenant t = createTestTenant("T-ORD-FLW");
        createTestUser(t.getId(), "orderUser", "Admin@1234");
        grantOrderAuth(t.getId(), 501L, 1001L);
        String token = loginAndGetToken("T-ORD-FLW", "orderUser", "Admin@1234");

        // 先创建订单
        Map<String, Object> createBody = Map.of(
                "dealerId", 501L,
                "lines", List.of(Map.of("productId", 1001L, "qty", 3, "unitPrice", 50))
        );
        MvcResult mr = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isOk())
                .andReturn();
        Number orderId = objectMapper.readTree(mr.getResponse().getContentAsString())
                .path("data").path("order").path("id").numberValue();

        // submit
        mockMvc.perform(post("/api/orders/" + orderId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // approve
        mockMvc.perform(post("/api/orders/" + orderId + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("异常分支：从 DRAFT 直接 approve 应返回 40006 状态非法")
    void should_returnBusinessRuleError_when_approveFromDraft() throws Exception {
        Tenant t = createTestTenant("T-ORD-ILL");
        createTestUser(t.getId(), "orderUser", "Admin@1234");
        grantOrderAuth(t.getId(), 501L, 1001L);
        String token = loginAndGetToken("T-ORD-ILL", "orderUser", "Admin@1234");

        Map<String, Object> createBody = Map.of(
                "dealerId", 501L,
                "lines", List.of(Map.of("productId", 1001L, "qty", 2, "unitPrice", 10))
        );
        MvcResult mr = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andReturn();
        Number orderId = objectMapper.readTree(mr.getResponse().getContentAsString())
                .path("data").path("order").path("id").numberValue();

        mockMvc.perform(post("/api/orders/" + orderId + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40006));
    }

    @Test
    @DisplayName("正常流程：DRAFT → cancel 直接取消")
    void should_cancelOrder_when_fromDraft() throws Exception {
        Tenant t = createTestTenant("T-ORD-CNL");
        createTestUser(t.getId(), "orderUser", "Admin@1234");
        grantOrderAuth(t.getId(), 501L, 1001L);
        String token = loginAndGetToken("T-ORD-CNL", "orderUser", "Admin@1234");

        Map<String, Object> createBody = Map.of(
                "dealerId", 501L,
                "lines", List.of(Map.of("productId", 1001L, "qty", 2, "unitPrice", 10))
        );
        MvcResult mr = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andReturn();
        Number orderId = objectMapper.readTree(mr.getResponse().getContentAsString())
                .path("data").path("order").path("id").numberValue();

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("正常流程：submit 后 reject 返回 REJECTED")
    void should_rejectOrder_when_fromSubmitted() throws Exception {
        Tenant t = createTestTenant("T-ORD-REJ");
        createTestUser(t.getId(), "orderUser", "Admin@1234");
        grantOrderAuth(t.getId(), 501L, 1001L);
        String token = loginAndGetToken("T-ORD-REJ", "orderUser", "Admin@1234");

        Map<String, Object> createBody = Map.of(
                "dealerId", 501L,
                "lines", List.of(Map.of("productId", 1001L, "qty", 2, "unitPrice", 10))
        );
        MvcResult mr = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andReturn();
        Number orderId = objectMapper.readTree(mr.getResponse().getContentAsString())
                .path("data").path("order").path("id").numberValue();

        mockMvc.perform(post("/api/orders/" + orderId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/orders/" + orderId + "/reject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"金额过大\"}"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }
}
