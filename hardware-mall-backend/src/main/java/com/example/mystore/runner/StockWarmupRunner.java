package com.example.mystore.runner;

import com.example.mystore.service.SkuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时触发一次库存预热；预热规则（只写正库存、TTL 与写入路径）由 SKU 服务承担。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockWarmupRunner implements CommandLineRunner {

    private final SkuService skuService;

    @Override
    public void run(String... args) {
        log.info("开始库存预热...");
        int count = skuService.warmUpStockCache();
        log.info("库存预热完成，共加载 {} 个SKU库存到Redis", count);
    }
}
