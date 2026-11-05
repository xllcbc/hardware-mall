package com.example.mystore.event;

import com.example.mystore.common.constant.ProductCacheConstants;
import com.example.mystore.common.exception.ProductCacheInvalidationException;
import com.example.mystore.common.metrics.ProductCacheMetrics;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.service.impl.DingTalkAlertService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class ProductCacheInvalidationListenerTest {

    @Mock
    private ProductCacheService productCacheService;

    @Mock
    private DingTalkAlertService alertService;

    private ProductCacheInvalidationListener listener;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new ProductCacheInvalidationListener(
                productCacheService,
                new ProductCacheMetrics(meterRegistry),
                alertService);
    }

    @Test
    void skuChanged_invalidatesSkuAndDependentProductCaches() {
        listener.onProductCacheInvalidation(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SKU_CHANGED,
                11L,
                22L,
                false));

        verify(productCacheService).invalidateAfterSkuChange(11L, 22L);
    }

    @Test
    void spuChanged_invalidatesRecommendWhenAffected() {
        listener.onProductCacheInvalidation(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SPU_CHANGED,
                11L,
                null,
                true));

        verify(productCacheService).invalidateAfterSpuChange(11L, true);
    }

    @Test
    void skuBatchChanged_invalidatesAllSkuCachesAtOnce() {
        listener.onProductSkuBatchChanged(new ProductSkuBatchChangedEvent(
                11L, java.util.List.of(22L, 23L)));

        verify(productCacheService).invalidateAfterSkuBatchChange(11L, java.util.List.of(22L, 23L));
    }

    @Test
    void listener_runsAfterCommitWithFallbackExecution() throws Exception {
        Method method = ProductCacheInvalidationListener.class.getMethod(
                "onProductCacheInvalidation", ProductCacheInvalidationEvent.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
    }

    @Test
    void redisFailure_doesNotFailCommittedRequest() {
        doThrow(new ProductCacheInvalidationException(List.of(
                new ProductCacheInvalidationException.Failure(
                        "sku:info:g:22", ProductCacheMetrics.CAUSE_REDIS_ERROR))))
                .when(productCacheService)
                .invalidateAfterSkuChange(11L, 22L);

        assertThatCode(() -> listener.onProductCacheInvalidation(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SKU_CHANGED,
                11L,
                22L,
                false)))
                .doesNotThrowAnyException();

        assertThat(counter(ProductCacheMetrics.CAUSE_REDIS_ERROR)).isEqualTo(1.0);
        verify(alertService).alert(
                eq(ProductCacheConstants.Alert.INVALIDATION_FAILED),
                contains("sku:info:g:22"));
    }

    @Test
    void successfulInvalidationRecordsSuccessWithoutAlert() {
        listener.onProductCacheInvalidation(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SKU_CHANGED,
                11L,
                22L,
                false));

        Counter success = meterRegistry.find("product.cache.invalidation")
                .tag("result", "success")
                .counter();
        assertThat(success).isNotNull();
        assertThat(success.count()).isEqualTo(1.0);
    }

    private double counter(String cause) {
        Counter counter = meterRegistry.find("product.cache.invalidation")
                .tags("result", "failure", "cause", cause)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
