package com.example.mystore.event;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PricingCacheInvalidationListener {

    private final RedisUtil redisUtil;

    @Value("${pricing.cache.lock-wait-ms:500}")
    private long lockWaitMs = 500L;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPricingCacheInvalidation(PricingCacheInvalidationEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        List<String> keys = new ArrayList<>(2);
        if (event.levelId() != null) {
            keys.add(RedisConstants.PREFIX_PRICING_LEVEL + event.levelId());
        }
        if (event.type() == PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED) {
            keys.add(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID);
        }
        if (keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            evict(key);
        }
    }

    private void evict(String key) {
        try {
            if (!redisUtil.evictWithLock(key, lockWaitMs)) {
                log.warn("定价缓存失效等待锁超时, key={}, maxWaitMs={}", key, lockWaitMs);
            }
        } catch (RuntimeException e) {
            log.error("定价缓存失效失败, key={}", key, e);
        }
    }
}
