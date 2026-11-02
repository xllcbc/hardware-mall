package com.example.mystore.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class StockCacheAsyncConfigTest {

    @Test
    void stockExecutor_usesSingleThreadToPreserveOrder() {
        ThreadPoolTaskExecutor executor = new StockCacheAsyncConfig().stockCacheExecutor();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }
}
