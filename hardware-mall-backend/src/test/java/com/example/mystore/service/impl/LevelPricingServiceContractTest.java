package com.example.mystore.service.impl;

import com.example.mystore.entity.dto.LevelPricing;
import com.example.mystore.entity.dto.LevelRateTable;
import com.example.mystore.entity.dto.ProductPricingFacts;
import com.example.mystore.service.LevelPricingService;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 等级定价模块契约测试：在模块接口上验证「等级结算价」「倍率缺口」与可售性，
 * 使用假的倍率解析适配器，不搭建线程上下文。
 */
@ExtendWith(MockitoExtension.class)
class LevelPricingServiceContractTest {

    private static final long CUSTOM_LEVEL_ID = 2L;
    private static final long RETAIL_LEVEL_ID = 1L;
    private static final long CATEGORY_ID = 9L;

    @Mock
    private PricingRuleService pricingRuleService;

    private LevelPricingService levelPricingService;

    @BeforeEach
    void setUp() {
        levelPricingService = new LevelPricingServiceImpl(pricingRuleService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void configuredRateProducesLevelSettlementPrice() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("0.8000")));

        LevelPricing pricing = levelPricingService.price(
                CUSTOM_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));

        assertThat(pricing.levelId()).isEqualTo(CUSTOM_LEVEL_ID);
        assertThat(pricing.rate()).isEqualByComparingTo("0.8000");
        assertThat(pricing.sellable()).isTrue();
        assertThat(pricing.minPrice()).isEqualByComparingTo("20.00");
        assertThat(pricing.maxPrice()).isEqualByComparingTo("20.00");
    }

    @Test
    void rateGapIsNotSellableAndHasNoLevelSettlementPrice() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of());

        LevelPricing pricing = levelPricingService.price(
                CUSTOM_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));

        assertThat(pricing.levelId()).isEqualTo(CUSTOM_LEVEL_ID);
        assertThat(pricing.rate()).isNull();
        assertThat(pricing.sellable()).isFalse();
        assertThat(pricing.minPrice()).isNull();
        assertThat(pricing.maxPrice()).isNull();
    }

    @Test
    void explicitOneIsAValidRateRatherThanAGap() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.0000")));

        LevelPricing pricing = levelPricingService.price(
                CUSTOM_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));

        assertThat(pricing.sellable()).isTrue();
        assertThat(pricing.rate()).isEqualByComparingTo("1.0000");
        assertThat(pricing.minPrice()).isEqualByComparingTo("25.00");
    }

    @Test
    void roundsHalfUpToTwoDecimalsAtBoundaries() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(
                CATEGORY_ID, new BigDecimal("0.8004"),
                10L, new BigDecimal("0.8005"),
                11L, new BigDecimal("0.8006")));

        assertThat(priceFor(CATEGORY_ID, "10.00")).isEqualByComparingTo("8.00");
        assertThat(priceFor(10L, "10.00")).isEqualByComparingTo("8.01");
        assertThat(priceFor(11L, "10.00")).isEqualByComparingTo("8.01");
    }

    @Test
    void acceptsRateBoundsZeroPointZeroZeroZeroOneAndThree() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(
                CATEGORY_ID, new BigDecimal("0.0001"),
                10L, new BigDecimal("3.0000")));

        LevelPricing smallest = levelPricingService.price(
                CUSTOM_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("123.45"), false));
        LevelPricing largest = levelPricingService.price(
                CUSTOM_LEVEL_ID,
                ProductPricingFacts.of(10L, new BigDecimal("10.00"), false));

        assertThat(smallest.rate()).isEqualByComparingTo("0.0001");
        assertThat(smallest.minPrice()).isEqualByComparingTo("0.01");
        assertThat(largest.rate()).isEqualByComparingTo("3.0000");
        assertThat(largest.minPrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void roundsBasePriceThirdDecimalHalfUp() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.0000")));

        assertThat(priceFor(CATEGORY_ID, "8.115")).isEqualByComparingTo("8.12");
        assertThat(priceFor(CATEGORY_ID, "8.114")).isEqualByComparingTo("8.11");
    }

    @Test
    void negativeBasePriceIsRejected() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.0000")));

        assertThatThrownBy(() -> levelPricingService.price(
                CUSTOM_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("-1.00"), false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void priceRangeUsesBaseRangeFacts() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("0.5000")));

        LevelPricing pricing = levelPricingService.price(
                CUSTOM_LEVEL_ID,
                ProductPricingFacts.range(
                        CATEGORY_ID, new BigDecimal("25.00"), new BigDecimal("30.00"), false));

        assertThat(pricing.minPrice()).isEqualByComparingTo("12.50");
        assertThat(pricing.maxPrice()).isEqualByComparingTo("15.00");
    }

    @Test
    void usesExplicitLevelIdentityInsteadOfThreadContext() {
        UserContext.setLevelId(999L);
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("0.8000")));

        LevelPricing pricing = levelPricingService.price(
                CUSTOM_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));

        assertThat(pricing.levelId()).isEqualTo(CUSTOM_LEVEL_ID);
        verify(pricingRuleService).rateTable(CUSTOM_LEVEL_ID);
        verify(pricingRuleService, never()).rateTable(999L);
    }

    @Test
    void wholesaleBasePriceIsMaskedOnGapAndKeptWhenSellable() {
        LevelPricing gap = LevelPricing.missing(CUSTOM_LEVEL_ID);
        LevelPricing sellable = LevelPricing.priced(
                CUSTOM_LEVEL_ID,
                new BigDecimal("0.8000"),
                new BigDecimal("20.00"),
                new BigDecimal("20.00"));

        assertThat(gap.maskWholesaleBasePrice(new BigDecimal("25.00"))).isNull();
        assertThat(sellable.maskWholesaleBasePrice(new BigDecimal("25.00")))
                .isEqualByComparingTo("25.00");
        assertThat(sellable.maskWholesaleBasePrice(null)).isNull();
    }

    @Test
    void customerResultTypeCarriesNoWholesaleBasePrice() {
        assertThat(Arrays.stream(LevelPricing.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly(
                        "levelId",
                        "rate",
                        "sellable",
                        "minPrice",
                        "maxPrice")
                .noneMatch(name -> name.toLowerCase().contains("base")
                        || name.toLowerCase().contains("wholesale")
                        || name.toLowerCase().contains("original"));
    }

    @Test
    void excludeDiscountUsesRetailLevelRate() {
        when(pricingRuleService.resolveRetailLevelId()).thenReturn(RETAIL_LEVEL_ID);
        when(pricingRuleService.rateTable(RETAIL_LEVEL_ID))
                .thenReturn(new LevelRateTable(
                        RETAIL_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.2000"))));

        LevelPricing pricing = levelPricingService.price(
                CUSTOM_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), true));

        assertThat(pricing.levelId()).isEqualTo(RETAIL_LEVEL_ID);
        assertThat(pricing.rate()).isEqualByComparingTo("1.2000");
        assertThat(pricing.minPrice()).isEqualByComparingTo("30.00");
        verify(pricingRuleService, never()).rateTable(CUSTOM_LEVEL_ID);
    }

    @Test
    void anonymousShopperUsesRetailLevelRateTable() {
        when(pricingRuleService.rateTable(null))
                .thenReturn(new LevelRateTable(
                        RETAIL_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.1000"))));

        LevelPricing pricing = levelPricingService.price(
                null,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));

        assertThat(pricing.levelId()).isEqualTo(RETAIL_LEVEL_ID);
        assertThat(pricing.minPrice()).isEqualByComparingTo("27.50");
        assertThat(pricing.sellable()).isTrue();
    }

    @Test
    void invalidLevelFallsBackToRetailLevelRateTable() {
        when(pricingRuleService.rateTable(88L))
                .thenReturn(new LevelRateTable(
                        RETAIL_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.1000"))));

        LevelPricing pricing = levelPricingService.price(
                88L,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));

        assertThat(pricing.levelId()).isEqualTo(RETAIL_LEVEL_ID);
        assertThat(pricing.rate()).isEqualByComparingTo("1.1000");
    }

    @Test
    void batchEntryLoadsRateTableOnceAndMatchesByCategory() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(
                CATEGORY_ID, new BigDecimal("0.8000"),
                10L, new BigDecimal("1.0000")));

        List<LevelPricing> pricings = levelPricingService.priceAll(
                CUSTOM_LEVEL_ID,
                List.of(
                        ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false),
                        ProductPricingFacts.of(10L, new BigDecimal("30.00"), false),
                        ProductPricingFacts.of(11L, new BigDecimal("40.00"), false),
                        ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("10.00"), false)));

        assertThat(pricings).hasSize(4);
        assertThat(pricings.get(0).minPrice()).isEqualByComparingTo("20.00");
        assertThat(pricings.get(1).minPrice()).isEqualByComparingTo("30.00");
        assertThat(pricings.get(1).sellable()).isTrue();
        assertThat(pricings.get(2).sellable()).isFalse();
        assertThat(pricings.get(2).minPrice()).isNull();
        assertThat(pricings.get(3).minPrice()).isEqualByComparingTo("8.00");
        verify(pricingRuleService, times(1)).rateTable(CUSTOM_LEVEL_ID);
    }

    @Test
    void batchEntryLoadsEachTableOnceWhenMixingExcludedAndNormalProducts() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("0.8000")));
        when(pricingRuleService.resolveRetailLevelId()).thenReturn(RETAIL_LEVEL_ID);
        when(pricingRuleService.rateTable(RETAIL_LEVEL_ID))
                .thenReturn(new LevelRateTable(
                        RETAIL_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.2000"))));

        List<LevelPricing> pricings = levelPricingService.priceAll(
                CUSTOM_LEVEL_ID,
                List.of(
                        ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false),
                        ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), true),
                        ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("10.00"), false)));

        assertThat(pricings).hasSize(3);
        assertThat(pricings.get(0).levelId()).isEqualTo(CUSTOM_LEVEL_ID);
        assertThat(pricings.get(0).minPrice()).isEqualByComparingTo("20.00");
        assertThat(pricings.get(1).levelId()).isEqualTo(RETAIL_LEVEL_ID);
        assertThat(pricings.get(1).minPrice()).isEqualByComparingTo("30.00");
        assertThat(pricings.get(2).minPrice()).isEqualByComparingTo("8.00");
        verify(pricingRuleService, times(1)).rateTable(CUSTOM_LEVEL_ID);
        verify(pricingRuleService, times(1)).rateTable(RETAIL_LEVEL_ID);
    }

    @Test
    void priceAllCoversMixedCategoryPageWithGapAndNormalRows() {
        stubRateTable(CUSTOM_LEVEL_ID, Map.of(
                CATEGORY_ID, new BigDecimal("0.8000"),
                10L, new BigDecimal("1.0000")));

        List<LevelPricing> page = levelPricingService.priceAll(
                CUSTOM_LEVEL_ID,
                List.of(
                        ProductPricingFacts.range(
                                CATEGORY_ID, new BigDecimal("100.00"), new BigDecimal("200.00"), false),
                        ProductPricingFacts.range(
                                10L, new BigDecimal("30.00"), new BigDecimal("40.00"), false),
                        ProductPricingFacts.range(
                                11L, new BigDecimal("7.50"), new BigDecimal("7.50"), false)));

        assertThat(page).hasSize(3);
        assertThat(page.get(0).sellable()).isTrue();
        assertThat(page.get(0).minPrice()).isEqualByComparingTo("80.00");
        assertThat(page.get(0).maxPrice()).isEqualByComparingTo("160.00");
        assertThat(page.get(1).sellable()).isTrue();
        assertThat(page.get(1).minPrice()).isEqualByComparingTo("30.00");
        assertThat(page.get(1).maxPrice()).isEqualByComparingTo("40.00");
        assertThat(page.get(2).sellable()).isFalse();
        assertThat(page.get(2).rate()).isNull();
        assertThat(page.get(2).minPrice()).isNull();
        assertThat(page.get(2).maxPrice()).isNull();
        verify(pricingRuleService, times(1)).rateTable(CUSTOM_LEVEL_ID);
    }

    private BigDecimal priceFor(long categoryId, String basePrice) {
        return levelPricingService.price(
                        CUSTOM_LEVEL_ID,
                        ProductPricingFacts.of(categoryId, new BigDecimal(basePrice), false))
                .minPrice();
    }

    private void stubRateTable(long levelId, Map<Long, BigDecimal> rates) {
        when(pricingRuleService.rateTable(levelId))
                .thenReturn(new LevelRateTable(levelId, rates));
    }
}
