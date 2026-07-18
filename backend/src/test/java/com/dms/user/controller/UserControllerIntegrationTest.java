/*
 * 测试目标：验证 /api/users 用户管理接口 —— CRUD + 解锁 + 重置密码。
 * 覆盖用户故事：US-2.3（用户 CRUD）、US-2.2（管理员解锁 / 重置密码）。
 */
package com.dms.user.controller;

import com.dms.BaseIntegrationTest;
import com.dms.tenant.entity.Tenant;
import com.dms.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIntegrationTest extends BaseIntegrationTest {

    private String tokenFor(String tenantCode, String username, String pwd) throws Exception {
        return loginAndGetToken(tenantCode, username, pwd);
    }

    @Test
    @DisplayName("异常分支：无 token 访问用户接口返回 401/403")
    void should_returnUnauthorized_when_noToken() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(401, 403);
                });
    }

    @Test
    @DisplayName("正常流程：登录后创建用户并可分页查询到该用户")
    void should_createUser_when_authorized() throws Exception {
        Tenant t = createTestTenant("T-USER-CRUD");
        createTestUser(t.getId(), "admin1", "Admin@1234");
        String token = tokenFor("T-USER-CRUD", "admin1", "Admin@1234");

        String body = objectMapper.writeValueAsString(Map.of(
                "tenantId", t.getId().toString(),
                "username", "newuser",
                "name", "新员工",
                "userType", "dealer_user",
                "password", "Init@1234"
        ));

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    @Test
    @DisplayName("异常分支：用户名在同租户内重复返回 40901 资源冲突")
    void should_returnConflict_when_usernameDuplicated() throws Exception {
        Tenant t = createTestTenant("T-USER-DUP");
        createTestUser(t.getId(), "admin1", "Admin@1234");
        createTestUser(t.getId(), "duplicated", "Xx@12345");
        String token = tokenFor("T-USER-DUP", "admin1", "Admin@1234");

        String body = objectMapper.writeValueAsString(Map.of(
                "tenantId", t.getId().toString(),
                "username", "duplicated",
                "name", "重复",
                "userType", "dealer_user",
                "password", "Init@1234"
        ));

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    @DisplayName("正常流程：更新用户 profile 生效")
    void should_updateProfile_when_putUser() throws Exception {
        Tenant t = createTestTenant("T-USER-UPD");
        User admin = createTestUser(t.getId(), "admin2", "Admin@1234");
        String token = tokenFor("T-USER-UPD", "admin2", "Admin@1234");

        String body = objectMapper.writeValueAsString(Map.of("name", "新名字", "email", "n@ok.com"));

        mockMvc.perform(put("/api/users/" + admin.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名字"))
                .andExpect(jsonPath("$.data.email").value("n@ok.com"));
    }

    @Test
    @DisplayName("正常流程：管理员解锁用户后 lockedUntil 变 null")
    void should_unlockUser_when_postUnlock() throws Exception {
        Tenant t = createTestTenant("T-USER-UNLK");
        User admin = createTestUser(t.getId(), "admin3", "Admin@1234");
        User locked = createTestUser(t.getId(), "lockedUser", "Any@1234");
        locked.setLockedUntil(OffsetDateTime.now().plusHours(1));
        locked.setLoginFailCount(9);
        userRepository.saveAndFlush(locked);

        String token = tokenFor("T-USER-UNLK", "admin3", "Admin@1234");

        mockMvc.perform(post("/api/users/" + locked.getId() + "/unlock")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        User after = userRepository.findById(locked.getId()).orElseThrow();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.getLoginFailCount()).isZero();
    }

    @Test
    @DisplayName("正常流程：管理员重置密码后 mustChangePassword=true 且新密码可登录")
    void should_resetPassword_when_postResetPassword() throws Exception {
        Tenant t = createTestTenant("T-USER-RST");
        User admin = createTestUser(t.getId(), "admin4", "Admin@1234");
        User target = createTestUser(t.getId(), "targetUser", "Old@1234");
        String token = tokenFor("T-USER-RST", "admin4", "Admin@1234");

        String body = objectMapper.writeValueAsString(Map.of("newPassword", "NewPass@1234"));

        mockMvc.perform(post("/api/users/" + target.getId() + "/reset-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        User after = userRepository.findById(target.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass@1234", after.getPasswordHash())).isTrue();
        assertThat(after.getMustChangePassword()).isTrue();
    }

    @Test
    @DisplayName("异常分支：重置密码时 newPassword 长度不足触发校验")
    void should_returnValidationError_when_newPasswordTooShort() throws Exception {
        Tenant t = createTestTenant("T-USER-VAL");
        User admin = createTestUser(t.getId(), "admin5", "Admin@1234");
        User target = createTestUser(t.getId(), "shortpwd", "Old@1234");
        String token = tokenFor("T-USER-VAL", "admin5", "Admin@1234");

        String body = objectMapper.writeValueAsString(Map.of("newPassword", "123"));

        mockMvc.perform(post("/api/users/" + target.getId() + "/reset-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
