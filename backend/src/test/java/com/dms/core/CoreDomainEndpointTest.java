package com.dms.core;

import com.dms.BaseIntegrationTest;
import com.dms.tenant.entity.Tenant;
import com.dms.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that the core business-domain endpoints required by the seven
 * critical chains (orders/outbound, stock moves, BOM/promotions, approval,
 * purchase/receipt, outbound/RMA) are actually mapped and secured.
 *
 *  - Without a token each list endpoint must reject (401/403), proving the
 *    security chain is in front of the controller.
 *  - With an authenticated user (no special permissions) each list endpoint
 *    must not 404 or 500. It may answer 200 or 403, but never 404 (mapping
 *    missing) or 500 (broken wiring). This is the exact check AGENTS.md 5.2
 *    demands: verify the backend mapping exists, do not let the front-end
 *    swallow a 404.
 *
 * Deep behavioural assertions for pricing/BOM/promotions live in
 * {@link com.dms.v4.V4CalculatorPromotionTest}.
 */
class CoreDomainEndpointTest extends BaseIntegrationTest {

    @Autowired
    com.fasterxml.jackson.databind.ObjectMapper om;

    @ParameterizedTest(name = "{0} requires authentication (no 404/500)")
    @ValueSource(strings = {
            "/api/sales-orders?page=1&size=10",
            "/api/sales-outs?page=1&size=10",
            "/api/stock-moves?page=1&size=10",
            "/api/approval/instances/my-submitted?page=1&size=10",
            "/api/purchase-orders?page=1&size=10",
            "/api/receipts?page=1&size=10",
            "/api/sales-returns?page=1&size=10"
    })
    void listEndpoints_areMappedAndSecured(String path) throws Exception {
        // 1) Anonymous must not reach the controller as a "success".
        int anonymous = mockMvc.perform(get(path))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(anonymous)
                .as("anonymous access to " + path)
                .isIn(401, 403);

        // 2) Authenticated request must not produce 404 (no mapping) or 5xx.
        Tenant tenant = createTestTenant("CORE-" + UUID.randomUUID().toString().substring(0, 6));
        User user = createTestUser(tenant.getId(), "coreuser", "Admin@1234");
        grantPermissions(user, "*");
        String token = loginAndGetToken(tenant.getCode(), "coreuser", "Admin@1234");

        MockHttpServletRequestBuilder req = get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        int authed = mockMvc.perform(req).andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(authed)
                .as("authenticated access to " + path + " must not be 404/5xx")
                .isNotIn(404, 500, 502, 503);
    }
}

