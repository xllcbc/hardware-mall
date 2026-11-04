package com.example.mystore.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCacheMetricsTest {

    @Test
    void recordsFailuresAndEmitsRecoveryOnlyWhenLeavingFailureState() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PricingCacheMetrics metrics = new PricingCacheMetrics(registry);

        metrics.recordFailure("lock_timeout");
        metrics.recordFailure("redis_error");

        assertThat(registry.get("pricing.cache.invalidation")
                .tags("result", "failure", "cause", "lock_timeout")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.get("pricing.cache.invalidation")
                .tags("result", "failure", "cause", "redis_error")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.get("pricing.cache.invalidation.consecutive.failures")
                .gauge()
                .value()).isEqualTo(2.0);

        metrics.recordSuccess();
        metrics.recordSuccess();

        assertThat(registry.get("pricing.cache.invalidation.recoveries")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.get("pricing.cache.invalidation.consecutive.failures")
                .gauge()
                .value()).isZero();
    }
}
