/*
 * Product master data integration tests: productType + categoryId persistence and echo.
 */
package com.dms.masterdata.controller;

import com.dms.BaseIntegrationTest;
import com.dms.masterdata.entity.ProductCategory;
import com.dms.masterdata.repository.ProductCategoryRepository;
import com.dms.tenant.entity.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    ProductCategoryRepository categoryRepository;

    private Long createCategory(Tenant tenant, String code, String name) {
        ProductCategory c = ProductCategory.builder()
                .tenantId(tenant.getId())
                .code(code)
                .name(name)
                .level(1)
                .status("active")
                .build();
        return categoryRepository.saveAndFlush(c).getId();
    }

    @Test
    @DisplayName("产品类型与分类在创建/更新后可正确保存并在详情/列表回显")
    void should_persistProductTypeAndCategory_when_createAndUpdate() throws Exception {
        Tenant t = createTestTenant("T-PROD-TC");
        createTestUser(t.getId(), "prodAdmin", "Admin@1234");
        String token = loginAndGetToken("T-PROD-TC", "prodAdmin", "Admin@1234");

        Long categoryId = createCategory(t, "CAT-TC", "耗材分类");

        // create with productType + categoryId
        String createBody = objectMapper.writeValueAsString(Map.of(
                "code", "PROD-TC-1",
                "nameCn", "产品类型回归测试",
                "productType", "CONSUMABLE",
                "categoryId", categoryId,
                "unit", "个",
                "status", "active"
        ));
        String createResp = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.productType").value("CONSUMABLE"))
                .andExpect(jsonPath("$.data.categoryId").value(categoryId))
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(createResp).path("data").path("id").asLong();

        // detail echoes both
        mockMvc.perform(get("/api/products/" + productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productType").value("CONSUMABLE"))
                .andExpect(jsonPath("$.data.categoryId").value(categoryId))
                .andExpect(jsonPath("$.data.categoryName").value("耗材分类"));

        // update productType + categoryId and verify persisted
        Long categoryId2 = createCategory(t, "CAT-TC-2", "设备分类");
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "productType", "EQUIPMENT",
                "categoryId", categoryId2
        ));
        mockMvc.perform(put("/api/products/" + productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productType").value("EQUIPMENT"))
                .andExpect(jsonPath("$.data.categoryId").value(categoryId2));

        mockMvc.perform(get("/api/products/" + productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productType").value("EQUIPMENT"))
                .andExpect(jsonPath("$.data.categoryId").value(categoryId2))
                .andExpect(jsonPath("$.data.categoryName").value("设备分类"));
    }
}
