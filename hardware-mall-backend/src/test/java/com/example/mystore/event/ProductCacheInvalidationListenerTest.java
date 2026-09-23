package com.example.mystore.event;

import com.example.mystore.service.ProductCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

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
}
