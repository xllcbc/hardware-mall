package com.example.mystore.event;

import com.example.mystore.service.SkuService;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 库存同步事件监听器
 * 确保在数据库事务成功提交后，才将最新库存同步到 Redis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSyncEventListener {

    private final SkuService skuService;
    private final RedisUtil redisUtil;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockSync(StockSyncEvent event) {
        for (Long skuId : event.getSkuIds()) {
            boolean success = skuService.syncStockToCache(skuId);
            if (!success) {
                redisUtil.sAdd("stock:sync:failed", skuId);
                log.warn("库存同步未成功, skuId={}, 已加入补偿重试队列", skuId);
            }
        }
    }
}
