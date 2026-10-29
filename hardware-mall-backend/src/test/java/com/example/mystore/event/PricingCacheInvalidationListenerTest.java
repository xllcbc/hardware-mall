package com.example.mystore.event;

import com.example.mystore.service.PricingRuleService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PricingCacheInvalidationListenerTest {

    @Mock
    private PricingRuleService pricingRuleService;

    @InjectMocks
    private PricingCacheInvalidationListener listener;

    @Test
    void matrixChange_evictsOnlyLevelCache() {
        listener.onPricingCacheInvalidation(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));

        verify(pricingRuleService).evictLevelCache(2L);
        verify(pricingRuleService, never()).evictRetailLevelIdCache();
    }

    @Test
    void levelStatusChange_evictsLevelAndRetailLookupCaches() {
        listener.onPricingCacheInvalidation(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, 2L));

        verify(pricingRuleService).evictLevelCache(2L);
        verify(pricingRuleService).evictRetailLevelIdCache();
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
    void redisFailure_doesNotFailCommittedRequest() {
        doThrow(new RuntimeException("redis unavailable"))
                .when(pricingRuleService)
                .evictLevelCache(2L);

        assertThatCode(() -> listener.onPricingCacheInvalidation(
                new PricingCacheInvalidationEvent(
                        PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L)))
                .doesNotThrowAnyException();
    }
}
