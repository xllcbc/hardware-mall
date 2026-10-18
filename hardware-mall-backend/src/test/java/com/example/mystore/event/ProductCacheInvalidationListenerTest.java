package com.example.mystore.event;

import com.example.mystore.service.ProductCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class ProductCacheInvalidationListenerTest {

    @Mock
    private ProductCacheService productCacheService;

    @InjectMocks
    private ProductCacheInvalidationListener listener;

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
        doThrow(new RuntimeException("redis unavailable"))
                .when(productCacheService)
                .invalidateAfterSkuChange(11L, 22L);

        assertThatCode(() -> listener.onProductCacheInvalidation(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SKU_CHANGED,
                11L,
                22L,
                false)))
                .doesNotThrowAnyException();
    }
}
