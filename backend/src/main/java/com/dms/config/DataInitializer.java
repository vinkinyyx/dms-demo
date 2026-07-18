/*
 * 应用启动初始化器，在 seed 开关开启时输出默认管理员账号提示，方便本地开发登录。
 */
package com.dms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataInitializer implements ApplicationRunner {

    @Value("${dms.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Seed 数据初始化未启用，跳过默认账号提示");
            return;
        }
        log.info("========================================================");
        log.info("  DMS 骨架已启动");
        log.info("  默认超管账号: admin / Sh123456");
        log.info("  Swagger UI:  /swagger-ui.html");
        log.info("  健康检查:    /actuator/health");
        log.info("========================================================");
    }
}
