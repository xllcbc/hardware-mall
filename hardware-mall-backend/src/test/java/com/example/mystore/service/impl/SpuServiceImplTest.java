package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.dto.AdminLevelPricing;
import com.example.mystore.entity.dto.LevelPricing;
import com.example.mystore.entity.vo.AdminProductPricingVO;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductDetailVO;
import com.example.mystore.entity.vo.ProductListCacheItem;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.entity.vo.ProductListVO;
import com.example.mystore.entity.vo.UserLevelProductPreviewVO;
import com.example.mystore.event.ProductCacheInvalidationEvent;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.LevelPricingService;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.SpecItemService;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpuServiceImplTest {

    @Mock
    private SpuMapper spuMapper;
    @Mock
    private SkuService skuService;
    @Mock
    private SpecTemplateService specTemplateService;
    @Mock
    private SpecItemService specItemService;
    @Mock
    private ProductCacheService productCacheService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PricingRuleService pricingRuleService;
    @Mock
    private LevelPricingService levelPricingService;

    @InjectMocks
    private SpuServiceImpl spuService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void productList_wiresModulePricingIntoView() {
        ProductListCacheItem item = listItem(
                1L, "100.00", "200.00", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        when(productCacheService.getProductList(eq(1L), eq(1), eq(10), eq(1), any()))
                .thenReturn(new ProductListResult(List.of(item), 1));
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(LevelPricing.priced(
                        2L, new BigDecimal("0.8000"), new BigDecimal("80.00"), new BigDecimal("160.00"))));
        UserContext.setLevelId(2L);

        var result = spuService.getProductListVO(1L, null, 1, 10, 1);

        ProductListVO vo = result.getRecords().get(0);
        assertThat(vo.getMinPrice()).isEqualByComparingTo("80.00");
        assertThat(vo.getMaxPrice()).isEqualByComparingTo("160.00");
        assertThat(vo.isSellable()).isTrue();
        assertThat(vo.getOriginalPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    void productList_wiresExcludeDiscountFactAndModuleResult() {
        ProductListCacheItem item = listItem(
                1L, "100.00", "200.00", DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED);
        when(productCacheService.getProductList(any(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(new ProductListResult(List.of(item), 1));
        when(levelPricingService.priceAll(eq(2L), argThat(facts -> facts.size() == 1
                && facts.get(0).excludeDiscount())))
                .thenReturn(List.of(LevelPricing.priced(
                        1L, BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("200.00"))));
        UserContext.setLevelId(2L);

        ProductListVO vo = spuService.getProductListVO(1L, null, 1, 10, 1)
                .getRecords().get(0);

        assertThat(vo.getMinPrice()).isEqualByComparingTo("100.00");
        assertThat(vo.getMaxPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void productList_gapLeavesRangeEmpty() {
        ProductListCacheItem item = listItem(
                1L, "100.00", "200.00", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        when(productCacheService.getProductList(any(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(new ProductListResult(List.of(item), 1));
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(LevelPricing.missing(2L)));
        UserContext.setLevelId(2L);

        ProductListVO vo = spuService.getProductListVO(1L, null, 1, 10, 1)
                .getRecords().get(0);

        assertThat(vo.getMinPrice()).isNull();
        assertThat(vo.getMaxPrice()).isNull();
        assertThat(vo.isSellable()).isFalse();
        assertThat(vo.getOriginalPrice()).isNull();
    }

    @Test
    void productListBatchPricesWholePageWithoutPerProductRateReads() {
        ProductListCacheItem normal = listItem(
                1L, "100.00", "200.00", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        normal.setId(1L);
        ProductListCacheItem otherCategory = listItem(
                3L, "50.00", "60.00", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        otherCategory.setId(2L);
        ProductListCacheItem gap = listItem(
                2L, "10.00", "20.00", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        gap.setId(3L);
        when(productCacheService.getProductList(any(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(new ProductListResult(List.of(normal, otherCategory, gap), 3));
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(
                        LevelPricing.priced(2L, new BigDecimal("0.8000"),
                                new BigDecimal("80.00"), new BigDecimal("160.00")),
                        LevelPricing.priced(2L, new BigDecimal("1.0000"),
                                new BigDecimal("50.00"), new BigDecimal("60.00")),
                        LevelPricing.missing(2L)));
        UserContext.setLevelId(2L);

        List<ProductListVO> records = spuService.getProductListVO(null, null, 1, 10, 1)
                .getRecords();

        assertThat(records).hasSize(3);
        assertThat(records.get(0).isSellable()).isTrue();
        assertThat(records.get(0).getOriginalPrice()).isEqualByComparingTo("300.00");
        assertThat(records.get(1).getMinPrice()).isEqualByComparingTo("50.00");
        assertThat(records.get(1).isSellable()).isTrue();
        assertThat(records.get(2).isSellable()).isFalse();
        assertThat(records.get(2).getMinPrice()).isNull();
        assertThat(records.get(2).getOriginalPrice()).isNull();
        verify(levelPricingService, times(1)).priceAll(eq(2L), argThat(facts -> facts.size() == 3
                && facts.get(0).categoryId().equals(1L)
                && facts.get(1).categoryId().equals(3L)
                && facts.get(2).categoryId().equals(2L)
                && facts.get(2).basePriceMin().compareTo(new BigDecimal("10.00")) == 0
                && facts.get(2).basePriceMax().compareTo(new BigDecimal("20.00")) == 0));
        verify(pricingRuleService, never()).rateFor(any(), any(), anyBoolean());
    }

    @Test
    void recommendBatchPricesWholeListWithoutPerProductRateReads() {
        ProductListCacheItem normal = listItem(
                1L, "100.00", "200.00", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        ProductListCacheItem gap = listItem(
                2L, "10.00", "20.00", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        when(productCacheService.getRecommend(any()))
                .thenReturn(new ProductListResult(List.of(normal, gap), 2));
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(
                        LevelPricing.priced(2L, new BigDecimal("0.8000"),
                                new BigDecimal("80.00"), new BigDecimal("160.00")),
                        LevelPricing.missing(2L)));
        UserContext.setLevelId(2L);

        List<ProductListVO> records = spuService.getRecommendProductListVO(10);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).getMinPrice()).isEqualByComparingTo("80.00");
        assertThat(records.get(0).isSellable()).isTrue();
        assertThat(records.get(1).isSellable()).isFalse();
        assertThat(records.get(1).getOriginalPrice()).isNull();
        verify(levelPricingService, times(1)).priceAll(eq(2L), anyList());
        verify(pricingRuleService, never()).rateFor(any(), any(), anyBoolean());
    }

    @Test
    void priceListPricesBatchByIdsUsingCurrentLevel() {
        Spu spu1 = spu(1L, 1L, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        Spu spu2 = spu(2L, 2L, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        when(spuMapper.selectList(any())).thenReturn(List.of(spu1, spu2));
        when(skuService.getSkusBySpuIds(anyList())).thenReturn(Map.of(
                1L, List.of(sku(10L, 1L, "100.00")),
                2L, List.of(sku(20L, 2L, "50.00"))));
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(
                        LevelPricing.priced(2L, new BigDecimal("0.8000"),
                                new BigDecimal("80.00"), new BigDecimal("80.00")),
                        LevelPricing.missing(2L)));
        UserContext.setLevelId(2L);

        List<ProductListVO> result = spuService.getProductPriceList(List.of(1L, 2L));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getMinPrice()).isEqualByComparingTo("80.00");
        assertThat(result.get(0).isSellable()).isTrue();
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).isSellable()).isFalse();
        assertThat(result.get(1).getMinPrice()).isNull();
        verify(levelPricingService, times(1)).priceAll(eq(2L), argThat(facts -> facts.size() == 2
                && facts.get(0).categoryId().equals(1L)
                && facts.get(1).categoryId().equals(2L)));
    }

    @Test
    void productDetail_wiresModulePricingAndReadsFreshStock() {
        Spu spu = spu(1L, 1L, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        Sku sku = sku(10L, 1L, "10.00");
        ProductDetailCache cache = detailCache(spu, List.of(sku));
        when(productCacheService.getProductDetail(eq(1L), any())).thenReturn(cache);
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(LevelPricing.priced(
                        2L, new BigDecimal("0.8000"), new BigDecimal("8.00"), new BigDecimal("8.00"))));
        when(skuService.getStockById(10L)).thenReturn(7);
        UserContext.setLevelId(2L);

        ProductDetailVO detail = spuService.getProductDetailVO(1L);

        assertThat(detail.getSkus().get(0).getPrice()).isEqualByComparingTo("8.00");
        assertThat(detail.getSkus().get(0).isSellable()).isTrue();
        assertThat(detail.getSkus().get(0).getStock()).isEqualTo(7);
        assertThat(detail.getMinPrice()).isEqualByComparingTo("8.00");
        assertThat(detail.isSellable()).isTrue();
    }

    @Test
    void productDetail_gapLeavesPricesEmpty() {
        Spu spu = spu(1L, 1L, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        Sku sku = sku(10L, 1L, "10.00");
        when(productCacheService.getProductDetail(eq(1L), any()))
                .thenReturn(detailCache(spu, List.of(sku)));
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(LevelPricing.missing(2L)));
        when(skuService.getStockById(10L)).thenReturn(7);
        UserContext.setLevelId(2L);

        ProductDetailVO detail = spuService.getProductDetailVO(1L);

        assertThat(detail.getSkus().get(0).getPrice()).isNull();
        assertThat(detail.getSkus().get(0).isSellable()).isFalse();
        assertThat(detail.getMinPrice()).isNull();
        assertThat(detail.getMaxPrice()).isNull();
        assertThat(detail.isSellable()).isFalse();
        assertThat(detail.getSpu().getOriginalPrice()).isNull();
        assertThat(spu.getOriginalPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void productDetail_passesExplicitLevelAndSkuFactsToPricingModule() {
        Spu spu = spu(1L, 1L, DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED);
        Sku sku = sku(10L, 1L, "10.00");
        when(productCacheService.getProductDetail(eq(1L), any()))
                .thenReturn(detailCache(spu, List.of(sku)));
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(LevelPricing.priced(
                        1L, BigDecimal.ONE, new BigDecimal("10.00"), new BigDecimal("10.00"))));
        when(skuService.getStockById(10L)).thenReturn(1);
        UserContext.setLevelId(2L);

        spuService.getProductDetailVO(1L);

        verify(levelPricingService).priceAll(eq(2L), argThat(facts ->
                facts.size() == 1
                        && facts.get(0).categoryId().equals(1L)
                        && facts.get(0).basePriceMin().compareTo(new BigDecimal("10.00")) == 0
                        && facts.get(0).excludeDiscount()));
    }

    @Test
    void productDetail_withoutSkuPassesOriginalPriceAsFact() {
        Spu spu = spu(1L, 1L, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        spu.setOriginalPrice(new BigDecimal("10.00"));
        when(productCacheService.getProductDetail(eq(1L), any()))
                .thenReturn(detailCache(spu, Collections.emptyList()));
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(LevelPricing.priced(
                        2L, new BigDecimal("0.8000"), new BigDecimal("8.00"), new BigDecimal("8.00"))));
        UserContext.setLevelId(2L);

        ProductDetailVO detail = spuService.getProductDetailVO(1L);

        assertThat(detail.getSkus()).isEmpty();
        assertThat(detail.getMinPrice()).isEqualByComparingTo("8.00");
        assertThat(detail.getMaxPrice()).isEqualByComparingTo("8.00");
        verify(levelPricingService).priceAll(eq(2L), argThat(facts ->
                facts.size() == 1
                        && facts.get(0).basePriceMin().compareTo(new BigDecimal("10.00")) == 0));
    }

    @Test
    void productSkusReuseDetailCache() {
        Spu spu = spu(1L, 1L, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        ProductDetailCache cache = detailCache(spu, List.of(sku(10L, 1L, "10.00")));
        when(productCacheService.getProductDetail(eq(1L), any())).thenReturn(cache);
        when(levelPricingService.priceAll(any(), anyList()))
                .thenReturn(List.of(LevelPricing.priced(
                        1L, BigDecimal.ONE, new BigDecimal("10.00"), new BigDecimal("10.00"))));
        when(skuService.getStockById(10L)).thenReturn(1);

        assertThat(spuService.getProductSkuVOs(1L)).hasSize(1);
        verify(skuService, never()).getSkusBySpu(anyLong(), any());
    }

    @Test
    void levelPreviewUsesNeutralListCacheAndTargetLevelRate() {
        ProductListCacheItem item = listItem(
                1L, "100.00", "200.00", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        when(productCacheService.getProductList(any(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(new ProductListResult(List.of(item), 1));
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(LevelPricing.priced(
                        2L, new BigDecimal("0.7000"), new BigDecimal("70.00"), new BigDecimal("140.00"))));

        var page = spuService.getUserLevelProductPreview(2L, null, 1, 10);

        UserLevelProductPreviewVO vo = page.getRecords().get(0);
        assertThat(vo.getPricingRate()).isEqualByComparingTo("0.7000");
        assertThat(vo.getMinPrice()).isEqualByComparingTo("70.00");
        assertThat(vo.getMaxPrice()).isEqualByComparingTo("140.00");
        assertThat(vo.isSellable()).isTrue();
        assertThat(vo.getOriginalPrice()).isEqualByComparingTo("300.00");
        assertThat(vo.getRetailMinPrice()).isEqualByComparingTo("100.00");
        assertThat(vo.getRetailMaxPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void levelPreviewBatchPricesPageAndMasksBasePriceOnGap() {
        ProductListCacheItem normal = listItem(
                1L, "100.00", "200.00", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        ProductListCacheItem gap = listItem(
                2L, "10.00", "20.00", DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        when(productCacheService.getProductList(any(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(new ProductListResult(List.of(normal, gap), 2));
        when(levelPricingService.priceAll(eq(2L), anyList()))
                .thenReturn(List.of(
                        LevelPricing.priced(2L, new BigDecimal("0.7000"),
                                new BigDecimal("70.00"), new BigDecimal("140.00")),
                        LevelPricing.missing(2L)));

        var page = spuService.getUserLevelProductPreview(2L, null, 1, 10);

        UserLevelProductPreviewVO priced = page.getRecords().get(0);
        assertThat(priced.isSellable()).isTrue();
        assertThat(priced.getMinPrice()).isEqualByComparingTo("70.00");
        assertThat(priced.getMaxPrice()).isEqualByComparingTo("140.00");

        UserLevelProductPreviewVO missing = page.getRecords().get(1);
        assertThat(missing.isSellable()).isFalse();
        assertThat(missing.getPricingRate()).isNull();
        assertThat(missing.getMinPrice()).isNull();
        assertThat(missing.getMaxPrice()).isNull();
        assertThat(missing.getOriginalPrice()).isNull();
        assertThat(missing.getRetailMinPrice()).isNull();
        assertThat(missing.getRetailMaxPrice()).isNull();

        verify(levelPricingService, times(1)).priceAll(eq(2L), argThat(facts -> facts.size() == 2
                && facts.get(0).categoryId().equals(1L)
                && facts.get(1).categoryId().equals(2L)
                && facts.get(1).basePriceMin().compareTo(new BigDecimal("10.00")) == 0
                && facts.get(1).basePriceMax().compareTo(new BigDecimal("20.00")) == 0));
        verify(pricingRuleService, never()).rateFor(any(), any(), anyBoolean());
    }

    @Test
    void adminPricingPageUsesOneBatchCallAndPreservesResponseFields() {
        Spu item = spu(1L, 9L, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        Page<Spu> source = new Page<>(1, 20, 1);
        source.setRecords(List.of(item));
        when(spuMapper.selectPage(any(), any())).thenReturn(source);
        when(skuService.getSkusBySpuIds(List.of(1L))).thenReturn(Map.of(
                1L, List.of(sku(11L, 1L, "10.00"), sku(12L, 1L, "20.00"))));
        when(levelPricingService.priceAllForAdmin(eq(2L), argThat(facts -> facts.size() == 1
                && facts.get(0).categoryId().equals(9L)
                && facts.get(0).basePriceMin().compareTo(new BigDecimal("10.00")) == 0
                && facts.get(0).basePriceMax().compareTo(new BigDecimal("20.00")) == 0)))
                .thenReturn(List.of(new AdminLevelPricing(
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00"),
                        LevelPricing.priced(2L, new BigDecimal("0.8000"),
                                new BigDecimal("8.00"), new BigDecimal("16.00")),
                        LevelPricing.priced(1L, new BigDecimal("1.2000"),
                                new BigDecimal("12.00"), new BigDecimal("24.00")),
                        LevelPricing.priced(2L, new BigDecimal("0.8000"),
                                new BigDecimal("8.00"), new BigDecimal("16.00")))));

        Page<AdminProductPricingVO> page = spuService.getAdminProductPricingPage(
                2L, null, null, null, 1, 20);

        AdminProductPricingVO vo = page.getRecords().get(0);
        assertThat(vo.getRetailMinPrice()).isEqualByComparingTo("10.00");
        assertThat(vo.getRetailMaxPrice()).isEqualByComparingTo("20.00");
        assertThat(vo.getPricingRate()).isEqualByComparingTo("0.8000");
        assertThat(vo.getEffectiveRate()).isEqualByComparingTo("0.8000");
        assertThat(vo.getSettlementMinPrice()).isEqualByComparingTo("8.00");
        assertThat(vo.getSettlementMaxPrice()).isEqualByComparingTo("16.00");
        assertThat(vo.isRateMissing()).isFalse();
        verify(levelPricingService, times(1)).priceAllForAdmin(eq(2L), anyList());
    }

    @Test
    void createSpuDefaultsExcludeDiscount() {
        Spu incoming = new Spu();
        incoming.setCategoryId(1L);
        incoming.setName("测试");

        spuService.createSpu(incoming);

        verify(spuMapper).insert(org.mockito.ArgumentMatchers.<Spu>argThat(
                spu -> spu.getExcludeDiscount() == DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED));
    }

    @Test
    void updateSpuCopiesExcludeDiscountAndPublishesEvent() {
        Spu exist = spu(1L, 1L, DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        when(spuMapper.selectById(1L)).thenReturn(exist);
        Spu update = new Spu();
        update.setId(1L);
        update.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED);

        spuService.updateSpu(update);

        assertThat(exist.getExcludeDiscount()).isEqualTo(DiscountConstants.DISCOUNT_PARTICIPATION_DISABLED);
        verify(eventPublisher).publishEvent(any(ProductCacheInvalidationEvent.class));
    }

    private ProductListCacheItem listItem(Long categoryId, String min, String max, int excludeDiscount) {
        ProductListCacheItem item = new ProductListCacheItem();
        item.setId(1L);
        item.setCategoryId(categoryId);
        item.setName("商品");
        item.setBasePriceMin(new BigDecimal(min));
        item.setBasePriceMax(new BigDecimal(max));
        item.setOriginalPrice(new BigDecimal("300.00"));
        item.setExcludeDiscount(excludeDiscount);
        item.setStatus(StatusConstants.PRODUCT_STATUS_ON_SHELF);
        return item;
    }

    private Spu spu(Long id, Long categoryId, int excludeDiscount) {
        Spu spu = new Spu();
        spu.setId(id);
        spu.setCategoryId(categoryId);
        spu.setName("商品");
        spu.setOriginalPrice(new BigDecimal("100.00"));
        spu.setStatus(StatusConstants.PRODUCT_STATUS_ON_SHELF);
        spu.setDeleteTime(0L);
        spu.setExcludeDiscount(excludeDiscount);
        return spu;
    }

    private Sku sku(Long id, Long spuId, String price) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setSpuId(spuId);
        sku.setPrice(new BigDecimal(price));
        sku.setSpecs(Collections.emptyList());
        sku.setStatus(1);
        return sku;
    }

    private ProductDetailCache detailCache(Spu spu, List<Sku> skus) {
        ProductDetailCache cache = new ProductDetailCache();
        cache.setSpu(spu);
        cache.setSkus(skus);
        cache.setSpecTemplates(Collections.emptyList());
        cache.setSpecItemsMap(Collections.emptyMap());
        return cache;
    }
}
