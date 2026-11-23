package com.example.mystore.service.impl;

import com.example.mystore.entity.dto.AdminLevelPricing;
import com.example.mystore.entity.dto.LevelRateTable;
import com.example.mystore.entity.dto.ProductPricingFacts;
import com.example.mystore.service.LevelPricingService;
import com.example.mystore.service.PriceService;
import com.example.mystore.service.PricingRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 等级定价模块管理入口契约测试：一次得到视角等级与零售等级两份结果，
 * 并保留批发基价用于管理端价格视角组装。缺口与显式 1.0000 必须可区分。
 */
@ExtendWith(MockitoExtension.class)
class AdminLevelPricingContractTest {

    private static final long VIEW_LEVEL_ID = 2L;
    private static final long RETAIL_LEVEL_ID = 1L;
    private static final long CATEGORY_ID = 9L;

    @Mock
    private PricingRuleService pricingRuleService;

    private final PriceService priceService = new PriceServiceImpl();
    private LevelPricingService levelPricingService;

    @BeforeEach
    void setUp() {
        levelPricingService = new LevelPricingServiceImpl(pricingRuleService, priceService);
        when(pricingRuleService.resolveRetailLevelId()).thenReturn(RETAIL_LEVEL_ID);
    }

    @Test
    void adminEntryReturnsViewAndRetailResultsAlongsideWholesaleBasePrice() {
        stubRateTable(VIEW_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("0.8000")));
        stubRateTable(RETAIL_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.2000")));

        AdminLevelPricing pricing = levelPricingService.priceForAdmin(
                VIEW_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));

