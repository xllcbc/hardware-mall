package com.example.mystore.event;

import com.example.mystore.service.ProductCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCacheInvalidationListener {

    private final ProductCacheService productCacheService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductCacheInvalidation(ProductCacheInvalidationEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        try {
            switch (event.type()) {
                case SKU_CHANGED -> productCacheService.invalidateAfterSkuChange(event.spuId(), event.skuId());
                case SPU_CHANGED -> productCacheService.invalidateAfterSpuChange(
                        event.spuId(), event.recommendChanged());
            }
        } catch (RuntimeException e) {
            log.error("商品缓存失效失败, type={}, spuId={}, skuId={}",
                    event.type(), event.spuId(), event.skuId(), e);
        }
    }
}
