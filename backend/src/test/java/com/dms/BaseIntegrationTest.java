package com.dms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dms.config.MinioStorageService;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.entity.Product;
import com.dms.masterdata.repository.DealerRepository;
import com.dms.masterdata.repository.ProductRepository;
import com.dms.rbac.entity.Resource;
import com.dms.rbac.entity.Role;
import com.dms.rbac.entity.RoleStrategy;
import com.dms.rbac.entity.Strategy;
import com.dms.rbac.entity.StrategyResource;
import com.dms.rbac.entity.UserRole;
import com.dms.rbac.repository.ResourceRepository;
import com.dms.rbac.repository.RoleRepository;
import com.dms.rbac.repository.RoleStrategyRepository;
import com.dms.rbac.repository.StrategyRepository;
import com.dms.rbac.repository.StrategyResourceRepository;
import com.dms.rbac.repository.UserRoleRepository;
import com.dms.tenant.entity.Tenant;
import com.dms.tenant.repository.TenantRepository;
import com.dms.user.entity.User;
import com.dms.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TenantRepository tenantRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected DealerRepository dealerRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected StrategyRepository strategyRepository;

    @Autowired
    protected ResourceRepository resourceRepository;

    @Autowired
    protected RoleStrategyRepository roleStrategyRepository;

    @Autowired
    protected StrategyResourceRepository strategyResourceRepository;

    @Autowired
    protected UserRoleRepository userRoleRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected ApplicationContext applicationContext;

    @MockBean
    protected RedissonClient redissonClient;

    @MockBean
    protected MinioStorageService minioStorageService;

    @BeforeEach
    void baseSetup() {
        RBucket<String> bucket = Mockito.mock(RBucket.class);
        Mockito.doReturn(bucket).when(redissonClient).getBucket(Mockito.anyString());
        Mockito.when(bucket.get()).thenReturn(null);
        Mockito.when(bucket.isExists()).thenReturn(false);
        // RateLimitInterceptor uses a Redisson RRateLimiter on login/mfa/forgot paths.
        // Stub it permissively so integration tests can authenticate without a live Redis.
        org.redisson.api.RRateLimiter rateLimiter = Mockito.mock(org.redisson.api.RRateLimiter.class);
        Mockito.lenient().doReturn(rateLimiter).when(redissonClient).getRateLimiter(Mockito.anyString());
        Mockito.lenient().when(rateLimiter.trySetRate(Mockito.any(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any())).thenReturn(true);
        Mockito.lenient().when(rateLimiter.tryAcquire(Mockito.anyLong())).thenReturn(true);
        Mockito.when(minioStorageService.bucket()).thenReturn("dms-test");
        Mockito.doReturn(new ByteArrayInputStream(new byte[0])).when(minioStorageService).get(Mockito.anyString());
    }

    protected Tenant createTestTenant(String code) {
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name("Test Tenant " + code)
                .industry("medical")
                .timezone("Asia/Shanghai")
                .status("active")
                .deploymentMode("SAAS")
                .modulesEnabled(new HashMap<>())
                .quota(new HashMap<>())
                .attrs(new HashMap<>())
                .tenantType("manufacturer")
                .updatedAt(OffsetDateTime.now())
                .build();
        tenant.ensureJsonFields();
        return tenantRepository.saveAndFlush(tenant);
    }

    protected User createTestUser(UUID tenantId, String username, String rawPassword) {
        return createTestUser(tenantId, username, rawPassword, "active");
    }

    protected User createTestUser(UUID tenantId, String username, String rawPassword, String status) {
        User user = User.builder()
                .tenantId(tenantId)
                .username(username)
                .name(username)
                .userType("vendor")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .mustChangePassword(false)
                .passwordUpdatedAt(OffsetDateTime.now())
                .email(username + "@test.local")
                .status(status)
                .loginFailCount(0)
                .attrs(new HashMap<>())
                .updatedAt(OffsetDateTime.now())
                .build();
        user.ensureAttrs();
        return userRepository.saveAndFlush(user);
    }

    protected Dealer createTestDealer(UUID tenantId, String code, String name) {
        Dealer dealer = Dealer.builder()
                .tenantId(tenantId)
                .code(code)
                .name(name)
                .level("A")
                .status("active")
                .updatedAt(OffsetDateTime.now())
                .build();
        return dealerRepository.saveAndFlush(dealer);
    }

    protected Product createTestProduct(UUID tenantId, String code, String name) {
        Product product = Product.builder()
                .tenantId(tenantId)
                .code(code)
                .nameCn(name)
                .spec("TEST-SPEC")
                .unit("box")
                .currentPrice(new BigDecimal("100"))
                .taxRate(new BigDecimal("0.13"))
                .isSerialManaged(false)
                .status("active")
                .updatedAt(OffsetDateTime.now())
                .build();
        return productRepository.saveAndFlush(product);
    }

    protected Role grantPermissions(User user, String... permissionCodes) {
        UUID tenantId = user.getTenantId();
        Role role = roleRepository.saveAndFlush(Role.builder()
                .tenantId(tenantId)
                .code("test-role-" + user.getId() + "-" + UUID.randomUUID())
                .name("Test Role")
                .type("custom")
                .status("active")
                .updatedAt(OffsetDateTime.now())
                .build());
        Strategy strategy = strategyRepository.saveAndFlush(Strategy.builder()
                .tenantId(tenantId)
                .name("Test Strategy")
                .status("active")
                .updatedAt(OffsetDateTime.now())
                .build());
        roleStrategyRepository.saveAndFlush(RoleStrategy.builder()
                .roleId(role.getId())
                .strategyId(strategy.getId())
                .build());
        for (String permissionCode : permissionCodes) {
            Resource resource = resourceRepository.saveAndFlush(Resource.builder()
                    .tenantId(tenantId)
                    .code(permissionCode)
                    .name(permissionCode)
                    .type("api")
                    .status("active")
                    .updatedAt(OffsetDateTime.now())
                    .build());
            strategyResourceRepository.saveAndFlush(StrategyResource.builder()
                    .strategyId(strategy.getId())
                    .resourceId(resource.getId())
                    .operations(new String[]{"*"})
                    .build());
        }
        userRoleRepository.saveAndFlush(UserRole.builder()
                .userId(user.getId())
                .roleId(role.getId())
                .build());
        entityManager().flush();
        entityManager().clear();
        return role;
    }

    protected String loginAndGetToken(String tenantCode, String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("tenantCode", tenantCode, "username", username, "password", password));
        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/auth/login")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private jakarta.persistence.EntityManager entityManager() {
        return applicationContext.getBean(jakarta.persistence.EntityManager.class);
    }
}