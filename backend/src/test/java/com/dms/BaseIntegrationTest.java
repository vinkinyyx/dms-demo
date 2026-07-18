/*
 * DMS 集成测试基类：启动 Spring 上下文（MOCK 环境） + MockMvc + 事务回滚。
 * 提供 createTestTenant / createTestUser / loginAndGetToken 等 helper。
 *
 * 通过 @MockBean 打桩 RedissonClient，避免依赖真实 Redis。
 * 通过覆盖 SecurityConfig 使得测试不强制 JWT（子类通过 loginAndGetToken 获取 token 后自行携带）。
 */
package com.dms;

import com.dms.tenant.entity.Tenant;
import com.dms.tenant.repository.TenantRepository;
import com.dms.user.entity.User;
import com.dms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
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
    protected PasswordEncoder passwordEncoder;

    /**
     * 使用 @MockBean 打桩 RedissonClient，避免测试依赖 Redis。
     */
    @MockBean
    protected RedissonClient redissonClient;

    @BeforeEach
    void baseSetup() {
        // 每个测试前提供一个宽松的 RBucket mock，避免 NPE
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = Mockito.mock(RBucket.class);
        Mockito.when(redissonClient.getBucket(Mockito.anyString())).thenReturn(bucket);
        Mockito.when(bucket.get()).thenReturn(null);
        Mockito.when(bucket.isExists()).thenReturn(false);
    }

    /**
     * 创建一个测试租户并入库。
     */
    protected Tenant createTestTenant(String code) {
        Tenant t = Tenant.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name("测试租户-" + code)
                .industry("medical")
                .timezone("Asia/Shanghai")
                .status("active")
                .modulesEnabled(new HashMap<>())
                .quota(new HashMap<>())
                .attrs(new HashMap<>())
                .updatedAt(OffsetDateTime.now())
                .build();
        t.ensureJsonFields();
        return tenantRepository.saveAndFlush(t);
    }

    /**
     * 在指定租户下创建一个测试用户（密码用 passwordEncoder 加密）。
     */
    protected User createTestUser(UUID tenantId, String username, String rawPassword) {
        return createTestUser(tenantId, username, rawPassword, "active");
    }

    protected User createTestUser(UUID tenantId, String username, String rawPassword, String status) {
        User u = User.builder()
                .tenantId(tenantId)
                .username(username)
                .name(username)
                .userType("dealer_user")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .mustChangePassword(false)
                .passwordUpdatedAt(OffsetDateTime.now())
                .email(username + "@test.local")
                .status(status)
                .loginFailCount(0)
                .attrs(new HashMap<>())
                .updatedAt(OffsetDateTime.now())
                .build();
        u.ensureAttrs();
        return userRepository.saveAndFlush(u);
    }

    /**
     * 使用测试账号密码登录并返回 accessToken（供子类透传到 Authorization 头）。
     */
    protected String loginAndGetToken(String tenantCode, String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("tenantCode", tenantCode, "username", username, "password", password));
        String resp = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/auth/login")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("accessToken").asText();
    }
}
