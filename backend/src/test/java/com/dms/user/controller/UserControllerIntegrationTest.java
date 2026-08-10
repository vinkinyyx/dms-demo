package com.dms.user.controller;

import com.dms.BaseIntegrationTest;
import com.dms.rbac.entity.Role;
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

    @Test
    @DisplayName("No token returns 401 or 403")
    void should_returnUnauthorized_when_noToken() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(result -> {
                    int responseStatus = result.getResponse().getStatus();
                    assertThat(responseStatus).isIn(401, 403);
                });
    }

    @Test
    @DisplayName("Authorized user can create and search users")
    void should_createUser_when_authorized() throws Exception {
        Tenant tenant = createTestTenant("T-USER-CRUD");
        User admin = createTestUser(tenant.getId(), "admin1", "Admin@1234");
        Role adminRole = grantPermissions(admin, "user:create", "user:search");
        String token = loginAndGetToken("T-USER-CRUD", "admin1", "Admin@1234");

        String body = objectMapper.writeValueAsString(Map.of(
                "tenantId", tenant.getId().toString(),
                "username", "newuser",
                "name", "New User",
                "userType", "dealer_user",
                "password", "Init@1234",
                "email", "newuser@test.local",
                "phone", "13800138000",
                "roleId", adminRole.getId()
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
    @DisplayName("Duplicate username returns conflict")
    void should_returnConflict_when_usernameDuplicated() throws Exception {
        Tenant tenant = createTestTenant("T-USER-DUP");
        User admin = createTestUser(tenant.getId(), "admin1", "Admin@1234");
        createTestUser(tenant.getId(), "duplicated", "Xx@12345");
        Role adminRole = grantPermissions(admin, "user:create");
        String token = loginAndGetToken("T-USER-DUP", "admin1", "Admin@1234");

        String body = objectMapper.writeValueAsString(Map.of(
                "tenantId", tenant.getId().toString(),
                "username", "duplicated",
                "name", "Duplicate User",
                "userType", "dealer_user",
                "password", "Init@1234",
                "email", "duplicated@test.local",
                "phone", "13800138001",
                "roleId", adminRole.getId()
        ));

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    @DisplayName("User profile update takes effect")
    void should_updateProfile_when_putUser() throws Exception {
        Tenant tenant = createTestTenant("T-USER-UPD");
        User admin = createTestUser(tenant.getId(), "admin2", "Admin@1234");
        grantPermissions(admin, "user:edit");
        String token = loginAndGetToken("T-USER-UPD", "admin2", "Admin@1234");

        String body = objectMapper.writeValueAsString(Map.of("name", "Updated Name", "email", "n@ok.com"));

        mockMvc.perform(put("/api/users/" + admin.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.email").value("n@ok.com"));
    }

    @Test
    @DisplayName("Administrator can unlock users")
    void should_unlockUser_when_postUnlock() throws Exception {
        Tenant tenant = createTestTenant("T-USER-UNLK");
        User admin = createTestUser(tenant.getId(), "admin3", "Admin@1234");
        User locked = createTestUser(tenant.getId(), "lockedUser", "Any@1234");
        grantPermissions(admin, "user:edit");
        locked.setLockedUntil(OffsetDateTime.now().plusHours(1));
        locked.setLoginFailCount(9);
        userRepository.saveAndFlush(locked);

        String token = loginAndGetToken("T-USER-UNLK", "admin3", "Admin@1234");

        mockMvc.perform(post("/api/users/" + locked.getId() + "/unlock")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        User after = userRepository.findById(locked.getId()).orElseThrow();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.getLoginFailCount()).isZero();
    }

    @Test
    @DisplayName("Administrator can reset passwords")
    void should_resetPassword_when_postResetPassword() throws Exception {
        Tenant tenant = createTestTenant("T-USER-RST");
        User admin = createTestUser(tenant.getId(), "admin4", "Admin@1234");
        User target = createTestUser(tenant.getId(), "targetUser", "Old@1234");
        grantPermissions(admin, "user:reset_password");
        String token = loginAndGetToken("T-USER-RST", "admin4", "Admin@1234");

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
    @DisplayName("Short reset password fails validation")
    void should_returnValidationError_when_newPasswordTooShort() throws Exception {
        Tenant tenant = createTestTenant("T-USER-VAL");
        User admin = createTestUser(tenant.getId(), "admin5", "Admin@1234");
        User target = createTestUser(tenant.getId(), "shortpwd", "Old@1234");
        grantPermissions(admin, "user:reset_password");
        String token = loginAndGetToken("T-USER-VAL", "admin5", "Admin@1234");

        String body = objectMapper.writeValueAsString(Map.of("newPassword", "123"));

        mockMvc.perform(post("/api/users/" + target.getId() + "/reset-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}