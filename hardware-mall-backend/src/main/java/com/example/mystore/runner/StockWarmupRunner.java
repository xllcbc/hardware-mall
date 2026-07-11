package com.example.mystore.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockWarmupRunner implements CommandLineRunner {

    private final SkuMapper skuMapper;
    private final RedisUtil redisUtil;

    @Override
    public void run(String... args) {
        log.info("开始库存预热...");
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sku::getDeleteTime, 0).eq(Sku::getStatus, 1);
        var skus = skuMapper.selectList(wrapper);
        int count = 0;
        for (Sku sku : skus) {
            if (sku.getStock() != null && sku.getStock() > 0) {
                redisUtil.set(RedisConstants.PREFIX_SKU_STOCK + sku.getId(), sku.getStock());
                count++;
            }
        }
        log.info("库存预热完成，共加载 {} 个SKU库存到Redis", count);
    }
}