package com.example.mystore.event;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingCacheInvalidationListenerTest {

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private PricingCacheInvalidationListener listener;

    @Test
    void matrixChange_evictsOnlyLevelCache() {
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L))
                .thenReturn(true);

        listener.onPricingCacheInvalidation(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));

        verify(redisUtil).evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L);
        verify(redisUtil, never()).evictWithLock(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID, 500L);
    }

    @Test
    void levelStatusChange_evictsLevelAndRetailLookupCaches() {
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L))
                .thenReturn(true);
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID, 500L))
                .thenReturn(true);

        listener.onPricingCacheInvalidation(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, 2L));

        verify(redisUtil).evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L);
        verify(redisUtil).evictWithLock(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID, 500L);
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
    void lockTimeout_doesNotFailCommittedRequest() {
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L))
                .thenReturn(false);

        assertThatCode(() -> listener.onPricingCacheInvalidation(
                new PricingCacheInvalidationEvent(
                        PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L)))
                .doesNotThrowAnyException();
    }

    @Test
    void redisFailure_doesNotFailCommittedRequest() {
        when(redisUtil.evictWithLock(RedisConstants.PREFIX_PRICING_LEVEL + 2L, 500L))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThatCode(() -> listener.onPricingCacheInvalidation(
                new PricingCacheInvalidationEvent(
                        PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L)))
                .doesNotThrowAnyException();
    }
}
