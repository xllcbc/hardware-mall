package com.example.mystore.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ProductCacheMetrics {

    public static final String CAUSE_LOCK_TIMEOUT = "lock_timeout";
    public static final String CAUSE_REDIS_ERROR = "redis_error";

    private static final String OUTCOMES_METRIC = "product.cache.invalidation";

    private final MeterRegistry meterRegistry;

    public ProductCacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSuccess() {
        meterRegistry.counter(OUTCOMES_METRIC, "result", "success").increment();
    }

    public void recordFailure(String cause) {
        Counter.builder(OUTCOMES_METRIC)
                .description("Product cache invalidation outcomes")
                .tag("result", "failure")
                .tag("cause", cause)
                .register(meterRegistry)
                .increment();
    }
}
