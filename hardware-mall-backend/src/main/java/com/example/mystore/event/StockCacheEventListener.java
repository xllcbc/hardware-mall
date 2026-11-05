package com.example.mystore.event;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.service.SkuService;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockCacheEventListener {

    private final SkuService skuService;
    private final RedisUtil redisUtil;

    @Async("stockCacheExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStockCacheEvent(StockCacheEvent event) {
        if (event == null || event.skuIds() == null) {
            return;
        }
        for (Long skuId : event.skuIds()) {
            if (skuId == null) {
                continue;
            }
            try {
                skuService.evictStockCache(skuId);
            } catch (RuntimeException e) {
                redisUtil.sAdd(RedisConstants.PREFIX_STOCK_EVICT_FAILED, skuId);
                log.error("库存缓存清除失败, skuId={}, 已加入补偿队列", skuId, e);
            }
        }
    }
}
