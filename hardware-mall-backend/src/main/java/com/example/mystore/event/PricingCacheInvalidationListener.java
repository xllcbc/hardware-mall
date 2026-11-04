package com.example.mystore.event;

import com.example.mystore.common.constant.PricingCacheConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.metrics.PricingCacheMetrics;
import com.example.mystore.service.impl.DingTalkAlertService;
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
    private final PricingCacheMetrics metrics;
    private final DingTalkAlertService alertService;

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

        List<FailedEviction> failures = new ArrayList<>(2);
        for (String key : keys) {
            String cause = evict(key);
            if (cause == null) {
                continue;
            }
            failures.add(new FailedEviction(key, cause));
        }

        if (failures.isEmpty()) {
            metrics.recordSuccess();
        } else {
            recordFailure(failures);
        }
    }

    private String evict(String key) {
        try {
            if (redisUtil.evictWithLock(key, lockWaitMs)) {
                return null;
            }
            return PricingCacheMetrics.CAUSE_LOCK_TIMEOUT;
        } catch (RuntimeException e) {
            log.error("定价缓存失效失败, key={}", key, e);
            return PricingCacheMetrics.CAUSE_REDIS_ERROR;
        }
    }

    private void recordFailure(List<FailedEviction> failures) {
        StringBuilder message = new StringBuilder("定价缓存失效失败: ");
        for (int i = 0; i < failures.size(); i++) {
            FailedEviction failure = failures.get(i);
            metrics.recordFailure(failure.cause());
            if (i > 0) {
                message.append("; ");
            }
            message.append("key=").append(failure.key())
                    .append(", cause=").append(failure.cause());
        }
        message.append(", TTL上限=").append(PricingCacheConstants.MAX_STALE_SECONDS).append("秒");
        alertService.alert(PricingCacheConstants.Alert.INVALIDATION_FAILED,
                message.toString());
    }

    private record FailedEviction(String key, String cause) {
    }
}
