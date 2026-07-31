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

class ProductLineControllerIntegrationTest extends BaseIntegrationTest {

    private String setupTenantAndLogin(String code) throws Exception {
        Tenant t = createTestTenant(code);
        createTestUser(t.getId(), "plAdmin", "Admin@1234");
        return loginAndGetToken(code, "plAdmin", "Admin@1234");
    }

    private Map<String, Object> lineBody(String code, String name, Integer level) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("level", level);
        m.put("status", "active");
        return m;
    }

    @Test
    @DisplayName("正常：BU层级产品线创建成功")
    void should_createBuLine_when_levelIsOne() throws Exception {
        String token = setupTenantAndLogin("T-PL-BU");

        mockMvc.perform(post("/api/product-lines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lineBody("BU001", "医疗BU", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("BU001"))
                .andExpect(jsonPath("$.data.level").value(1));
    }

    @Test
    @DisplayName("正常：产品线列表查询")
    void should_list_when_tokenValid() throws Exception {
        String token = setupTenantAndLogin("T-PL-LIST");

        mockMvc.perform(post("/api/product-lines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lineBody("BU010", "骨科BU", 1))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/product-lines")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].code").value("BU010"));
    }

    @Test
    @DisplayName("异常：层级超出 1-3 范围被拒绝")
    void should_reject_when_levelOutOfRange() throws Exception {
        String token = setupTenantAndLogin("T-PL-LEVEL");

        mockMvc.perform(post("/api/product-lines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lineBody("BU020", "非法层级", 5))))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("异常：编码重复返回 409")
    void should_rejectDuplicateCode() throws Exception {
        String token = setupTenantAndLogin("T-PL-DUP");

        mockMvc.perform(post("/api/product-lines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lineBody("BU030", "重复测试", 1))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/product-lines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lineBody("BU030", "重复测试2", 1))))
                .andExpect(jsonPath("$.code").value(40901));
    }
}
