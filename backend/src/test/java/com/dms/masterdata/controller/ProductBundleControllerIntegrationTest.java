package com.dms.masterdata.controller;

import com.dms.BaseIntegrationTest;
import com.dms.tenant.entity.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductBundleControllerIntegrationTest extends BaseIntegrationTest {

    private String setupTenantAndLogin(String code) throws Exception {
        Tenant t = createTestTenant(code);
        com.dms.user.entity.User pbAdmin = createTestUser(t.getId(), "pbAdmin", "Admin@1234");
        grantPermissions(pbAdmin, "product:view", "product:search", "product:create", "product:edit",
                "product_bundle:view", "product_bundle:create", "product_bundle:edit");
        return loginAndGetToken(code, "pbAdmin", "Admin@1234");
    }

    private Long createTestProduct(String token, String code) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("nameCn", "测试产品-" + code);
        m.put("unit", "piece");
        m.put("status", "active");
        String resp = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(m)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Map<String, Object> bundleBody(Long parentProductId, String pricingType) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", parentProductId);
        m.put("code", "BUNDLE-" + System.currentTimeMillis());
        m.put("name", "测试组套");
        m.put("pricingType", pricingType);
        m.put("allowSplit", false);
        m.put("status", "active");
        if ("OVERRIDE".equals(pricingType)) {
            m.put("bundlePrice", new BigDecimal("999.00"));
        }
        return m;
    }

    @Test
    @DisplayName("正常：INHERIT 定价方式创建组套成功")
    void should_createBundle_when_inheritPricing() throws Exception {
        String token = setupTenantAndLogin("T-PB-INH");
        Long parentProductId = createTestProduct(token, "PARENT-" + System.currentTimeMillis());

        mockMvc.perform(post("/api/product-bundles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundleBody(parentProductId, "INHERIT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pricingType").value("INHERIT"));
    }

    @Test
    @DisplayName("正常：OVERRIDE 定价方式带价格成功")
    void should_createBundle_when_overrideWithPrice() throws Exception {
        String token = setupTenantAndLogin("T-PB-OVR");
        Long parentProductId = createTestProduct(token, "PARENT-" + System.currentTimeMillis());

        mockMvc.perform(post("/api/product-bundles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundleBody(parentProductId, "OVERRIDE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bundlePrice").value(999.00));
    }

    @Test
    @DisplayName("异常：OVERRIDE 没有 bundlePrice 被拒绝")
    void should_rejectOverride_when_noPrice() throws Exception {
        String token = setupTenantAndLogin("T-PB-NOP");
        Long parentProductId = createTestProduct(token, "PARENT-" + System.currentTimeMillis());

        Map<String, Object> badBundle = bundleBody(parentProductId, "OVERRIDE");
        badBundle.remove("bundlePrice");

        mockMvc.perform(post("/api/product-bundles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badBundle)))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("异常：无效定价方式被拒绝")
    void should_reject_when_invalidPricingType() throws Exception {
        String token = setupTenantAndLogin("T-PB-BAD");
        Long parentProductId = createTestProduct(token, "PARENT-" + System.currentTimeMillis());

        mockMvc.perform(post("/api/product-bundles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundleBody(parentProductId, "INVALID_TYPE"))))
                .andExpect(jsonPath("$.code").value(40001));
    }
}
