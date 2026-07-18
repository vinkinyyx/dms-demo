/*
 * 测试目标：验证 /auth 相关接口 —— 登录成功/失败/账号锁定；忘记密码；微信 qrcode / callback / bind 全流程。
 * 覆盖用户故事：US-2.1（用户名密码登录）、US-2.2（登录失败次数与锁定）、US-2.4（微信扫码登录）、US-2.7（忘记密码）。
 */
package com.dms.auth.controller;

import com.dms.BaseIntegrationTest;
import com.dms.tenant.entity.Tenant;
import com.dms.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RBucket;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("正常流程：正确账号密码登录返回 accessToken/refreshToken")
    void should_returnTokens_when_correctCredentials() throws Exception {
        Tenant t = createTestTenant("T-LOGIN-OK");
        createTestUser(t.getId(), "alice", "Pass1234");

        String body = objectMapper.writeValueAsString(
                Map.of("tenantCode", "T-LOGIN-OK", "username", "alice", "password", "Pass1234"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("alice"));
    }

    @Test
    @DisplayName("异常分支：密码错误返回 40101 未授权")
    void should_returnUnauthorized_when_wrongPassword() throws Exception {
        Tenant t = createTestTenant("T-LOGIN-BAD");
        createTestUser(t.getId(), "bob", "RightPwd123");

        String body = objectMapper.writeValueAsString(
                Map.of("tenantCode", "T-LOGIN-BAD", "username", "bob", "password", "WrongPwd"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("异常分支：账号被锁定时登录返回 40301 禁止访问")
    void should_returnForbidden_when_accountLocked() throws Exception {
        Tenant t = createTestTenant("T-LOCKED");
        User u = createTestUser(t.getId(), "carol", "Pass1234");
        u.setLockedUntil(OffsetDateTime.now().plusMinutes(30));
        userRepository.saveAndFlush(u);

        String body = objectMapper.writeValueAsString(
                Map.of("tenantCode", "T-LOCKED", "username", "carol", "password", "Pass1234"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    @DisplayName("异常分支：账号被停用时登录返回 40301")
    void should_returnForbidden_when_userStatusInactive() throws Exception {
        Tenant t = createTestTenant("T-INACT");
        createTestUser(t.getId(), "dave", "Pass1234", "inactive");

        String body = objectMapper.writeValueAsString(
                Map.of("tenantCode", "T-INACT", "username", "dave", "password", "Pass1234"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    @DisplayName("正常流程：忘记密码请求直接返回 200 成功（V1 占位实现）")
    void should_returnOk_when_forgotPasswordCalled() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", "someone@test.local"));

        mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("正常流程：wechat/qrcode 返回 scene 与 qrUrl")
    void should_returnQrScene_when_requestQrcode() throws Exception {
        mockMvc.perform(post("/auth/wechat/qrcode").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.scene").isNotEmpty())
                .andExpect(jsonPath("$.data.qrUrl").isNotEmpty());
    }

    @Test
    @DisplayName("异常分支：wechat/callback 非法 code 返回 40001")
    void should_returnBadRequest_when_wechatCodeInvalid() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("code", "BAD_CODE", "state", "x"));
        mockMvc.perform(post("/auth/wechat/callback").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("正常流程：wechat/callback 未绑定用户返回 needBind=true + bindToken")
    void should_returnBindToken_when_openidNotBound() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("code", "MOCK_OPENID_test001", "state", "s"));

        mockMvc.perform(post("/auth/wechat/callback").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.needBind").value(true))
                .andExpect(jsonPath("$.data.bindToken").isNotEmpty());
    }

    @Test
    @DisplayName("正常流程：wechat/bind 使用有效 bindToken 与账号密码完成绑定并返回 token")
    void should_bindAndLogin_when_wechatBindWithValidToken() throws Exception {
        Tenant t = createTestTenant("T-WX-BIND");
        createTestUser(t.getId(), "eric", "Pass1234");

        // 单独 mock 一个 bindToken 对应的 bucket
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = Mockito.mock(RBucket.class);
        Mockito.when(bucket.get()).thenReturn("MOCK_OPENID_eric001");
        Mockito.when(redissonClient.getBucket(Mockito.contains("dms:wechat:bind:BT123"))).thenReturn(bucket);

        String body = objectMapper.writeValueAsString(Map.of(
                "bindToken", "BT123",
                "username", "eric",
                "password", "Pass1234",
                "tenantCode", "T-WX-BIND"
        ));

        mockMvc.perform(post("/auth/wechat/bind").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        User bound = userRepository.findByTenantIdAndUsername(t.getId(), "eric").orElseThrow();
        assertThat(bound.getWechatOpenid()).isEqualTo("MOCK_OPENID_eric001");
    }

    @Test
    @DisplayName("异常分支：wechat/bind 使用过期/不存在的 bindToken 返回 40006")
    void should_returnBusinessRuleError_when_bindTokenExpired() throws Exception {
        // 默认 mock bucket.get()=null 即代表 token 过期
        String body = objectMapper.writeValueAsString(Map.of(
                "bindToken", "EXPIRED_TOKEN",
                "username", "x",
                "password", "y",
                "tenantCode", "no-such"
        ));

        mockMvc.perform(post("/auth/wechat/bind").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(40006));
    }
}
