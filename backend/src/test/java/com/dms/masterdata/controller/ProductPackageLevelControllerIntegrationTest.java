package com.dms.masterdata.controller;

import com.dms.BaseIntegrationTest;
import com.dms.tenant.entity.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductPackageLevelControllerIntegrationTest extends BaseIntegrationTest {

    private String setupTenantAndLogin(String code) throws Exception {
        Tenant t = createTestTenant(code);
        createTestUser(t.getId(), "pplAdmin", "Admin@1234");
        return loginAndGetToken(code, "pplAdmin", "Admin@1234");
    }

    private Long createTestProduct(String token) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("code", "T-P-" + System.currentTimeMillis());
        m.put("nameCn", "测试产品");
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

    private Map<String, Object> levelBody(Long productId, Integer level, Integer quantity) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", productId);
        m.put("level", level);
        m.put("code", "PL-" + level);
        m.put("name", "层级" + level);
        m.put("quantity", quantity);
        m.put("uom", "piece");
        m.put("status", "active");
        return m;
    }

    @Test
    @DisplayName("正常：创建单品层级成功")
    void should_createPackageLevel_when_valid() throws Exception {
        String token = setupTenantAndLogin("T-PPL-1");
        Long productId = createTestProduct(token);

        mockMvc.perform(post("/api/product-package-levels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(levelBody(productId, 4, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.level").value(4));
    }

    @Test
    @DisplayName("异常：关联产品不存在返回 404")
    void should_reject_when_productNotExists() throws Exception {
        String token = setupTenantAndLogin("T-PPL-2");

        mockMvc.perform(post("/api/product-package-levels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(levelBody(999999L, 1, 1))))
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("异常：层级 0 被拒绝")
    void should_reject_when_levelZero() throws Exception {
        String token = setupTenantAndLogin("T-PPL-3");
        Long productId = createTestProduct(token);

        mockMvc.perform(post("/api/product-package-levels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(levelBody(productId, 0, 1))))
                .andExpect(jsonPath("$.code").value(40001));
    }
}
