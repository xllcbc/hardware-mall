package com.example.mystore.job.stock;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.service.SkuService;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCacheEvictRetryJobTest {

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private SkuService skuService;

    @InjectMocks
    private StockCacheEvictRetryJob job;

    @Test
    void retry_success_removesSkuFromFailedSet() {
        when(redisUtil.sMembers(RedisConstants.PREFIX_STOCK_EVICT_FAILED, Object.class))
                .thenReturn(Set.of(5L));

        job.retryFailedEvict();

        verify(skuService).evictStockCache(5L);
        verify(redisUtil).sRemove(RedisConstants.PREFIX_STOCK_EVICT_FAILED, 5L);
    }

    @Test
    void retry_failure_keepsSkuInFailedSet() {
        when(redisUtil.sMembers(RedisConstants.PREFIX_STOCK_EVICT_FAILED, Object.class))
                .thenReturn(Set.of(5L));
        doThrow(new RuntimeException("redis unavailable"))
                .when(skuService)
                .evictStockCache(5L);

        job.retryFailedEvict();

        verify(redisUtil, never()).sRemove(RedisConstants.PREFIX_STOCK_EVICT_FAILED, 5L);
    }
}
