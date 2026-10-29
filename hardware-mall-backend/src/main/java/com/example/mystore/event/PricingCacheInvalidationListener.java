package com.example.mystore.event;

import com.example.mystore.service.PricingRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PricingCacheInvalidationListener {

    private final PricingRuleService pricingRuleService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPricingCacheInvalidation(PricingCacheInvalidationEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        try {
            switch (event.type()) {
                case MATRIX_CHANGED -> pricingRuleService.evictLevelCache(event.levelId());
                case LEVEL_AVAILABILITY_CHANGED -> {
                    pricingRuleService.evictLevelCache(event.levelId());
                    pricingRuleService.evictRetailLevelIdCache();
                }
            }
        } catch (RuntimeException e) {
            log.error("定价缓存失效失败, type={}, levelId={}", event.type(), event.levelId(), e);
        }
    }
}
