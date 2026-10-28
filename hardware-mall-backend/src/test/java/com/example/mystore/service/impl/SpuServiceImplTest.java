package com.example.mystore.service.impl;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductDetailVO;
import com.example.mystore.entity.vo.ProductListCacheItem;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.entity.vo.ProductListVO;
import com.example.mystore.entity.vo.UserLevelProductPreviewVO;
import com.example.mystore.event.ProductCacheInvalidationEvent;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.PriceService;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

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

    @Spy
    private PriceService priceService = new PriceServiceImpl();

    @InjectMocks
    private SpuServiceImpl spuService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void productList_appliesCategoryRateToRetailRange() {
        ProductListCacheItem item = listItem(1L, "100.00", "200.00", 0);
        when(productCacheService.getProductList(eq(1L), eq(1), eq(10), eq(1), any()))
                .thenReturn(new ProductListResult(List.of(item), 1));
        when(pricingRuleService.rateFor(2L, 1L, false)).thenReturn(new BigDecimal("0.8000"));
        UserContext.setLevelId(2L);

        var result = spuService.getProductListVO(1L, null, 1, 10, 1);

        ProductListVO vo = result.getRecords().get(0);
        assertThat(vo.getMinPrice()).isEqualByComparingTo("80.00");
        assertThat(vo.getMaxPrice()).isEqualByComparingTo("160.00");
    }

    @Test
    void productList_excludeDiscountKeepsRetail() {
        ProductListCacheItem item = listItem(1L, "100.00", "200.00", 1);
        when(productCacheService.getProductList(any(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(new ProductListResult(List.of(item), 1));
        when(pricingRuleService.rateFor(2L, 1L, true)).thenReturn(BigDecimal.ONE);
        UserContext.setLevelId(2L);

        ProductListVO vo = spuService.getProductListVO(1L, null, 1, 10, 1)
                .getRecords().get(0);

        assertThat(vo.getMinPrice()).isEqualByComparingTo("100.00");
        assertThat(vo.getMaxPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void productDetail_appliesRateAndReadsFreshStock() {
        Spu spu = spu(1L, 1L, 0);
        Sku sku = sku(10L, 1L, "10.00");
        ProductDetailCache cache = detailCache(spu, List.of(sku));
        when(productCacheService.getProductDetail(eq(1L), any())).thenReturn(cache);
        when(pricingRuleService.rateFor(2L, 1L, false)).thenReturn(new BigDecimal("0.8000"));
        when(skuService.getStockById(10L)).thenReturn(7);
        UserContext.setLevelId(2L);

        ProductDetailVO detail = spuService.getProductDetailVO(1L);

        assertThat(detail.getSkus().get(0).getPrice()).isEqualByComparingTo("8.00");
        assertThat(detail.getSkus().get(0).getStock()).isEqualTo(7);
        assertThat(detail.getMinPrice()).isEqualByComparingTo("8.00");
    }

    @Test
    void productDetail_withoutSkuUsesOriginalPriceTimesRate() {
        Spu spu = spu(1L, 1L, 0);
        spu.setOriginalPrice(new BigDecimal("10.00"));
        when(productCacheService.getProductDetail(eq(1L), any()))
                .thenReturn(detailCache(spu, Collections.emptyList()));
        when(pricingRuleService.rateFor(2L, 1L, false)).thenReturn(new BigDecimal("0.8000"));
        UserContext.setLevelId(2L);

        ProductDetailVO detail = spuService.getProductDetailVO(1L);

        assertThat(detail.getSkus()).isEmpty();
        assertThat(detail.getMinPrice()).isEqualByComparingTo("8.00");
        assertThat(detail.getMaxPrice()).isEqualByComparingTo("8.00");
    }

    @Test
    void productSkusReuseDetailCache() {
        Spu spu = spu(1L, 1L, 0);
        ProductDetailCache cache = detailCache(spu, List.of(sku(10L, 1L, "10.00")));
        when(productCacheService.getProductDetail(eq(1L), any())).thenReturn(cache);
        when(pricingRuleService.rateFor(any(), any(), anyBoolean())).thenReturn(BigDecimal.ONE);
        when(skuService.getStockById(10L)).thenReturn(1);

        assertThat(spuService.getProductSkuVOs(1L)).hasSize(1);
        verify(skuService, never()).getSkusBySpu(anyLong(), any());
    }

    @Test
    void levelPreviewUsesNeutralListCacheAndTargetLevelRate() {
        ProductListCacheItem item = listItem(1L, "100.00", "200.00", 0);
        when(productCacheService.getProductList(any(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(new ProductListResult(List.of(item), 1));
        when(pricingRuleService.rateFor(2L, 1L, false)).thenReturn(new BigDecimal("0.7000"));

        var page = spuService.getUserLevelProductPreview(2L, null, 1, 10);

        UserLevelProductPreviewVO vo = page.getRecords().get(0);
        assertThat(vo.getDiscountRate()).isEqualByComparingTo("0.7000");
        assertThat(vo.getMinPrice()).isEqualByComparingTo("70.00");
        assertThat(vo.getMaxPrice()).isEqualByComparingTo("140.00");
    }

    @Test
    void createSpuDefaultsExcludeDiscount() {
        Spu incoming = new Spu();
        incoming.setCategoryId(1L);
        incoming.setName("测试");

        spuService.createSpu(incoming);

        verify(spuMapper).insert(org.mockito.ArgumentMatchers.<Spu>argThat(
                spu -> spu.getExcludeDiscount() == 0));
    }

    @Test
    void updateSpuCopiesExcludeDiscountAndPublishesEvent() {
        Spu exist = spu(1L, 1L, 0);
        when(spuMapper.selectById(1L)).thenReturn(exist);
        Spu update = new Spu();
        update.setId(1L);
        update.setExcludeDiscount(1);

        spuService.updateSpu(update);

        assertThat(exist.getExcludeDiscount()).isEqualTo(1);
        verify(eventPublisher).publishEvent(any(ProductCacheInvalidationEvent.class));
    }

    private ProductListCacheItem listItem(Long categoryId, String min, String max, int excludeDiscount) {
        ProductListCacheItem item = new ProductListCacheItem();
        item.setId(1L);
        item.setCategoryId(categoryId);
        item.setName("商品");
        item.setRetailMinPrice(new BigDecimal(min));
        item.setRetailMaxPrice(new BigDecimal(max));
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
