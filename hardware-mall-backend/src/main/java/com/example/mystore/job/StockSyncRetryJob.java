package com.example.mystore.job;

import com.example.mystore.service.SkuService;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 库存同步补偿定时任务
 * 处理因 Redis 临时不可用导致的库存缓存同步失败
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockSyncRetryJob {

    private final RedisUtil redisUtil;
    private final SkuService skuService;

    private static final String FAILED_KEY = "stock:sync:failed";

    /**
     * 每分钟执行一次，重试失败的库存同步
     */
    @Scheduled(fixedDelay = 60_000)
    public void retryFailedSync() {
        Set<Object> failedIds = redisUtil.sMembers(FAILED_KEY, Object.class);
        if (failedIds == null || failedIds.isEmpty()) {
            return;
        }

        log.info("发现 {} 条库存同步失败记录，开始补偿", failedIds.size());

        int successCount = 0;
        for (Object idObj : failedIds) {
            Long skuId = Long.valueOf(idObj.toString());
            try {
                if (skuService.syncStockToCache(skuId)) {
                    redisUtil.sRemove(FAILED_KEY, idObj);
                    successCount++;
                }
            } catch (Exception e) {
                log.error("库存同步补偿异常, skuId={}", skuId, e);
            }
        }

        int remaining = failedIds.size() - successCount;
        if (remaining > 0) {
            log.warn("库存同步补偿完成, 成功={}, 剩余={}", successCount, remaining);
        } else {
            log.info("库存同步补偿完成, 全部成功");
        }
    }
}
