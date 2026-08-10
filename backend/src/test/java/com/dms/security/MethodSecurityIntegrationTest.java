package com.dms.security;

import com.dms.BaseIntegrationTest;
import com.dms.tenant.entity.Tenant;
import com.dms.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MethodSecurityIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Non-privileged users cannot access P0 administration APIs")
    void should_forbidP0AdminApis_when_userLacksPermissions() throws Exception {
        Tenant tenant = createTestTenant("T-PERM-P0");
        User user = createTestUser(tenant.getId(), "limited", "Dms@123456");
        grantPermissions(user, "order:search");
        String token = loginAndGetToken("T-PERM-P0", "limited", "Dms@123456");

        String[] paths = {
                "/api/users?page=1&size=5",
                "/api/roles",
                "/api/approval/admin/instances?page=1&size=5",
                "/api/product-mappings?page=1&size=5",
                "/api/tenant-ui/pages/products/buttons"
        };

        for (String path : paths) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }
}