package com.example.mystore.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
public class RefundAsyncConfig {

    @Bean(name = "refundExecutor")
    public ThreadPoolTaskExecutor refundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        // 饱和时退回调用线程执行并记录日志, 避免自动退款事件被静默丢弃
        executor.setRejectedExecutionHandler((task, pool) -> {
            log.warn("退款执行线程池饱和, 由调用线程执行任务, activeCount={}, queueSize={}",
                    pool.getActiveCount(), pool.getQueue().size());
            task.run();
        });
        executor.setThreadNamePrefix("late-payment-refund-");
        executor.initialize();
        return executor;
    }
}
