package com.example.mystore.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class StockCacheAsyncConfigTest {

    @Test
    void stockExecutor_usesSingleThreadToPreserveOrder() {
        ThreadPoolTaskExecutor executor = new StockCacheAsyncConfig().stockCacheExecutor();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(1);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.DiscardOldestPolicy.class);
        } finally {
            executor.shutdown();
        }
    }
}
