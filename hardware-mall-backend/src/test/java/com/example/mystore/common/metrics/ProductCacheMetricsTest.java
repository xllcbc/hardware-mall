package com.example.mystore.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCacheMetricsTest {

    @Test
    void recordsSuccessAndFailuresByCause() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductCacheMetrics metrics = new ProductCacheMetrics(registry);

        metrics.recordSuccess();
        metrics.recordFailure(ProductCacheMetrics.CAUSE_LOCK_TIMEOUT);
        metrics.recordFailure(ProductCacheMetrics.CAUSE_REDIS_ERROR);

        assertThat(registry.get("product.cache.invalidation")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.get("product.cache.invalidation")
                .tags("result", "failure", "cause", ProductCacheMetrics.CAUSE_LOCK_TIMEOUT)
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.get("product.cache.invalidation")
                .tags("result", "failure", "cause", ProductCacheMetrics.CAUSE_REDIS_ERROR)
                .counter()
                .count()).isEqualTo(1.0);
    }
}
