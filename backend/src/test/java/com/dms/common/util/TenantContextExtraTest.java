package com.dms.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextExtraTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_storeTenantTypeAndAuthSource() {
        UUID tid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        TenantContext.setTenantId(tid);
        TenantContext.setTenantType("DEALER");
        TenantContext.setOwnerManufacturerId(owner);
        TenantContext.setAuthSource(TenantContext.AUTH_SOURCE_TENANT);

        assertThat(TenantContext.getTenantType()).isEqualTo("DEALER");
        assertThat(TenantContext.getOwnerManufacturerId()).isEqualTo(owner);
        assertThat(TenantContext.isPlatformAdmin()).isFalse();

        TenantContext.setAuthSource(TenantContext.AUTH_SOURCE_PLATFORM);
        assertThat(TenantContext.isPlatformAdmin()).isTrue();
    }

    @Test
    void clear_should_resetAllFields() {
        TenantContext.setTenantType("MANUFACTURER");
        TenantContext.setAuthSource(TenantContext.AUTH_SOURCE_PLATFORM);
        TenantContext.clear();
        assertThat(TenantContext.getTenantType()).isNull();
        assertThat(TenantContext.getAuthSource()).isNull();
        assertThat(TenantContext.isPlatformAdmin()).isFalse();
    }
}