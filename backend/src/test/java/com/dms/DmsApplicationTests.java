/*
 * 应用上下文加载冒烟测试，确保骨架配置能够被 Spring 正确装配。
 */
package com.dms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class DmsApplicationTests {

    @Test
    void contextLoads() {
        // 若上下文能正常启动即视为通过
    }
}
