package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryDiscount;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.dto.LevelDiscountItemRequest;
import com.example.mystore.entity.vo.PricingLevelCache;
import com.example.mystore.event.PricingCacheInvalidationEvent;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryDiscountMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        UserLevel level = new UserLevel();
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

    @Test
    void customLevelCacheMissLoadsRatesFromDatabase() {
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID),
                eq(Long.class), eq(RedisConstants.CACHE_TTL_HOUR), any()))
                .thenReturn(1L);
        when(redisUtil.queryWithCache(eq(RedisConstants.PREFIX_PRICING_LEVEL + 2L),
                eq(PricingLevelCache.class), eq(RedisConstants.CACHE_TTL_HOUR), any()))
                .thenAnswer(invocation -> {
                    Supplier<PricingLevelCache> loader = invocation.getArgument(3);
                    return loader.get();
                });
        LevelCategoryDiscount discount = new LevelCategoryDiscount();
        discount.setLevelId(2L);
        discount.setCategoryId(9L);
        discount.setRate(new BigDecimal("0.7500"));
        when(levelCategoryDiscountMapper.selectList(any())).thenReturn(List.of(discount));

        assertThat(pricingRuleService.rateFor(2L, 9L, false))
                .isEqualByComparingTo("0.7500");
        verify(levelCategoryDiscountMapper).selectList(any());
    }

    @Test
    void saveDiscounts_publishesPricingInvalidationInsteadOfEvictingInTransaction() {
        when(userLevelMapper.selectById(2L)).thenReturn(customLevel(2L));
        Category category = new Category();
        category.setId(9L);
        when(categoryMapper.selectById(9L)).thenReturn(category);
        when(levelCategoryDiscountMapper.selectOne(any())).thenReturn(null);
        LevelDiscountItemRequest item = new LevelDiscountItemRequest();
        item.setCategoryId(9L);
        item.setRate(new BigDecimal("0.8000"));

        pricingRuleService.saveDiscounts(2L, List.of(item));

        verify(eventPublisher).publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));
        verify(redisUtil, never()).delete(anyString());
    }

    @Test
    void fillDiscounts_publishesPricingInvalidationInsteadOfEvictingInTransaction() {
        when(userLevelMapper.selectById(2L)).thenReturn(customLevel(2L));
        Category category = new Category();
        category.setId(9L);
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(levelCategoryDiscountMapper.selectOne(any())).thenReturn(null);

        pricingRuleService.fillDiscounts(2L, new BigDecimal("0.7000"));

        verify(eventPublisher).publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));
        verify(redisUtil, never()).delete(anyString());
    }

    @Test
    void copyDiscounts_replacesTargetMatrixAndPreservesSourceGap() {
        when(userLevelMapper.selectById(2L)).thenReturn(customLevel(2L));
        when(userLevelMapper.selectById(3L)).thenReturn(customLevel(3L));
        LevelCategoryDiscount source = new LevelCategoryDiscount();
        source.setLevelId(3L);
        source.setCategoryId(9L);
        source.setRate(new BigDecimal("0.6000"));
        when(levelCategoryDiscountMapper.selectList(any())).thenReturn(List.of(source));
        when(levelCategoryDiscountMapper.delete(any())).thenReturn(1);

        pricingRuleService.copyDiscounts(2L, 3L);

        verify(levelCategoryDiscountMapper).delete(any());
        ArgumentCaptor<LevelCategoryDiscount> inserted = ArgumentCaptor.forClass(LevelCategoryDiscount.class);
        verify(levelCategoryDiscountMapper).insert(inserted.capture());
        assertThat(inserted.getAllValues())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getLevelId()).isEqualTo(2L);
                    assertThat(row.getCategoryId()).isEqualTo(9L);
                    assertThat(row.getRate()).isEqualByComparingTo("0.6000");
                });
        verify(levelCategoryDiscountMapper, never()).updateById(any(LevelCategoryDiscount.class));
        verify(eventPublisher).publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));
        verify(redisUtil, never()).delete(anyString());
    }

    private UserLevel customLevel(Long id) {
        UserLevel level = new UserLevel();
        level.setId(id);
        level.setIsRetail(0);
        return level;
    }
}
