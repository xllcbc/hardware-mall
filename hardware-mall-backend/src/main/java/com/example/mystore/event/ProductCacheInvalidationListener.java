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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductSkuBatchChanged(ProductSkuBatchChangedEvent event) {
        if (event == null || event.skuIds().isEmpty()) {
            return;
        }
        try {
            productCacheService.invalidateAfterSkuBatchChange(event.spuId(), event.skuIds());
        } catch (RuntimeException e) {
            log.error("批量 SKU 缓存失效失败, spuId={}, skuIds={}",
                    event.spuId(), event.skuIds(), e);
        }
    }
}
