package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.dto.LevelPricing;
import com.example.mystore.entity.dto.ProductPricingFacts;
import com.example.mystore.entity.vo.PricingLevelCache;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryRateMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.service.LevelPricingService;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 等级定价模块与真实倍率解析适配器接线后的回退契约：匿名与失效等级按零售等级取价。
 * 仅替换 Redis 缓存适配器，不依赖真实缓存或数据库。
 */
@ExtendWith(MockitoExtension.class)
class LevelPricingServiceFallbackContractTest {

    private static final long RETAIL_LEVEL_ID = 1L;
    private static final long INVALID_LEVEL_ID = 88L;
    private static final long CATEGORY_ID = 9L;

    @Mock
    private UserLevelMapper userLevelMapper;
    @Mock
    private LevelCategoryRateMapper levelCategoryRateMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LevelPricingService levelPricingService;

    @BeforeEach
    void setUp() {
        PricingRuleServiceImpl pricingRuleService = new PricingRuleServiceImpl(
                userLevelMapper,
                levelCategoryRateMapper,
                categoryMapper,
                redisUtil,
                eventPublisher);
        levelPricingService = new LevelPricingServiceImpl(
                pricingRuleService, new PriceServiceImpl());

        when(redisUtil.queryWithCache(
                anyString(), any(), anyLong(), anyLong(), anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    if (RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID.equals(key)) {
                        return RETAIL_LEVEL_ID;
                    }
                    if (key.equals(RedisConstants.PREFIX_PRICING_LEVEL + INVALID_LEVEL_ID)) {
                        return levelCache(false, Map.of());
                    }
                    return levelCache(true, Map.of(CATEGORY_ID, new BigDecimal("1.2000")));
                });
    }

    @Test
    void anonymousShopperUsesRetailLevelRate() {
        LevelPricing pricing = levelPricingService.price(
                null,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));

        assertThat(pricing.levelId()).isEqualTo(RETAIL_LEVEL_ID);
        assertThat(pricing.rate()).isEqualByComparingTo("1.2000");
        assertThat(pricing.sellable()).isTrue();
        assertThat(pricing.minPrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void nonexistentLevelFallsBackToRetailLevelRate() {
        LevelPricing pricing = levelPricingService.price(
                INVALID_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));

        assertThat(pricing.levelId()).isEqualTo(RETAIL_LEVEL_ID);
        assertThat(pricing.rate()).isEqualByComparingTo("1.2000");
        assertThat(pricing.minPrice()).isEqualByComparingTo("30.00");
    }

    private PricingLevelCache levelCache(boolean levelExists, Map<Long, BigDecimal> rates) {
        PricingLevelCache cache = new PricingLevelCache();
        cache.setLevelExists(levelExists);
        cache.setRates(new HashMap<>(rates));
        return cache;
    }
}
