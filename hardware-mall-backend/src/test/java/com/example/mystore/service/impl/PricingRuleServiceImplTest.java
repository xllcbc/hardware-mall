package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.PricingCacheConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryRate;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.dto.LevelRateItemRequest;
import com.example.mystore.entity.vo.PricingLevelCache;
import com.example.mystore.entity.dto.PricingRateResolution;
import com.example.mystore.event.PricingCacheInvalidationEvent;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryRateMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingRuleServiceImplTest {

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

    @InjectMocks
    private PricingRuleServiceImpl pricingRuleService;

    @Test
    void retailLevelUsesItsOwnMatrix() {
        stubLevelCache(1L, true, Map.of(9L, new BigDecimal("1.5000")));

        PricingRateResolution result = pricingRuleService.rateFor(1L, 9L, false);

        assertThat(result.levelId()).isEqualTo(1L);
        assertThat(result.isMissing()).isFalse();
        assertThat(result.rate()).isEqualByComparingTo("1.5000");
    }

    @Test
    void customLevelUsesCachedCategoryRate() {
        stubLevelCache(2L, true, Map.of(9L, new BigDecimal("0.7000")));

        PricingRateResolution configured = pricingRuleService.rateFor(2L, 9L, false);
        PricingRateResolution gap = pricingRuleService.rateFor(2L, 10L, false);

        assertThat(configured.levelId()).isEqualTo(2L);
        assertThat(configured.rate()).isEqualByComparingTo("0.7000");
        assertThat(gap.levelId()).isEqualTo(2L);
        assertThat(gap.isMissing()).isTrue();
        assertThat(gap.rate()).isNull();
    }

    @Test
    void explicitOneIsNotAGap() {
        stubLevelCache(2L, true, Map.of(9L, new BigDecimal("1.0000")));

        PricingRateResolution result = pricingRuleService.rateFor(2L, 9L, false);

        assertThat(result.isMissing()).isFalse();
        assertThat(result.rate()).isEqualByComparingTo("1.0000");
    }

    @Test
    void retailGapIsMissingAndNeverFallsBackToOne() {
        stubLevelCache(1L, true, Map.of());

        PricingRateResolution result = pricingRuleService.rateFor(1L, 9L, false);

        assertThat(result.isMissing()).isTrue();
        assertThat(result.rate()).isNull();
    }

    @Test
    void anonymousGuestUsesRetailMatrix() {
        stubRetailLevelId(1L);
        stubLevelCache(1L, true, Map.of(9L, new BigDecimal("1.2000")));

        PricingRateResolution result = pricingRuleService.rateFor(null, 9L, false);

        assertThat(result.levelId()).isEqualTo(1L);
        assertThat(result.isMissing()).isFalse();
        assertThat(result.rate()).isEqualByComparingTo("1.2000");
    }

    @Test
    void nonexistentLevelFallsBackToRetail() {
        stubLoadingLevelCache(88L);
        when(userLevelMapper.selectById(88L)).thenReturn(null);
        stubRetailLevelId(1L);
        stubLevelCache(1L, true, Map.of(9L, new BigDecimal("1.1000")));

        PricingRateResolution result = pricingRuleService.rateFor(88L, 9L, false);

        assertThat(result.levelId()).isEqualTo(1L);
        assertThat(result.rate()).isEqualByComparingTo("1.1000");
    }

    @Test
    void disabledLevelKeepsItsOwnRateOnCacheMiss() {
        UserLevel disabled = customLevel(3L);
        disabled.setStatus(UserLevelStatusConstants.STATUS_DISABLED);
        when(userLevelMapper.selectById(3L)).thenReturn(disabled);
        stubLoadingLevelCache(3L);
        LevelCategoryRate rate = new LevelCategoryRate();
        rate.setLevelId(3L);
        rate.setCategoryId(9L);
        rate.setRate(new BigDecimal("0.6000"));
        when(levelCategoryRateMapper.selectList(any())).thenReturn(List.of(rate));

        PricingRateResolution result = pricingRuleService.rateFor(3L, 9L, false);

        assertThat(result.levelId()).isEqualTo(3L);
        assertThat(result.rate()).isEqualByComparingTo("0.6000");
    }

    @Test
    void excludeDiscountAlwaysUsesRetailRate() {
        stubRetailLevelId(1L);
        stubLevelCache(1L, true, Map.of(9L, new BigDecimal("1.2000")));

        PricingRateResolution result = pricingRuleService.rateFor(2L, 9L, true);

        assertThat(result.levelId()).isEqualTo(1L);
        assertThat(result.isMissing()).isFalse();
        assertThat(result.rate()).isEqualByComparingTo("1.2000");
    }

    @Test
    void excludeDiscountWithRetailGapIsMissing() {
        stubRetailLevelId(1L);
        stubLevelCache(1L, true, Map.of());

        PricingRateResolution result = pricingRuleService.rateFor(2L, 9L, true);

        assertThat(result.isMissing()).isTrue();
        assertThat(result.rate()).isNull();
    }

    @Test
    void legacyCacheWithoutExistenceFlagIsTreatedAsExistingLevel() {
        stubLevelCache(2L, null, Map.of(9L, new BigDecimal("0.9000")));

        PricingRateResolution result = pricingRuleService.rateFor(2L, 9L, false);

        assertThat(result.levelId()).isEqualTo(2L);
        assertThat(result.rate()).isEqualByComparingTo("0.9000");
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
        LevelCategoryRate rate = new LevelCategoryRate();
        rate.setLevelId(2L);
        rate.setCategoryId(9L);
        rate.setRate(new BigDecimal("0.8000"));
        when(levelCategoryRateMapper.selectList(any())).thenReturn(List.of(rate));

        assertThat(pricingRuleService.isComplete(2L)).isTrue();
    }

    @Test
    void customLevelCacheMissLoadsRatesFromDatabase() {
        stubLoadingLevelCache(2L);
        when(userLevelMapper.selectById(2L)).thenReturn(customLevel(2L));
        LevelCategoryRate rate = new LevelCategoryRate();
        rate.setLevelId(2L);
        rate.setCategoryId(9L);
        rate.setRate(new BigDecimal("0.7500"));
        when(levelCategoryRateMapper.selectList(any())).thenReturn(List.of(rate));

        PricingRateResolution result = pricingRuleService.rateFor(2L, 9L, false);

        assertThat(result.rate()).isEqualByComparingTo("0.7500");
        verify(levelCategoryRateMapper).selectList(any());
    }

    @Test
    void saveRates_publishesPricingInvalidationInsteadOfEvictingInTransaction() {
        when(userLevelMapper.selectById(2L)).thenReturn(customLevel(2L));
        Category category = new Category();
        category.setId(9L);
        when(categoryMapper.selectById(9L)).thenReturn(category);
        when(levelCategoryRateMapper.selectOne(any())).thenReturn(null);
        LevelRateItemRequest item = new LevelRateItemRequest();
        item.setCategoryId(9L);
        item.setRate(new BigDecimal("0.8000"));

        pricingRuleService.saveRates(2L, List.of(item));

        verify(eventPublisher).publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));
        verify(redisUtil, never()).delete(anyString());
    }

    @Test
    void fillRates_publishesPricingInvalidationInsteadOfEvictingInTransaction() {
        when(userLevelMapper.selectById(2L)).thenReturn(customLevel(2L));
        Category category = new Category();
        category.setId(9L);
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(levelCategoryRateMapper.selectOne(any())).thenReturn(null);

        pricingRuleService.fillRates(2L, new BigDecimal("0.7000"));

        verify(eventPublisher).publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));
        verify(redisUtil, never()).delete(anyString());
    }

    @Test
    void copyRates_replacesTargetMatrixAndPreservesSourceGap() {
        when(userLevelMapper.selectById(2L)).thenReturn(customLevel(2L));
        when(userLevelMapper.selectById(3L)).thenReturn(customLevel(3L));
        LevelCategoryRate source = new LevelCategoryRate();
        source.setLevelId(3L);
        source.setCategoryId(9L);
        source.setRate(new BigDecimal("0.6000"));
        when(levelCategoryRateMapper.selectList(any())).thenReturn(List.of(source));
        when(levelCategoryRateMapper.delete(any())).thenReturn(1);

        pricingRuleService.copyRates(2L, 3L);

        verify(levelCategoryRateMapper).delete(any());
        ArgumentCaptor<LevelCategoryRate> inserted = ArgumentCaptor.forClass(LevelCategoryRate.class);
        verify(levelCategoryRateMapper).insert(inserted.capture());
        assertThat(inserted.getAllValues())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getLevelId()).isEqualTo(2L);
                    assertThat(row.getCategoryId()).isEqualTo(9L);
                    assertThat(row.getRate()).isEqualByComparingTo("0.6000");
                });
        verify(levelCategoryRateMapper, never()).updateById(any(LevelCategoryRate.class));
        verify(eventPublisher).publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));
        verify(redisUtil, never()).delete(anyString());
    }

    @Test
    void copyRates_rejectsIncompleteSourceForEnabledTarget() {
        UserLevel target = customLevel(2L);
        target.setStatus(UserLevelStatusConstants.STATUS_ENABLED);
        when(userLevelMapper.selectById(2L)).thenReturn(target);
        when(userLevelMapper.selectById(3L)).thenReturn(customLevel(3L));

        Category configuredCategory = new Category();
        configuredCategory.setId(9L);
        Category gapCategory = new Category();
        gapCategory.setId(10L);
        when(categoryMapper.selectList(any())).thenReturn(List.of(configuredCategory, gapCategory));

        LevelCategoryRate source = new LevelCategoryRate();
        source.setLevelId(3L);
        source.setCategoryId(9L);
        source.setRate(new BigDecimal("0.6000"));
        when(levelCategoryRateMapper.selectList(any())).thenReturn(List.of(source));

        assertThatThrownBy(() -> pricingRuleService.copyRates(2L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("来源等级倍率矩阵未填满，不能复制到已启用等级");

        verify(levelCategoryRateMapper, never()).delete(any());
        verify(levelCategoryRateMapper, never()).insert(any(LevelCategoryRate.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void retailLevelAcceptsRatesWithUnifiedCompleteness() {
        UserLevel retail = new UserLevel();
        retail.setId(1L);
        retail.setIsRetail(UserLevelStatusConstants.IS_RETAIL);
        when(userLevelMapper.selectById(1L)).thenReturn(retail);
        Category category = new Category();
        category.setId(9L);
        when(categoryMapper.selectById(9L)).thenReturn(category);
        when(levelCategoryRateMapper.selectOne(any())).thenReturn(null);
        LevelRateItemRequest item = new LevelRateItemRequest();
        item.setCategoryId(9L);
        item.setRate(new BigDecimal("3.0000"));

        pricingRuleService.saveRates(1L, List.of(item));

        ArgumentCaptor<LevelCategoryRate> inserted = ArgumentCaptor.forClass(LevelCategoryRate.class);
        verify(levelCategoryRateMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getRate()).isEqualByComparingTo("3.0000");
        verify(eventPublisher).publishEvent(new PricingCacheInvalidationEvent(
                PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 1L));
    }

    @Test
    void retailLevelCompletenessFollowsUnifiedRule() {
        UserLevel retail = new UserLevel();
        retail.setId(1L);
        retail.setIsRetail(UserLevelStatusConstants.IS_RETAIL);
        when(userLevelMapper.selectById(1L)).thenReturn(retail);
        Category category = new Category();
        category.setId(9L);
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(levelCategoryRateMapper.selectList(any())).thenReturn(List.of());

        assertThat(pricingRuleService.isComplete(1L)).isFalse();
    }

    @Test
    void rateAboveUpperBoundIsRejected() {
        UserLevel retail = new UserLevel();
        retail.setId(1L);
        retail.setIsRetail(UserLevelStatusConstants.IS_RETAIL);
        when(userLevelMapper.selectById(1L)).thenReturn(retail);
        Category category = new Category();
        category.setId(9L);
        when(categoryMapper.selectById(9L)).thenReturn(category);
        LevelRateItemRequest item = new LevelRateItemRequest();
        item.setCategoryId(9L);
        item.setRate(new BigDecimal("3.0001"));

        assertThatThrownBy(() -> pricingRuleService.saveRates(1L, List.of(item)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("倍率必须在 (0, 3] 之间");
        verify(levelCategoryRateMapper, never()).insert(any(LevelCategoryRate.class));
    }

    @Test
    void retailLevelFillAndCopyRatesSucceed() {
        UserLevel retail = new UserLevel();
        retail.setId(1L);
        retail.setIsRetail(UserLevelStatusConstants.IS_RETAIL);
        when(userLevelMapper.selectById(1L)).thenReturn(retail);
        Category category = new Category();
        category.setId(9L);
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        when(levelCategoryRateMapper.selectOne(any())).thenReturn(null);

        pricingRuleService.fillRates(1L, new BigDecimal("2.5000"));

        ArgumentCaptor<LevelCategoryRate> inserted = ArgumentCaptor.forClass(LevelCategoryRate.class);
        verify(levelCategoryRateMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getRate()).isEqualByComparingTo("2.5000");

        reset(levelCategoryRateMapper, eventPublisher);
        when(userLevelMapper.selectById(2L)).thenReturn(customLevel(2L));
        LevelCategoryRate source = new LevelCategoryRate();
        source.setLevelId(1L);
        source.setCategoryId(9L);
        source.setRate(new BigDecimal("2.5000"));
        when(levelCategoryRateMapper.selectList(any())).thenReturn(List.of(source));
        when(levelCategoryRateMapper.delete(any())).thenReturn(1);

        pricingRuleService.copyRates(2L, 1L);

        verify(levelCategoryRateMapper).delete(any());
        ArgumentCaptor<LevelCategoryRate> copied = ArgumentCaptor.forClass(LevelCategoryRate.class);
        verify(levelCategoryRateMapper).insert(copied.capture());
        assertThat(copied.getValue().getLevelId()).isEqualTo(2L);
        assertThat(copied.getValue().getRate()).isEqualByComparingTo("2.5000");
    }

    private void stubRetailLevelId(Long retailId) {
        when(redisUtil.queryWithCache(
                eq(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID),
                eq(Long.class),
                eq(PricingCacheConstants.TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(PricingCacheConstants.JITTER_SECONDS),
                eq(500L),
                any())).thenReturn(retailId);
    }

    private void stubLoadingLevelCache(Long levelId) {
        when(redisUtil.queryWithCache(
                eq(RedisConstants.PREFIX_PRICING_LEVEL + levelId),
                eq(PricingLevelCache.class),
                eq(PricingCacheConstants.TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(PricingCacheConstants.JITTER_SECONDS),
                eq(500L),
                any()))
                .thenAnswer(invocation -> {
                    Supplier<PricingLevelCache> loader = invocation.getArgument(6);
                    return loader.get();
                });
    }

    private void stubLevelCache(Long levelId, Boolean levelExists, Map<Long, BigDecimal> rates) {
        PricingLevelCache cache = new PricingLevelCache();
        cache.setLevelExists(levelExists);
        cache.setRates(new HashMap<>(rates));
        when(redisUtil.queryWithCache(
                eq(RedisConstants.PREFIX_PRICING_LEVEL + levelId),
                eq(PricingLevelCache.class),
                eq(PricingCacheConstants.TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(PricingCacheConstants.JITTER_SECONDS),
                eq(500L),
                any())).thenReturn(cache);
    }

    private UserLevel customLevel(Long id) {
        UserLevel level = new UserLevel();
        level.setId(id);
        level.setIsRetail(0);
        return level;
    }
}
