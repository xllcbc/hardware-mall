package com.example.mystore.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PricingCacheMetrics {

    public static final String CAUSE_LOCK_TIMEOUT = "lock_timeout";
    public static final String CAUSE_REDIS_ERROR = "redis_error";

    private static final String OUTCOMES_METRIC = "pricing.cache.invalidation";
    private static final String RECOVERIES_METRIC = "pricing.cache.invalidation.recoveries";
    private static final String CONSECUTIVE_FAILURES_METRIC =
            "pricing.cache.invalidation.consecutive.failures";

    private final MeterRegistry meterRegistry;
    private final Counter recoveries;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    public PricingCacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.recoveries = Counter.builder(RECOVERIES_METRIC)
                .description("Number of pricing cache invalidation recoveries")
                .register(meterRegistry);
        Gauge.builder(CONSECUTIVE_FAILURES_METRIC, consecutiveFailures, AtomicInteger::get)
                .description("Consecutive pricing cache invalidation failures")
                .register(meterRegistry);
    }

    public void recordSuccess() {
        meterRegistry.counter(OUTCOMES_METRIC, "result", "success").increment();
        if (consecutiveFailures.getAndSet(0) > 0) {
            recoveries.increment();
        }
    }

    public void recordFailure(String cause) {
        consecutiveFailures.incrementAndGet();
        meterRegistry.counter(OUTCOMES_METRIC,
                "result", "failure",
                "cause", cause).increment();
    }
}
