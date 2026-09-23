package com.example.mystore.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductDetailVO;
import com.example.mystore.entity.vo.ProductListVO;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.event.ProductCacheInvalidationEvent;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.PriceService;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.SpecItemService;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.util.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @org.mockito.Spy
    private PriceService priceService = new PriceServiceImpl();

    @InjectMocks
    private SpuServiceImpl spuService;

    private Spu spu1;
    private Sku sku1;
    private Sku sku2;

    @BeforeEach
    void setUp() {
        spu1 = new Spu();
        spu1.setId(1L);
        spu1.setCategoryId(1L);
        spu1.setName("防盗门锁 C级");
        spu1.setSubtitle("家用防盗门锁心");
        spu1.setOriginalPrice(new BigDecimal("299.00"));
        spu1.setSalesCount(52);
        spu1.setStatus(1);
        spu1.setIsRecommend(1);
        spu1.setDeleteTime(0L);

        sku1 = new Sku();
        sku1.setId(1L);
        sku1.setSpuId(1L);
        sku1.setPrice(new BigDecimal("199.00"));
        sku1.setStatus(1);

        sku2 = new Sku();
        sku2.setId(2L);
        sku2.setSpuId(1L);
        sku2.setPrice(new BigDecimal("299.00"));
        sku2.setStatus(1);
    }

    @Test
    void testGetSpuById_Success() {
        when(spuMapper.selectById(1L)).thenReturn(spu1);

        Spu result = spuService.getSpuById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("防盗门锁 C级");
    }

    @Test
    void testGetSpuById_Deleted() {
        Spu deleted = new Spu();
        deleted.setId(3L);
        deleted.setDeleteTime(System.currentTimeMillis());
        when(spuMapper.selectById(3L)).thenReturn(deleted);

        assertThatThrownBy(() -> spuService.getSpuById(3L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("商品不存在或已下架");
    }

    @Test
    void testCreateSpu_DefaultValues() {
        Spu newSpu = new Spu();
        newSpu.setName("测试商品");
        newSpu.setCategoryId(1L);

        spuService.createSpu(newSpu);

        verify(spuMapper).insert(org.mockito.Mockito.<Spu>argThat(spu ->
                spu.getSalesCount() == 0 &&
                spu.getStatus() == StatusConstants.PRODUCT_STATUS_ON_SHELF &&
                spu.getIsRecommend() == 0 &&
                spu.getDeleteTime() == 0L
        ));
    }

    @Test
    void testGetProductListVO_PriceRange() {
        stubProductListCache();
        when(spuMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Spu> page = inv.getArgument(0);
            page.setRecords(Collections.singletonList(spu1));
            page.setTotal(1);
            return page;
        });
        when(skuService.getSkusBySpuIds(Collections.singletonList(1L)))
                .thenReturn(java.util.Collections.singletonMap(1L, Arrays.asList(sku1, sku2)));

        var result = spuService.getProductListVO(null, null, 1, 10, 1);

        assertThat(result.getRecords()).hasSize(1);
        ProductListVO vo = result.getRecords().get(0);
        assertThat(vo.getMinPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(vo.getMaxPrice()).isEqualByComparingTo(new BigDecimal("299.00"));
    }

    @Test
    void getProductListVO_delegatesToProductCacheService() {
        ProductListResult cached = new ProductListResult(Collections.emptyList(), 0);
        when(productCacheService.getProductList(
                eq(1L), eq(1), eq(10), eq(1), any()))
                .thenReturn(cached);

        var result = spuService.getProductListVO(1L, null, 1, 10, 1);

        assertThat(result.getRecords()).isEmpty();
        verify(productCacheService).getProductList(
                eq(1L), eq(1), eq(10), eq(1), any());
    }

    @Test
    void productList_wholesaleViewUsesWholesaleRangeWhenPresent() {
        sku1.setPrice(new BigDecimal("25.00"));
        sku1.setWholesalePrice(new BigDecimal("18.00"));
        sku2.setPrice(new BigDecimal("30.00"));
        sku2.setWholesalePrice(new BigDecimal("22.00"));
        stubProductListCache();
        when(spuMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Spu> page = inv.getArgument(0);
            page.setRecords(Collections.singletonList(spu1));
            page.setTotal(1);
            return page;
        });
        when(skuService.getSkusBySpuIds(Collections.singletonList(1L)))
                .thenReturn(java.util.Collections.singletonMap(1L, Arrays.asList(sku1, sku2)));

        UserContext.setUserGroup(StatusConstants.USER_GROUP_WHOLESALE);
        try {
            var result = spuService.getProductListVO(null, null, 1, 10, 1);

            ProductListVO vo = result.getRecords().get(0);
            assertThat(vo.getMinPrice()).isEqualByComparingTo(new BigDecimal("18.00"));
            assertThat(vo.getMaxPrice()).isEqualByComparingTo(new BigDecimal("22.00"));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void productList_wholesaleRangeNullWhenNoSkuHasWholesalePrice() {
        stubProductListCache();
        when(spuMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Spu> page = inv.getArgument(0);
            page.setRecords(Collections.singletonList(spu1));
            page.setTotal(1);
            return page;
        });
        when(skuService.getSkusBySpuIds(Collections.singletonList(1L)))
                .thenReturn(java.util.Collections.singletonMap(1L, Arrays.asList(sku1, sku2)));

        UserContext.setUserGroup(StatusConstants.USER_GROUP_WHOLESALE);
        try {
            var result = spuService.getProductListVO(null, null, 1, 10, 1);

            ProductListVO vo = result.getRecords().get(0);
            assertThat(vo.getMinPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
            assertThat(vo.getMaxPrice()).isEqualByComparingTo(new BigDecimal("299.00"));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void productList_retailViewDoesNotSerializeWholesalePriceFields() throws Exception {
        sku1.setWholesalePrice(new BigDecimal("18.00"));
        sku2.setWholesalePrice(new BigDecimal("22.00"));
        stubProductListCache();
        when(spuMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Spu> page = inv.getArgument(0);
            page.setRecords(Collections.singletonList(spu1));
            page.setTotal(1);
            return page;
        });
        when(skuService.getSkusBySpuIds(Collections.singletonList(1L)))
                .thenReturn(java.util.Collections.singletonMap(1L, Arrays.asList(sku1, sku2)));

        UserContext.setUserGroup(StatusConstants.USER_GROUP_RETAIL);
        try {
            var result = spuService.getProductListVO(null, null, 1, 10, 1);
            ProductListVO vo = result.getRecords().get(0);

            String json = new ObjectMapper().writeValueAsString(vo);
            assertThat(json).doesNotContain("wholesale");
            assertThat(vo.getMinPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
            assertThat(vo.getMaxPrice()).isEqualByComparingTo(new BigDecimal("299.00"));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void productList_wholesaleViewUsesEffectivePricesIncludingFallback() {
        sku1.setPrice(new BigDecimal("25.00"));
        sku1.setWholesalePrice(new BigDecimal("18.00"));
        sku2.setPrice(new BigDecimal("30.00"));
        sku2.setWholesalePrice(null);
        stubProductListCache();
        when(spuMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Spu> page = inv.getArgument(0);
            page.setRecords(Collections.singletonList(spu1));
            page.setTotal(1);
            return page;
        });
        when(skuService.getSkusBySpuIds(Collections.singletonList(1L)))
                .thenReturn(java.util.Collections.singletonMap(1L, Arrays.asList(sku1, sku2)));

        UserContext.setUserGroup(StatusConstants.USER_GROUP_WHOLESALE);
        try {
            var result = spuService.getProductListVO(null, null, 1, 10, 1);
            ProductListVO vo = result.getRecords().get(0);

            assertThat(vo.getMinPrice()).isEqualByComparingTo(new BigDecimal("18.00"));
            assertThat(vo.getMaxPrice()).isEqualByComparingTo(new BigDecimal("30.00"));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void getProductListVO_shouldQuerySkusInOneBatchCall() {
        stubProductListCache();
        Spu s1 = new Spu();
        s1.setId(1L);
        s1.setCategoryId(1L);
        s1.setStatus(1);
        s1.setDeleteTime(0L);
        Spu s2 = new Spu();
        s2.setId(2L);
        s2.setCategoryId(1L);
        s2.setStatus(1);
        s2.setDeleteTime(0L);
        when(spuMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Spu> page = inv.getArgument(0);
            page.setRecords(Arrays.asList(s1, s2));
            page.setTotal(2);
            return page;
        });
        when(skuService.getSkusBySpuIds(Arrays.asList(1L, 2L))).thenReturn(java.util.Collections.emptyMap());

        spuService.getProductListVO(1L, null, 1, 10, 1);

        verify(skuService, times(1)).getSkusBySpuIds(anyList());
        verify(skuService, never()).getSkusBySpu(anyLong());
    }

    @Test
    void testGetProductDetailVO_FromCache() {
        ProductDetailCache cached = new ProductDetailCache();
        cached.setSpu(spu1);
        cached.setSkus(Collections.emptyList());
        cached.setSpecTemplates(Collections.emptyList());
        cached.setSpecItemsMap(Collections.emptyMap());
        when(productCacheService.getProductDetail(eq(1L), any())).thenReturn(cached);

        ProductDetailVO result = spuService.getProductDetailVO(1L);

        assertThat(result.getSpu()).isSameAs(spu1);
        verify(spuMapper, never()).selectById(any());
    }

    @Test
    void testGetProductDetailVO_NotFound() {
        when(productCacheService.getProductDetail(eq(1L), any())).thenReturn(null);

        assertThatThrownBy(() -> spuService.getProductDetailVO(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("商品不存在或已下架");
    }

    @Test
    void testGetProductDetailVO_AssembleFromDb() {
        sku1.setSpecs(Collections.emptyList());
        sku2.setSpecs(Collections.emptyList());
        stubProductDetailCache();
        when(spuMapper.selectById(1L)).thenReturn(spu1);
        when(skuService.getSkusBySpu(1L, 1)).thenReturn(Arrays.asList(sku1, sku2));
        when(specTemplateService.getTemplatesByCategory(1L)).thenReturn(Collections.emptyList());
        when(specItemService.getItemsGroupedByTemplateIds(any())).thenReturn(Collections.emptyMap());

        ProductDetailVO result = spuService.getProductDetailVO(1L);

        assertThat(result).isNotNull();
        assertThat(result.getSpu()).isSameAs(spu1);
        assertThat(result.getSkus()).hasSize(2);
        assertThat(result.getMinPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(result.getMaxPrice()).isEqualByComparingTo(new BigDecimal("299.00"));
    }

    @Test
    void productDetail_retailViewDoesNotSerializeWholesalePrice() throws Exception {
        sku1.setSpecs(Collections.emptyList());
        sku1.setWholesalePrice(new BigDecimal("18.00"));
        stubProductDetailCache();
        when(spuMapper.selectById(1L)).thenReturn(spu1);
        when(skuService.getSkusBySpu(1L, 1)).thenReturn(Collections.singletonList(sku1));
        when(specTemplateService.getTemplatesByCategory(1L)).thenReturn(Collections.emptyList());
        when(specItemService.getItemsGroupedByTemplateIds(any())).thenReturn(Collections.emptyMap());

        UserContext.setUserGroup(StatusConstants.USER_GROUP_RETAIL);
        try {
            ProductDetailVO result = spuService.getProductDetailVO(1L);

            String json = new ObjectMapper().writeValueAsString(result);
            assertThat(json).doesNotContain("wholesalePrice");
            assertThat(result.getMinPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void getProductSkuVOs_reusesProductDetailCache() {
        sku1.setSpecs(Collections.emptyList());
        ProductDetailCache cached = new ProductDetailCache();
        cached.setSpu(spu1);
        cached.setSkus(Collections.singletonList(sku1));
        cached.setSpecTemplates(Collections.emptyList());
        cached.setSpecItemsMap(Collections.emptyMap());
        when(productCacheService.getProductDetail(eq(1L), any())).thenReturn(cached);

        var result = spuService.getProductSkuVOs(1L);

        assertThat(result).hasSize(1);
        verify(skuService, never()).getSkusBySpu(anyLong(), any());
    }

    @Test
    void testUpdateStatus() {
        when(spuMapper.selectById(1L)).thenReturn(spu1);

        spuService.updateStatus(1L, 0);

        verify(spuMapper).updateById(org.mockito.Mockito.<Spu>argThat(spu -> spu.getStatus() == 0));
        verify(eventPublisher).publishEvent(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SPU_CHANGED, 1L, null, true));
    }

    @Test
    void testDeleteSpu() {
        when(spuMapper.selectById(1L)).thenReturn(spu1);

        spuService.deleteSpu(1L);

        verify(spuMapper).updateById(org.mockito.Mockito.<Spu>argThat(spu -> spu.getDeleteTime() > 0));
        verify(eventPublisher).publishEvent(new ProductCacheInvalidationEvent(
                ProductCacheInvalidationEvent.Type.SPU_CHANGED, 1L, null, true));
    }

    @Test
    void testGetTotalCount() {
        when(spuMapper.selectCount(any())).thenReturn(10L);

        Long count = spuService.getTotalCount();

        assertThat(count).isEqualTo(10L);
    }

    private void stubProductListCache() {
        when(productCacheService.getProductList(any(), anyInt(), anyInt(), anyInt(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<ProductListResult> supplier = inv.getArgument(4);
                    return supplier.get();
                });
    }

    private void stubProductDetailCache() {
        when(productCacheService.getProductDetail(anyLong(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<ProductDetailCache> supplier = inv.getArgument(1);
                    return supplier.get();
                });
    }
}
