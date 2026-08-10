/*
 * 濠电偞娼欓鍫ユ儊椤栫偞鍎庢い鏃傛櫕閸ㄥジ鏌ㄥ☉娆忓摵闁绘稒鐟ч幏?/auth 闂佺儵鏅濋…鍫ュ矗瑜旈獮鎺楀Ψ閵夈儳绋?闂佺偨鍎查弻锟犲焵?闂佽皫鍡╁殭缂傚秴绉归獮瀣箛椤掆偓椤?婵犮垺鍎肩划鍓ф喆?闁荤姵鍔х粻鎴ｃ亹閸ф鐓ュù锝呮憸閺嗕即鏌ㄥ☉娆戔槈缂佺粯娲滈幏瀣级鐠恒劍顫氶梺娲诲枙缁躲倗妲愰崡鐑嗗殫妞ゆ棁顔婄换?qrcode / callback / bind 闂佺绻堥崝宥囩矈閿斿墽鐭欓悗锝冨妷閸? * 闁荤喐娲栧Λ娑樏烘繝鍥ㄥ仺闁靛绠戦悡鏇㈡煛娴ｅ憡鍣哥紒銊ｅ姂閺佸秴顫㈠?2.1闂佹寧绋戦悧蹇涘极閵堝绠ｇ€瑰嫮澧楅崐鎶芥倵闂堟稒顥犻柣鏍ㄧ矒閹倻鎷犻懠顒傂梺鎸庣☉椤︻參鍩€椤戣法鐣砈-2.2闂佹寧绋戦悧蹇撯枍閵夈劊浜归柡鍥╁亼娴滃ジ鎮归幇鈺佸姕妞ゆ劕銈稿顐ｏ紣娴ｈ櫣鎲块梻浣搞仒缁€渚€鎮炬ィ鍐╂櫖濠㈣泛鐗冮崑鎾寸▕娑?2.4闂佹寧绋戦悧鍡樼閺囩喓鈹嶉柍鈺佸暙椤ュ洭鏌ｉ鏄忓厡婵炲懌鍎撮妵鎰板即椤忓棛顦梺闈涙缁犳壌-2.7闂佹寧绋戦悧鍡欐崲閺囩姵濯奸柡澶庢硶濡叉洟鏌ｉ鑽ょ瓘缂佽鲸宀告俊? */
package com.dms.auth.controller;

import com.dms.BaseIntegrationTest;
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
    @DisplayName("auth test")
    void should_returnTokens_when_correctCredentials() throws Exception {
        Tenant t = createTestTenant("T-LOGIN-OK");
        createTestUser(t.getId(), "alice", "Pass1234");

        String body = objectMapper.writeValueAsString(
                Map.of("tenantCode", "T-LOGIN-OK", "username", "alice", "password", "Pass1234"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("alice"));
    }

    @Test
    @DisplayName("auth test")
    void should_returnUnauthorized_when_wrongPassword() throws Exception {
        Tenant t = createTestTenant("T-LOGIN-BAD");
        createTestUser(t.getId(), "bob", "RightPwd123");

        String body = objectMapper.writeValueAsString(
                Map.of("tenantCode", "T-LOGIN-BAD", "username", "bob", "password", "WrongPwd"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("auth test")
    void should_returnForbidden_when_accountLocked() throws Exception {
        Tenant t = createTestTenant("T-LOCKED");
        User u = createTestUser(t.getId(), "carol", "Pass1234");
        u.setLockedUntil(OffsetDateTime.now().plusMinutes(30));
        userRepository.saveAndFlush(u);

        String body = objectMapper.writeValueAsString(
                Map.of("tenantCode", "T-LOCKED", "username", "carol", "password", "Pass1234"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    @DisplayName("auth test")
    void should_returnForbidden_when_userStatusInactive() throws Exception {
        Tenant t = createTestTenant("T-INACT");
        createTestUser(t.getId(), "dave", "Pass1234", "inactive");

        String body = objectMapper.writeValueAsString(
                Map.of("tenantCode", "T-INACT", "username", "dave", "password", "Pass1234"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    @DisplayName("auth test")
    void should_returnOk_when_forgotPasswordCalled() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", "someone@test.local"));

        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("auth test")
    void should_returnQrScene_when_requestQrcode() throws Exception {
        mockMvc.perform(post("/api/auth/wechat/qrcode").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.scene").isNotEmpty())
                .andExpect(jsonPath("$.data.qrUrl").isNotEmpty());
    }

    @Test
    @DisplayName("auth test")
    void should_returnBadRequest_when_wechatCodeInvalid() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("code", "BAD_CODE", "state", "x"));
        mockMvc.perform(post("/api/auth/wechat/callback").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("auth test")
    void should_returnBindToken_when_openidNotBound() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("code", "MOCK_OPENID_test001", "state", "s"));

        mockMvc.perform(post("/api/auth/wechat/callback").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.needBind").value(true))
                .andExpect(jsonPath("$.data.bindToken").isNotEmpty());
    }

    @Test
    @DisplayName("auth test")
    void should_bindAndLogin_when_wechatBindWithValidToken() throws Exception {
        Tenant t = createTestTenant("T-WX-BIND");
        createTestUser(t.getId(), "eric", "Pass1234");

        // 闂佸憡顨嗗ú婊呪偓?mock 婵炴垶鎸撮崑鎾斥槈?bindToken 闁诲海鏁搁幊鎾惰姳閺屻儲鍎?bucket
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = Mockito.mock(RBucket.class);
        Mockito.when(bucket.get()).thenReturn("MOCK_OPENID_eric001");
        Mockito.doReturn(bucket).when(redissonClient).getBucket(Mockito.contains("dms:wechat:bind:BT123"));

        String body = objectMapper.writeValueAsString(Map.of(
                "bindToken", "BT123",
                "username", "eric",
                "password", "Pass1234",
                "tenantCode", "T-WX-BIND"
        ));

        mockMvc.perform(post("/api/auth/wechat/bind").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        User bound = userRepository.findByTenantIdAndUsername(t.getId(), "eric").orElseThrow();
        assertThat(bound.getWechatOpenid()).isEqualTo("MOCK_OPENID_eric001");
    }

    @Test
    @DisplayName("auth test")
    void should_returnBusinessRuleError_when_bindTokenExpired() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "bindToken", "EXPIRED_TOKEN",
                "username", "x",
                "password", "y",
                "tenantCode", "no-such"
        ));

        mockMvc.perform(post("/api/auth/wechat/bind").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40006));
    }
}