        assertThat(pricing.basePriceMin()).isEqualByComparingTo("25.00");
        assertThat(pricing.basePriceMax()).isEqualByComparingTo("25.00");
        assertThat(pricing.viewLevel().levelId()).isEqualTo(VIEW_LEVEL_ID);
        assertThat(pricing.viewLevel().rate()).isEqualByComparingTo("0.8000");
        assertThat(pricing.viewLevel().minPrice()).isEqualByComparingTo("20.00");
        assertThat(pricing.retailLevel().levelId()).isEqualTo(RETAIL_LEVEL_ID);
        assertThat(pricing.retailLevel().rate()).isEqualByComparingTo("1.2000");
        assertThat(pricing.retailLevel().minPrice()).isEqualByComparingTo("30.00");
        assertThat(pricing.effective().levelId()).isEqualTo(VIEW_LEVEL_ID);
        assertThat(pricing.effective().minPrice()).isEqualByComparingTo("20.00");
        assertThat(pricing.rateMissing()).isFalse();
    }

    @Test
    void gapIsNotConfusedWithExplicitOneRate() {
        stubRateTable(VIEW_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.0000")));
        stubRateTable(RETAIL_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.2000")));

        AdminLevelPricing explicitOne = levelPricingService.priceForAdmin(
                VIEW_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));
        AdminLevelPricing gap = levelPricingService.priceForAdmin(
                VIEW_LEVEL_ID,
                ProductPricingFacts.of(10L, new BigDecimal("25.00"), false));

        assertThat(explicitOne.rateMissing()).isFalse();
        assertThat(explicitOne.viewLevel().sellable()).isTrue();
        assertThat(explicitOne.viewLevel().rate()).isEqualByComparingTo("1.0000");
        assertThat(explicitOne.effective().minPrice()).isEqualByComparingTo("25.00");

        assertThat(gap.rateMissing()).isTrue();
        assertThat(gap.viewLevel().sellable()).isFalse();
        assertThat(gap.viewLevel().rate()).isNull();
        assertThat(gap.effective().sellable()).isFalse();
        // 缺口时管理端仍可见批发基价，但不得回落到 1.0000 或给出结算价。
        assertThat(gap.basePriceMin()).isEqualByComparingTo("25.00");
        assertThat(gap.effective().minPrice()).isNull();
    }

    @Test
    void excludedDiscountProductSettlesAtRetailButStillShowsViewRate() {
        stubRateTable(VIEW_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("0.8000")));
        stubRateTable(RETAIL_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.2000")));

        AdminLevelPricing pricing = levelPricingService.priceForAdmin(
                VIEW_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), true));

        assertThat(pricing.viewLevel().rate()).isEqualByComparingTo("0.8000");
        assertThat(pricing.effective().levelId()).isEqualTo(RETAIL_LEVEL_ID);
        assertThat(pricing.effective().rate()).isEqualByComparingTo("1.2000");
        assertThat(pricing.effective().minPrice()).isEqualByComparingTo("30.00");
        assertThat(pricing.rateMissing()).isFalse();
    }

    @Test
    void retailGapMakesExcludedProductUnsellableEvenWhenViewRateExists() {
        stubRateTable(VIEW_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("0.8000")));
        stubRateTable(RETAIL_LEVEL_ID, Map.of());

        AdminLevelPricing pricing = levelPricingService.priceForAdmin(
                VIEW_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), true));

        assertThat(pricing.viewLevel().rate()).isEqualByComparingTo("0.8000");
        assertThat(pricing.effective().sellable()).isFalse();
        assertThat(pricing.rateMissing()).isTrue();
        assertThat(pricing.effective().minPrice()).isNull();
    }

    @Test
    void batchAdminEntryReadsEachRateTableOnceForTheWholePage() {
        stubRateTable(VIEW_LEVEL_ID, Map.of(
                CATEGORY_ID, new BigDecimal("0.8000"),
                10L, new BigDecimal("1.0000")));
        stubRateTable(RETAIL_LEVEL_ID, Map.of(
                CATEGORY_ID, new BigDecimal("1.2000"),
                10L, new BigDecimal("1.5000")));

        List<AdminLevelPricing> page = levelPricingService.priceAllForAdmin(
                VIEW_LEVEL_ID,
                List.of(
                        ProductPricingFacts.range(
                                CATEGORY_ID, new BigDecimal("10.00"), new BigDecimal("20.00"), false),
                        ProductPricingFacts.of(10L, new BigDecimal("30.00"), false),
                        ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("30.00"), true),
                        ProductPricingFacts.of(11L, new BigDecimal("40.00"), false)));

        assertThat(page).hasSize(4);
        assertThat(page.get(0).viewLevel().minPrice()).isEqualByComparingTo("8.00");
        assertThat(page.get(0).viewLevel().maxPrice()).isEqualByComparingTo("16.00");
        assertThat(page.get(0).retailLevel().minPrice()).isEqualByComparingTo("12.00");
        assertThat(page.get(0).effective().minPrice()).isEqualByComparingTo("8.00");
        assertThat(page.get(1).viewLevel().rate()).isEqualByComparingTo("1.0000");
        assertThat(page.get(1).rateMissing()).isFalse();
        // 控价商品的有效结算走零售，视角倍率仍保留
        assertThat(page.get(2).viewLevel().rate()).isEqualByComparingTo("0.8000");
        assertThat(page.get(2).effective().rate()).isEqualByComparingTo("1.2000");
        assertThat(page.get(2).effective().minPrice()).isEqualByComparingTo("36.00");
        assertThat(page.get(3).rateMissing()).isTrue();
        assertThat(page.get(3).basePriceMax()).isEqualByComparingTo("40.00");

        verify(pricingRuleService, times(1)).rateTable(VIEW_LEVEL_ID);
        verify(pricingRuleService, times(1)).rateTable(RETAIL_LEVEL_ID);
    }

    @Test
    void retailViewReusesOneRateTableForBothResults() {
        stubRateTable(RETAIL_LEVEL_ID, Map.of(CATEGORY_ID, new BigDecimal("1.2000")));

        AdminLevelPricing pricing = levelPricingService.priceForAdmin(
                RETAIL_LEVEL_ID,
                ProductPricingFacts.of(CATEGORY_ID, new BigDecimal("25.00"), false));

        assertThat(pricing.viewLevel().levelId()).isEqualTo(RETAIL_LEVEL_ID);
        assertThat(pricing.retailLevel().levelId()).isEqualTo(RETAIL_LEVEL_ID);
        assertThat(pricing.effective().minPrice()).isEqualByComparingTo("30.00");
        verify(pricingRuleService, times(1)).rateTable(RETAIL_LEVEL_ID);
    }

    private void stubRateTable(long levelId, Map<Long, BigDecimal> rates) {
        when(pricingRuleService.rateTable(levelId))
                .thenReturn(new LevelRateTable(levelId, rates));
    }
}
