package com.dms.asynctask.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步导入导出专用线程池（BIZ-07）。
 * 导入/导出为 IO 密集型，使用较大队列、有界线程数，避免拖垮主请求线程。
 */
@Configuration
public class AsyncTaskExecutorConfig {

    @Bean("asyncImportExportExecutor")
    public Executor asyncImportExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("dms-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}