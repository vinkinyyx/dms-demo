package com.dms.authz.service;

import com.dms.BaseIntegrationTest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.authz.dto.AuthorizationCheckRequest;
import com.dms.authz.dto.AuthorizationCheckResult;
import com.dms.authz.entity.Authorization;
import com.dms.common.BusinessException;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.entity.Hospital;
import com.dms.masterdata.entity.Product;
import com.dms.masterdata.entity.ProductLine;
import com.dms.masterdata.repository.HospitalRepository;
import com.dms.masterdata.repository.ProductLineRepository;
import com.dms.system.service.SystemSettingService;
import com.dms.tenant.entity.Tenant;
import com.dms.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 授权服务集成测试：开关两态、产品线/终端匹配、跨经销商排他、续约、终止回调。
 */
class AuthorizationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthorizationService authorizationService;
    @Autowired private AuthorizationApprovalCallback authorizationCallback;
    @Autowired private SystemSettingService systemSettingService;
    @Autowired private ProductLineRepository productLineRepository;
    @Autowired private HospitalRepository hospitalRepository;

    private Tenant tenant;
    private Dealer dealer1;
    private Dealer dealer2;
    private ProductLine pl1;
    private ProductLine pl2;
    private Hospital h1;
    private Hospital h2;
    private Product productOnPl1;

    @BeforeEach
    void setup() {
        tenant = createTestTenant("AUTHZ-T");
        User user = createTestUser(tenant.getId(), "authz-admin", "Pass@1234");
        TenantContext.setTenantId(tenant.getId());
        TenantContext.setUserId(user.getId());
        TenantContext.setUsername(user.getUsername());

        dealer1 = createTestDealer(tenant.getId(), "D-AUTHZ-1", "授权经销商一");
        dealer2 = createTestDealer(tenant.getId(), "D-AUTHZ-2", "授权经销商二");

        pl1 = productLineRepository.saveAndFlush(ProductLine.builder()
                .tenantId(tenant.getId()).code("PL-1").name("介入产品线").level(1).status("active").sortOrder(1).build());
        pl2 = productLineRepository.saveAndFlush(ProductLine.builder()
                .tenantId(tenant.getId()).code("PL-2").name("骨科产品线").level(1).status("active").sortOrder(2).build());

        h1 = hospitalRepository.saveAndFlush(Hospital.builder()
                .tenantId(tenant.getId()).code("H-1").name("第一人民医院").status("active").build());
        h2 = hospitalRepository.saveAndFlush(Hospital.builder()
                .tenantId(tenant.getId()).code("H-2").name("第二人民医院").status("active").build());

        productOnPl1 = Product.builder()
                .tenantId(tenant.getId()).code("P-PL1").nameCn("介入耗材A").spec("S").unit("box")
                .productLineId(pl1.getId()).status("active").build();
        productOnPl1 = productRepository.saveAndFlush(productOnPl1);
    }

    @AfterEach
    void cleanup() { TenantContext.clear(); }

    private AuthorizationCheckRequest.Line line(Long productId, Long terminalId) {
        AuthorizationCheckRequest.Line l = new AuthorizationCheckRequest.Line();
        l.setProductId(productId);
        l.setTerminalId(terminalId);
        return l;
    }

    private Authorization buildAuth(Dealer dealer, String lineCsv, String terminalCsv,
                                     LocalDate from, LocalDate to) {
        Authorization a = new Authorization();
        a.setDealerId(dealer.getId());
        a.setProductLines(lineCsv);
        a.setTerminalIds(terminalCsv);
        a.setValidFrom(from);
        a.setValidTo(to);
        a.setAuthType("ORDER");
        return a;
    }

    @Test
    void switchOff_byDefault_authorizationNotEnforced() {
        // 默认无配置：开关关闭，即使没有任何授权也应全部放行
        assertFalse(systemSettingService.isOrderAuthzEnforced(tenant.getId()));
        AuthorizationCheckRequest req = new AuthorizationCheckRequest();
        req.setDealerId(dealer1.getId());
        req.setAuthType("ORDER");
        req.setLines(List.of(line(productOnPl1.getId(), null)));
        List<AuthorizationCheckResult> results = authorizationService.check(req);
        assertEquals(1, results.size());
        assertTrue(results.get(0).getAuthorized(), "开关关闭时应放行");
    }

    @Test
    void switchOn_noAuthorization_rejected() {
        systemSettingService.setOrderAuthzEnforced(true);
        AuthorizationCheckRequest req = new AuthorizationCheckRequest();
        req.setDealerId(dealer1.getId());
        req.setAuthType("ORDER");
        req.setLines(List.of(line(productOnPl1.getId(), null)));
        List<AuthorizationCheckResult> results = authorizationService.check(req);
        assertFalse(results.get(0).getAuthorized(), "无授权应拦截");
        assertNotEquals("OK", results.get(0).getReason());
    }

    @Test
    void switchOn_matchingProductLine_orderAuthorized() {
        systemSettingService.setOrderAuthzEnforced(true);
        // 给 dealer1 授权 PL1 + H1
        authorizationService.create(buildAuth(dealer1, String.valueOf(pl1.getId()),
                String.valueOf(h1.getId()), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));

        AuthorizationCheckRequest req = new AuthorizationCheckRequest();
        req.setDealerId(dealer1.getId());
        req.setAuthType("ORDER");
        req.setLines(List.of(line(productOnPl1.getId(), null)));
        List<AuthorizationCheckResult> results = authorizationService.check(req);
        assertTrue(results.get(0).getAuthorized(), "产品线命中 ORDER 应放行（ORDER 不校验终端）");

        // PL2 未授权 -> 拦截
        Product pPl2 = productRepository.saveAndFlush(Product.builder()
                .tenantId(tenant.getId()).code("P-PL2").nameCn("骨科耗材B").spec("S").unit("box")
                .productLineId(pl2.getId()).status("active").build());
        AuthorizationCheckRequest req2 = new AuthorizationCheckRequest();
        req2.setDealerId(dealer1.getId());
        req2.setAuthType("ORDER");
        req2.setLines(List.of(line(pPl2.getId(), null)));
        assertFalse(authorizationService.check(req2).get(0).getAuthorized(), "未授权产品线应拦截");
    }

    @Test
    void switchOn_salesToHospital_terminalMatched() {
        systemSettingService.setOrderAuthzEnforced(true);
        Authorization hospitalAuth = buildAuth(dealer1,
                String.valueOf(pl1.getId()), String.valueOf(h1.getId()),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
        hospitalAuth.setAuthType("SALES_TO_HOSPITAL");
        Authorization created = authorizationService.create(hospitalAuth);
        assertEquals("active", created.getStatus(), "无审批模板应自动通过并生效");

        AuthorizationCheckRequest req = new AuthorizationCheckRequest();
        req.setDealerId(dealer1.getId());
        req.setAuthType("SALES_TO_HOSPITAL");
        // H1 在授权范围 -> 放行；H2 不在 -> 拦截
        req.setLines(List.of(line(productOnPl1.getId(), h1.getId()), line(productOnPl1.getId(), h2.getId())));
        List<AuthorizationCheckResult> results = authorizationService.check(req);
        assertTrue(results.get(0).getAuthorized(), "授权终端 H1 应放行");
        assertFalse(results.get(1).getAuthorized(), "未授权终端 H2 应拦截");
    }

    @Test
    void exclusivity_overlappingDifferentDealer_rejected() {
        systemSettingService.setOrderAuthzEnforced(true);
        authorizationService.create(buildAuth(dealer1, String.valueOf(pl1.getId()),
                String.valueOf(h1.getId()), LocalDate.now(), LocalDate.now().plusDays(30)));

        Authorization conflict = buildAuth(dealer2, String.valueOf(pl1.getId()),
                String.valueOf(h1.getId()), LocalDate.now().plusDays(10), LocalDate.now().plusDays(40));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authorizationService.create(conflict));
        assertTrue(ex.getMessage().contains("授权冲突") || ex.getMessage().contains("不能重复授权"),
                "应抛排他冲突，实际: " + ex.getMessage());
    }

    @Test
    void exclusivity_nonOverlappingRange_allowed() {
        systemSettingService.setOrderAuthzEnforced(true);
        authorizationService.create(buildAuth(dealer1, String.valueOf(pl1.getId()),
                String.valueOf(h1.getId()), LocalDate.now(), LocalDate.now().plusDays(10)));
        // 时间段不重叠（在前一段结束之后）-> 即使同医院同产品线给另一家也允许
        Authorization later = buildAuth(dealer2, String.valueOf(pl1.getId()),
                String.valueOf(h1.getId()), LocalDate.now().plusDays(11), LocalDate.now().plusDays(40));
        Authorization saved = assertDoesNotThrow(() -> authorizationService.create(later));
        assertNotNull(saved.getId());
    }

    @Test
    void renew_sameDealer_copiesScopeAndAllowed() {
        systemSettingService.setOrderAuthzEnforced(true);
        Authorization original = authorizationService.create(buildAuth(dealer1,
                String.valueOf(pl1.getId()), String.valueOf(h1.getId()),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(5)));

        Authorization renewReq = new Authorization();
        renewReq.setValidFrom(LocalDate.now().plusDays(6));
        renewReq.setValidTo(LocalDate.now().plusDays(40));
        Authorization renewed = authorizationService.renew(original.getId(), renewReq);

        assertNotEquals(original.getId(), renewed.getId());
        assertEquals(dealer1.getId(), renewed.getDealerId());
        assertEquals(original.getProductLines(), renewed.getProductLines());
        assertEquals(original.getTerminalIds(), renewed.getTerminalIds());
        assertEquals("renew", renewed.getSource());
    }

    @Test
    void terminate_whenNoApprovalTemplate_autoTerminated() {
        // 测试环境无审批模板：发起终止会自动通过（AUTO_APPROVED），直接置 terminated
        Authorization active = authorizationService.create(buildAuth(dealer1,
                String.valueOf(pl1.getId()), String.valueOf(h1.getId()),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
        assertEquals("active", active.getStatus());

        Authorization result = authorizationService.terminate(active.getId(), "合作终止");
        assertEquals("terminated", result.getStatus(), "无审批模板时终止应自动通过");
    }

    @Test
    void terminateApprovalCallback_whenPending_setsTerminated() {
        // 直接构造一条 active 授权，置为 terminate_pending，再模拟终止审批通过回调
        Authorization active = authorizationService.create(buildAuth(dealer1,
                String.valueOf(pl2.getId()), String.valueOf(h2.getId()),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
        // 手动置为终止审批中（模拟有人工审批模板的中间态）
        active.setStatus("terminate_pending");
        com.dms.authz.repository.AuthorizationRepository repo =
                applicationContext.getBean(com.dms.authz.repository.AuthorizationRepository.class);
        repo.saveAndFlush(active);

        ApprovalInstance inst = ApprovalInstance.builder()
                .businessId(active.getId()).businessType(AuthorizationService.BT_AUTHORIZATION_TERMINATE).build();
        authorizationCallback.onApproved(inst);

        Authorization reloaded = authorizationService.getDetail(active.getId());
        assertEquals("terminated", reloaded.getStatus());
    }

    @Test
    void terminateReject_restoresActive() {
        Authorization active = authorizationService.create(buildAuth(dealer1,
                String.valueOf(pl1.getId()), String.valueOf(h2.getId()),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
        active.setStatus("terminate_pending");
        com.dms.authz.repository.AuthorizationRepository repo =
                applicationContext.getBean(com.dms.authz.repository.AuthorizationRepository.class);
        repo.saveAndFlush(active);

        ApprovalInstance inst = ApprovalInstance.builder()
                .businessId(active.getId()).businessType(AuthorizationService.BT_AUTHORIZATION_TERMINATE)
                .businessSnapshot(java.util.Map.of("prevStatus", "active")).build();
        authorizationCallback.onRejected(inst);
        assertEquals("active", authorizationService.getDetail(active.getId()).getStatus(), "终止驳回应恢复生效");
    }

    @Test
    void orderEnforceSwitch_writeThenRead_isConsistent() {
        // 写入后立即读取必须一致（缓存失效 key 与读取 key 必须一致）
        assertFalse(systemSettingService.isOrderAuthzEnforced(tenant.getId()), "默认关闭");
        systemSettingService.setOrderAuthzEnforced(true);
        assertTrue(systemSettingService.isOrderAuthzEnforced(tenant.getId()), "开启后应立即可读");
        assertTrue(systemSettingService.isOrderAuthzEnforced(), "服务层默认租户读取应一致");
        systemSettingService.setOrderAuthzEnforced(false);
        assertFalse(systemSettingService.isOrderAuthzEnforced(tenant.getId()), "关闭后应立即可读");
    }

    @Test
    void create_missingProductLine_rejected() {
        Authorization bad = buildAuth(dealer1, null, String.valueOf(h1.getId()),
                LocalDate.now(), LocalDate.now().plusDays(10));
        BusinessException ex = assertThrows(BusinessException.class, () -> authorizationService.create(bad));
        assertTrue(ex.getMessage().contains("产品线"));
    }
}
