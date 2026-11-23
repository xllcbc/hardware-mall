package com.example.mystore.runner;

import com.example.mystore.service.SkuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 启动器只负责在启动时触发库存预热，预热规则由 SKU 服务承担。
 */
@ExtendWith(MockitoExtension.class)
class StockWarmupRunnerTest {

    @Mock
    private SkuService skuService;

    @InjectMocks
    private StockWarmupRunner stockWarmupRunner;

    @Test
    void run_triggersStockWarmupThroughSkuService() {
        when(skuService.warmUpStockCache()).thenReturn(3);

        stockWarmupRunner.run();

        verify(skuService).warmUpStockCache();
    }
}
