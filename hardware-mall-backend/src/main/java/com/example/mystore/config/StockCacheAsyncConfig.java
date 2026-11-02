package com.example.mystore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class StockCacheAsyncConfig {

    @Bean(name = "stockCacheExecutor")
    public ThreadPoolTaskExecutor stockCacheExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("stock-cache-");
        executor.initialize();
        return executor;
    }
}
