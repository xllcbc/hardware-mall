package com.example.mystore.event;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.service.SkuService;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockCacheEventListenerTest {

    @Mock
    private SkuService skuService;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private StockCacheEventListener listener;

    @Test
    void evict_removesStockKeyThroughSkuService() {
        listener.onStockCacheEvent(new StockCacheEvent(List.of(5L)));

        verify(skuService).evictStockCache(5L);
    }

    @Test
    void evictFailure_entersCompensationSet() {
        doThrow(new RuntimeException("redis unavailable"))
                .when(skuService)
                .evictStockCache(5L);

        assertThatCode(() -> listener.onStockCacheEvent(new StockCacheEvent(List.of(5L))))
                .doesNotThrowAnyException();

        verify(redisUtil).sAdd(RedisConstants.PREFIX_STOCK_EVICT_FAILED, 5L);
    }
}
