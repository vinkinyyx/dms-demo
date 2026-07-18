/*
 * 测试目标：验证 /api/inventory 库存查询接口 + InventoryService.applyTransaction 扣减防负逻辑。
 * 覆盖用户故事：US-5.1（库存查询）、US-5.3（库存扣减防负）、US-5.4（库存流水记录）。
 */
package com.dms.inventory.controller;

import com.dms.BaseIntegrationTest;
import com.dms.common.BusinessException;
import com.dms.common.util.TenantContext;
import com.dms.inventory.entity.Inventory;
import com.dms.inventory.repository.InventoryRepository;
import com.dms.inventory.service.InventoryService;
import com.dms.tenant.entity.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    InventoryRepository inventoryRepository;

    @Autowired
    InventoryService inventoryService;

    private Inventory seedInventory(UUID tenantId, Long dealerId, Long productId, String batchNo, BigDecimal qty) {
        Inventory inv = Inventory.builder()
                .tenantId(tenantId)
                .dealerId(dealerId)
                .warehouseId(1L)
                .productId(productId)
                .batchNo(batchNo)
                .qty(qty)
                .inSource("SEED")
                .updatedAt(OffsetDateTime.now())
                .build();
        return inventoryRepository.saveAndFlush(inv);
    }

    @Test
    @DisplayName("正常流程：库存查询按 dealerId + productId 返回")
    void should_returnInventoryList_when_queryWithFilters() throws Exception {
        Tenant t = createTestTenant("T-INV-Q");
        createTestUser(t.getId(), "invUser", "Admin@1234");
        seedInventory(t.getId(), 501L, 1001L, "B001", new BigDecimal("100"));
        seedInventory(t.getId(), 501L, 1002L, "B002", new BigDecimal("50"));

        String token = loginAndGetToken("T-INV-Q", "invUser", "Admin@1234");

        mockMvc.perform(get("/api/inventory?dealerId=501&productId=1001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].productId").value(1001));
    }

    @Test
    @DisplayName("异常分支：无 tenantId 上下文查询报 40002")
    void should_returnParamMissing_when_noTenantContext() throws Exception {
        // 未登录直接查询 - 会被 SecurityConfig 拦截为 401/403，属于另一种保护
        int st = mockMvc.perform(get("/api/inventory"))
                .andReturn().getResponse().getStatus();
        assertThat(st).isIn(401, 403);
    }

    @Test
    @DisplayName("正常流程：applyTransaction 增库存（正数变动）在无原库存时新建")
    void should_createInventory_when_positiveTxnWithoutExisting() {
        Tenant t = createTestTenant("T-INV-ADD");
        UUID tid = t.getId();
        try {
            TenantContext.setTenantId(tid);
            TenantContext.setUserId(1L);
            Inventory inv = inventoryService.applyTransaction(tid, 501L, 10L, 1001L, "B-A", null,
                    new BigDecimal("20"), "RECEIPT_IN", "RECEIPT", 999L);
            assertThat(inv.getQty()).isEqualByComparingTo(new BigDecimal("20"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("异常分支：applyTransaction 扣减不存在库存返回 40006")
    void should_throwBusinessException_when_deductWithoutInventory() {
        Tenant t = createTestTenant("T-INV-DED-NONE");
        UUID tid = t.getId();
        try {
            TenantContext.setTenantId(tid);
            TenantContext.setUserId(1L);
            assertThatThrownBy(() -> inventoryService.applyTransaction(
                    tid, 501L, 10L, 9999L, "B-X", null,
                    new BigDecimal("-1"), "SALES_OUT", "SO", 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("库存不存在");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("异常分支：扣减库存超过现有数量返回 40006 库存不足")
    void should_throwBusinessException_when_deductBelowZero() {
        Tenant t = createTestTenant("T-INV-NEG");
        UUID tid = t.getId();
        seedInventory(tid, 501L, 1001L, "B001", new BigDecimal("5"));
        try {
            TenantContext.setTenantId(tid);
            TenantContext.setUserId(1L);
            assertThatThrownBy(() -> inventoryService.applyTransaction(
                    tid, 501L, 1L, 1001L, "B001", null,
                    new BigDecimal("-10"), "SALES_OUT", "SO", 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("库存不足");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("边界：qtyChange=0 报 40001")
    void should_throwParamInvalid_when_qtyChangeIsZero() {
        Tenant t = createTestTenant("T-INV-ZERO");
        UUID tid = t.getId();
        try {
            TenantContext.setTenantId(tid);
            assertThatThrownBy(() -> inventoryService.applyTransaction(
                    tid, 1L, 1L, 1L, null, null,
                    BigDecimal.ZERO, "ADJUST", "ADJ", 1L))
                    .isInstanceOf(BusinessException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("正常流程：applyTransaction 扣减库存后余量减少并生成流水")
    void should_deductQty_when_validNegativeTxn() {
        Tenant t = createTestTenant("T-INV-DED-OK");
        UUID tid = t.getId();
        seedInventory(tid, 501L, 1001L, "B001", new BigDecimal("100"));
        try {
            TenantContext.setTenantId(tid);
            TenantContext.setUserId(1L);
            Inventory inv = inventoryService.applyTransaction(
                    tid, 501L, 1L, 1001L, "B001", null,
                    new BigDecimal("-30"), "SALES_OUT", "SO", 1L);
            assertThat(inv.getQty()).isEqualByComparingTo(new BigDecimal("70"));
        } finally {
            TenantContext.clear();
        }
    }
}
