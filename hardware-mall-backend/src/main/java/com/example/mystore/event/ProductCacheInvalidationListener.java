package com.example.mystore.event;

import com.example.mystore.service.ProductCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProductCacheInvalidationListener {

    private final ProductCacheService productCacheService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductCacheInvalidation(ProductCacheInvalidationEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        switch (event.type()) {
            case SKU_CHANGED -> productCacheService.invalidateAfterSkuChange(event.spuId(), event.skuId());
            case STOCK_CHANGED -> productCacheService.invalidateAfterStockChange(event.skuId());
            case SPU_CHANGED -> productCacheService.invalidateAfterSpuChange(
                    event.spuId(), event.recommendChanged());
        }
    }
}
