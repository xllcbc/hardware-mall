package com.example.mystore.event;

import com.example.mystore.common.constant.PricingCacheConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.metrics.PricingCacheMetrics;
import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.util.RedisUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingCacheInvalidationListenerTest {

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private DingTalkAlertService alertService;

    private PricingCacheInvalidationListener listener;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new PricingCacheInvalidationListener(
                redisUtil, new PricingCacheMetrics(meterRegistry), alertService);
    }

    @Test
    void matrixChange_evictsOnlyLevelCache() {
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L))
                .thenReturn(true);

        listener.onPricingCacheInvalidation(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));

        assertThat(outcomeCount("success", null)).isEqualTo(1.0);
    }

    @Test
    void levelStatusChange_evictsLevelAndRetailLookupCaches() {
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L))
                .thenReturn(true);
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID, 500L))
                .thenReturn(true);

        listener.onPricingCacheInvalidation(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, 2L));

        assertThat(outcomeCount("success", null)).isEqualTo(1.0);
    }

    @Test
    void listener_runsSynchronouslyAfterCommitWithFallbackExecution() throws Exception {
        Method method = PricingCacheInvalidationListener.class.getMethod(
                "onPricingCacheInvalidation", PricingCacheInvalidationEvent.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
    }

    @Test
    void lockTimeout_recordsFailureAndDoesNotFailCommittedRequest() {
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L))
                .thenReturn(false);

        assertThatCode(() -> listener.onPricingCacheInvalidation(
                new PricingCacheInvalidationEvent(
                        PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L)))
                .doesNotThrowAnyException();

        assertThat(outcomeCount("failure", PricingCacheMetrics.CAUSE_LOCK_TIMEOUT))
                .isEqualTo(1.0);
        assertThat(consecutiveFailures()).isEqualTo(1.0);
        verify(alertService).alert(
                eq(PricingCacheConstants.Alert.INVALIDATION_FAILED),
                argThat(message -> message.contains("key=pricing:level:2")
                        && message.contains("cause=lock_timeout")));
    }

    @Test
    void redisFailure_recordsFailureAndDoesNotFailCommittedRequest() {
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThatCode(() -> listener.onPricingCacheInvalidation(
                new PricingCacheInvalidationEvent(
                        PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L)))
                .doesNotThrowAnyException();

        assertThat(outcomeCount("failure", PricingCacheMetrics.CAUSE_REDIS_ERROR))
                .isEqualTo(1.0);
        assertThat(consecutiveFailures()).isEqualTo(1.0);
        verify(alertService).alert(
                eq(PricingCacheConstants.Alert.INVALIDATION_FAILED),
                argThat(message -> message.contains("key=pricing:level:2")
                        && message.contains("cause=redis_error")));
    }

    @Test
    void partialAvailabilityInvalidationIsRecordedAsOneFailureWithoutRecovery() {
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L))
                .thenReturn(true);
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID, 500L))
                .thenReturn(false);

        listener.onPricingCacheInvalidation(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, 2L));

        assertThat(outcomeCount("failure", PricingCacheMetrics.CAUSE_LOCK_TIMEOUT))
                .isEqualTo(1.0);
        assertThat(outcomeCount("success", null)).isZero();
        assertThat(recoveryCount()).isZero();
    }

    @Test
    void mixedFailureCausesAreRecordedPerKey() {
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L))
                .thenReturn(false);
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID, 500L))
                .thenThrow(new IllegalStateException("redis unavailable"));

        listener.onPricingCacheInvalidation(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, 2L));

        assertThat(outcomeCount("failure", PricingCacheMetrics.CAUSE_LOCK_TIMEOUT))
                .isEqualTo(1.0);
        assertThat(outcomeCount("failure", PricingCacheMetrics.CAUSE_REDIS_ERROR))
                .isEqualTo(1.0);
        assertThat(consecutiveFailures()).isEqualTo(2.0);
        verify(alertService).alert(
                eq(PricingCacheConstants.Alert.INVALIDATION_FAILED),
                argThat(message -> message.contains("pricing:level:2")
                        && message.contains("pricing:retail-level-id")
                        && message.contains("lock_timeout")
                        && message.contains("redis_error")));
    }

    private double outcomeCount(String result, String cause) {
        Search search = meterRegistry.find("pricing.cache.invalidation").tag("result", result);
        if (cause != null) {
            search.tag("cause", cause);
        }
        Counter counter = search.counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double consecutiveFailures() {
        return meterRegistry.get("pricing.cache.invalidation.consecutive.failures")
                .gauge()
                .value();
    }

    private double recoveryCount() {
        Counter counter = meterRegistry.find("pricing.cache.invalidation.recoveries").counter();
        return counter == null ? 0.0 : counter.count();
    }
}
