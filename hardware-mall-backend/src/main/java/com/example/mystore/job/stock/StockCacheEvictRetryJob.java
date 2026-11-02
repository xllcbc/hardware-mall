package com.example.mystore.job.stock;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.service.SkuService;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 库存缓存清除补偿任务。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockCacheEvictRetryJob {

    private final RedisUtil redisUtil;
    private final SkuService skuService;

    @Scheduled(fixedDelay = 60_000)
    public void retryFailedEvict() {
        Set<Object> failedIds = redisUtil.sMembers(RedisConstants.PREFIX_STOCK_EVICT_FAILED, Object.class);
        if (failedIds == null || failedIds.isEmpty()) {
            return;
        }

        log.info("发现 {} 条库存缓存清除失败记录，开始补偿", failedIds.size());

        int successCount = 0;
        for (Object idObj : failedIds) {
            Long skuId = Long.valueOf(idObj.toString());
            try {
                skuService.evictStockCache(skuId);
                redisUtil.sRemove(RedisConstants.PREFIX_STOCK_EVICT_FAILED, idObj);
                successCount++;
            } catch (Exception e) {
                log.error("库存缓存清除补偿异常, skuId={}", skuId, e);
            }
        }

        int remaining = failedIds.size() - successCount;
        if (remaining > 0) {
            log.warn("库存缓存清除补偿完成, 成功={}, 剩余={}", successCount, remaining);
        } else {
            log.info("库存缓存清除补偿完成, 全部成功");
        }
    }
}
