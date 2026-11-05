package com.example.mystore.event;

import com.example.mystore.common.constant.ProductCacheConstants;
import com.example.mystore.common.exception.ProductCacheInvalidationException;
import com.example.mystore.common.metrics.ProductCacheMetrics;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.service.impl.DingTalkAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCacheInvalidationListener {

    private final ProductCacheService productCacheService;
    private final ProductCacheMetrics metrics;
    private final DingTalkAlertService alertService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductCacheInvalidation(ProductCacheInvalidationEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        try {
            switch (event.type()) {
                case SKU_CHANGED -> runInvalidation(
                        () -> productCacheService.invalidateAfterSkuChange(
                                event.spuId(), event.skuId()),
                        "SKU_CHANGED, spuId=" + event.spuId() + ", skuId=" + event.skuId());
                case SPU_CHANGED -> runInvalidation(
                        () -> productCacheService.invalidateAfterSpuChange(
                                event.spuId(), event.recommendChanged()),
                        "SPU_CHANGED, spuId=" + event.spuId());
            }
        } catch (RuntimeException e) {
            recordUnexpectedFailure("商品缓存失效失败, type=" + event.type()
                    + ", spuId=" + event.spuId() + ", skuId=" + event.skuId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductSkuBatchChanged(ProductSkuBatchChangedEvent event) {
        if (event == null || event.skuIds() == null || event.skuIds().isEmpty()) {
            return;
        }
        try {
            runInvalidation(
                    () -> productCacheService.invalidateAfterSkuBatchChange(
                            event.spuId(), event.skuIds()),
                    "SKU_BATCH_CHANGED, spuId=" + event.spuId()
                            + ", skuIds=" + event.skuIds());
        } catch (RuntimeException e) {
            recordUnexpectedFailure("批量 SKU 缓存失效失败, spuId=" + event.spuId()
                    + ", skuIds=" + event.skuIds(), e);
        }
    }

    private void runInvalidation(Runnable invalidation, String context) {
        try {
            invalidation.run();
            metrics.recordSuccess();
        } catch (ProductCacheInvalidationException e) {
            recordFailure(context, e.failures(), e);
        }
    }

    private void recordFailure(String context,
                               List<ProductCacheInvalidationException.Failure> failures,
                               RuntimeException cause) {
        StringBuilder message = new StringBuilder(context).append(": ");
        for (int i = 0; i < failures.size(); i++) {
            ProductCacheInvalidationException.Failure failure = failures.get(i);
            metrics.recordFailure(failure.cause());
            if (i > 0) {
                message.append("; ");
            }
            message.append("key=").append(failure.key())
                    .append(", cause=").append(failure.cause());
        }
        message.append(", TTL上限=").append(ProductCacheConstants.MAX_STALE_SECONDS).append("秒");
        log.error(message.toString(), cause);
        alertService.alert(ProductCacheConstants.Alert.INVALIDATION_FAILED, message.toString());
    }

    private void recordUnexpectedFailure(String context, RuntimeException cause) {
        metrics.recordFailure(ProductCacheMetrics.CAUSE_REDIS_ERROR);
        String message = context + ", TTL上限=" + ProductCacheConstants.MAX_STALE_SECONDS + "秒";
        log.error(message, cause);
        alertService.alert(ProductCacheConstants.Alert.INVALIDATION_FAILED, message);
    }
}
