package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCacheServiceImplTest {

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private ProductCacheServiceImpl productCacheService;

    @Test
    void getProductList_usesSharedListCacheKey() {
        ProductListResult expected = new ProductListResult(Collections.emptyList(), 0);
        when(redisUtil.queryWithCache(any(), eq(ProductListResult.class), eq(RedisConstants.CACHE_TTL_HOUR), any()))
                .thenReturn(expected);

        ProductListResult result = productCacheService.getProductList(7L, 2, 20, 1, () -> expected);

        assertThat(result).isSameAs(expected);
        verify(redisUtil).queryWithCache(
                eq(RedisConstants.PREFIX_PRODUCT_LIST + "7:2:20:1"),
                eq(ProductListResult.class),
                eq(RedisConstants.CACHE_TTL_HOUR),
                any());
    }

    @Test
    void searchProducts_normalizesKeywordToStableCacheKey() {
        ProductListResult expected = new ProductListResult(Collections.emptyList(), 0);
        when(redisUtil.queryWithCache(
                any(),
                eq(ProductListResult.class),
                eq(RedisConstants.PRODUCT_SEARCH_TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(RedisConstants.PRODUCT_SEARCH_JITTER_SECONDS),
                any())).thenReturn(expected);

        ProductListResult result = productCacheService.searchProducts(
                "  Door Lock  ", 7L, 2, 20, 1, () -> expected);

        assertThat(result).isSameAs(expected);
        verify(redisUtil).queryWithCache(
                eq(RedisConstants.PREFIX_PRODUCT_SEARCH
                        + "343fa871f168dcb21aed1b140a969348b311c4e065854277095ecf8765c77a20"
                        + ":7:2:20:1"),
                eq(ProductListResult.class),
                eq(RedisConstants.PRODUCT_SEARCH_TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(RedisConstants.PRODUCT_SEARCH_JITTER_SECONDS),
                any());
    }

    @Test
    void invalidateAfterSkuChange_evictsAllDependentProductCaches() {
        productCacheService.invalidateAfterSkuChange(11L, 22L);

        verify(redisUtil).delete(RedisConstants.PREFIX_SKU_INFO + 22L);
        verify(redisUtil).delete(RedisConstants.PREFIX_PRODUCT_DETAIL + 11L);
        verify(redisUtil).delete(RedisConstants.PREFIX_PRODUCT_RECOMMEND);
        verify(redisUtil).deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        verify(redisUtil).deleteByPattern(RedisConstants.PREFIX_PRODUCT_SEARCH + "*");
    }

    @Test
    void invalidateAfterSpuChange_keepsRecommendCacheWhenFlagUnchanged() {
        productCacheService.invalidateAfterSpuChange(11L, false);

        verify(redisUtil).delete(RedisConstants.PREFIX_PRODUCT_DETAIL + 11L);
        verify(redisUtil).deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        verify(redisUtil).deleteByPattern(RedisConstants.PREFIX_PRODUCT_SEARCH + "*");
        verify(redisUtil, never()).delete(RedisConstants.PREFIX_PRODUCT_RECOMMEND);
    }

    @Test
    void getProductDetail_usesSharedDetailCacheKey() {
        ProductDetailCache expected = new ProductDetailCache();
        when(redisUtil.queryWithCache(
                eq(RedisConstants.PREFIX_PRODUCT_DETAIL + 11L),
                eq(ProductDetailCache.class),
                eq(RedisConstants.CACHE_TTL_HOUR),
                any())).thenReturn(expected);

        ProductDetailCache result = productCacheService.getProductDetail(11L, () -> expected);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getRecommend_usesSharedRecommendCacheKey() {
        ProductListResult expected = new ProductListResult(Collections.emptyList(), 0);
        when(redisUtil.queryWithCache(
                eq(RedisConstants.PREFIX_PRODUCT_RECOMMEND),
                eq(ProductListResult.class),
                eq(RedisConstants.CACHE_TTL_HOUR),
                any())).thenReturn(expected);

        ProductListResult result = productCacheService.getRecommend(() -> expected);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void invalidateAfterStockChange_deletesOnlyStockKey() {
        productCacheService.invalidateAfterStockChange(22L);

        verify(redisUtil).delete(RedisConstants.PREFIX_SKU_STOCK + 22L);
        verifyNoMoreInteractions(redisUtil);
    }

    @Test
    void getSkusByIds_usesBatchCacheWhenAllHit() {
        Sku first = sku(1L);
        Sku second = sku(2L);
        when(redisUtil.multiGet(List.of(
                RedisConstants.PREFIX_SKU_INFO + 1L,
                RedisConstants.PREFIX_SKU_INFO + 2L)))
                .thenReturn(List.of(first, second));

        List<Sku> result = productCacheService.getSkusByIds(List.of(1L, 2L), ids -> {
            throw new AssertionError("全部命中时不应回源数据库");
        });

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void getSkusByIds_loadsMissesInOneBatchAndFillsCache() {
        Sku first = sku(1L);
        Sku second = sku(2L);
        Sku third = sku(3L);
        when(redisUtil.multiGet(List.of(
                RedisConstants.PREFIX_SKU_INFO + 1L,
                RedisConstants.PREFIX_SKU_INFO + 2L,
                RedisConstants.PREFIX_SKU_INFO + 3L)))
                .thenReturn(java.util.Arrays.asList(first, null, third));

        List<Sku> result = productCacheService.getSkusByIds(
                List.of(1L, 2L, 3L),
                ids -> {
                    assertThat(ids).containsExactly(2L);
                    return List.of(second);
                });

        assertThat(result).containsExactly(first, second, third);
        verify(redisUtil).set(
                RedisConstants.PREFIX_SKU_INFO + 2L,
                second,
                RedisConstants.CACHE_TTL_HOUR,
                TimeUnit.SECONDS);
    }

    private Sku sku(Long id) {
        Sku sku = new Sku();
        sku.setId(id);
        return sku;
    }
}
