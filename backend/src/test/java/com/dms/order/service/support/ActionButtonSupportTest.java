package com.dms.order.service.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionButtonSupportTest {
    @Test
    void action_buildsOrderedFrontendActionDescriptor() {
        Map<String, Object> action = ActionButtonSupport.action("approve", "审批通过", "success", "POST", "/approve");
        assertThat(action).containsExactly(
                Map.entry("key", "approve"),
                Map.entry("label", "审批通过"),
                Map.entry("type", "success"),
                Map.entry("method", "POST"),
                Map.entry("path", "/approve")
        );
    }
}