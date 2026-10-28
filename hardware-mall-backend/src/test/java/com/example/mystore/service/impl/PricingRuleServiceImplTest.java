package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryDiscount;
import com.example.mystore.entity.vo.PricingLevelCache;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryDiscountMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingRuleServiceImplTest {

    @Mock
    private UserLevelMapper userLevelMapper;
    @Mock
    private LevelCategoryDiscountMapper levelCategoryDiscountMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private PricingRuleServiceImpl pricingRuleService;

    @Test
    void retailLevelShortCircuitsToOne() {
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID),
                eq(Long.class), eq(RedisConstants.CACHE_TTL_HOUR), any()))
                .thenReturn(1L);

        BigDecimal rate = pricingRuleService.rateFor(1L, 9L, false);

        assertThat(rate).isEqualByComparingTo("1.0000");
        verifyNoInteractions(levelCategoryDiscountMapper);
    }

    @Test
    void customLevelUsesCachedCategoryRate() {
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID),
                eq(Long.class), eq(RedisConstants.CACHE_TTL_HOUR), any()))
                .thenReturn(1L);
        PricingLevelCache cache = new PricingLevelCache();
        cache.setRates(Map.of(9L, new BigDecimal("0.7000")));
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_PRICING_LEVEL + 2L),
                eq(PricingLevelCache.class), eq(RedisConstants.CACHE_TTL_HOUR), any()))
                .thenReturn(cache);

        assertThat(pricingRuleService.rateFor(2L, 9L, false))
                .isEqualByComparingTo("0.7000");
        assertThat(pricingRuleService.rateFor(2L, 10L, false))
                .isEqualByComparingTo("1.0000");
    }

    @Test
    void excludeDiscountAlwaysUsesRetailRate() {
        assertThat(pricingRuleService.rateFor(2L, 9L, true))
                .isEqualByComparingTo("1.0000");
        verifyNoInteractions(redisUtil);
    }

    @Test
    void completeMatrixRequiresEveryCategoryRow() {
        com.example.mystore.entity.db.UserLevel level = new com.example.mystore.entity.db.UserLevel();
        level.setId(2L);
        level.setIsRetail(0);
        when(userLevelMapper.selectById(2L)).thenReturn(level);
        Category category = new Category();
        category.setId(9L);
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        LevelCategoryDiscount discount = new LevelCategoryDiscount();
        discount.setLevelId(2L);
        discount.setCategoryId(9L);
        discount.setRate(new BigDecimal("0.8000"));
        when(levelCategoryDiscountMapper.selectList(any())).thenReturn(List.of(discount));

        assertThat(pricingRuleService.isComplete(2L)).isTrue();
    }
}
