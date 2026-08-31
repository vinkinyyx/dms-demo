/*
 * 测试目标：验证 /api/promotions 促销 CRUD 与 promoType 白名单校验。
 * 现行规则：CRUD 仅接受 GIFT（满A赠B）与 FULL_REDUCTION（满A减钱）；
 * MOQ/BUNDLE/未知类型一律返回 40001 参数非法。
 */
package com.dms.promotion.controller;

import com.dms.BaseIntegrationTest;
import com.dms.tenant.entity.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PromotionControllerIntegrationTest extends BaseIntegrationTest {

    private String setupTenantAndLogin(String code) throws Exception {
        Tenant t = createTestTenant(code);
        com.dms.user.entity.User promoAdmin = createTestUser(t.getId(), "promoAdmin", "Admin@1234");
        grantPermissions(promoAdmin, "promotion:view", "promotion:search", "promotion:edit");
        return loginAndGetToken(code, "promoAdmin", "Admin@1234");
    }

    private Map<String, Object> promoBody(String type, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", "P-" + type);
        m.put("name", name);
        m.put("promoType", type);
        m.put("priority", 60);
        m.put("exclusive", false);
        m.put("status", "draft");
        return m;
    }

    @Test
    @DisplayName("正常流程：创建 GIFT 促销成功")
    void should_createGiftPromotion_when_typeValid() throws Exception {
        String token = setupTenantAndLogin("T-PROMO-GIFT");

        mockMvc.perform(post("/api/promotions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(promoBody("GIFT", "赠品活动"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.promoType").value("GIFT"))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    @DisplayName("正常流程：创建 FULL_REDUCTION 促销成功")
    void should_createFullReductionPromotion_when_typeValid() throws Exception {
        String token = setupTenantAndLogin("T-PROMO-FR");

        mockMvc.perform(post("/api/promotions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(promoBody("FULL_REDUCTION", "满减活动"))))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.promoType").value("FULL_REDUCTION"));
    }

    @Test
    @DisplayName("异常分支：MOQ 类型不在 CRUD 白名单，返回 40001")
    void should_reject_when_promoTypeIsMoq() throws Exception {
        String token = setupTenantAndLogin("T-PROMO-MOQ");

        mockMvc.perform(post("/api/promotions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(promoBody("MOQ", "起订量活动"))))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("异常分支：BUNDLE 类型不支持，返回 40001")
    void should_reject_when_promoTypeIsBundle() throws Exception {
        String token = setupTenantAndLogin("T-PROMO-BND");

        mockMvc.perform(post("/api/promotions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(promoBody("BUNDLE", "捆绑活动"))))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("异常分支：非法 promoType 返回 40001 参数错误")
    void should_returnParamInvalid_when_promoTypeUnknown() throws Exception {
        String token = setupTenantAndLogin("T-PROMO-BAD");

        mockMvc.perform(post("/api/promotions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(promoBody("UNKNOWN_TYPE", "非法"))))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("正常流程：list 分页查询返回结构体")
    void should_returnPageResult_when_listPromotions() throws Exception {
        String token = setupTenantAndLogin("T-PROMO-LIST");

        mockMvc.perform(get("/api/promotions?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").exists());
    }
}