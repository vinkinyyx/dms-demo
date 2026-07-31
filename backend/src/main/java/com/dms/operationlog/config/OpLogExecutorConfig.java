package com.dms.operationlog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 操作日志异步队列与 Executor 配置（v3.6.2 R4）。
 *
 * - 队列上限 10000，超出丢弃 + 计数
 * - 3 个 worker 线程处理
 * - 提供 op_log_dropped_total 计数器
 */
@Slf4j
@Configuration
public class OpLogExecutorConfig {

    public static final int QUEUE_CAPACITY = 10_000;
    public static final int WORKER_COUNT = 3;

    private final AtomicLong droppedTotal = new AtomicLong(0);

    @Bean("opLogQueue")
    public BlockingQueue<Runnable> opLogQueue() {
        return new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    }

    @Bean("opLogExecutor")
    public ExecutorService opLogExecutor(BlockingQueue<Runnable> opLogQueue) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "op-log-worker-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                WORKER_COUNT, WORKER_COUNT,
                0L, TimeUnit.MILLISECONDS,
                opLogQueue,
                factory,
                (r, executor) -> {
                    long dropped = droppedTotal.incrementAndGet();
                    if (dropped <= 5 || dropped % 100 == 0) {
                        log.warn("op_log queue full, dropped total={}", dropped);
                    }
                }
        );
        log.info("OpLogExecutor started: workers={}, queueCapacity={}", WORKER_COUNT, QUEUE_CAPACITY);
        return exec;
    }

    @Bean("opLogDroppedTotal")
    public AtomicLong opLogDroppedTotal() {
        return droppedTotal;
    }
}
