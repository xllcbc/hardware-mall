package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCacheServiceImplTest {

    private static final String GENERATION = "test-generation";

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    @InjectMocks
    private ProductCacheServiceImpl productCacheService;

    @BeforeEach
    void setUp() {
        when(redisUtil.get(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION))
                .thenReturn(GENERATION);
    }

    @Test
    void getProductList_usesSharedListCacheKey() {
        ProductListResult expected = new ProductListResult(Collections.emptyList(), 0);
        when(redisUtil.queryWithCache(any(), eq(ProductListResult.class), eq(RedisConstants.CACHE_TTL_HOUR), any()))
                .thenReturn(expected);

        ProductListResult result = productCacheService.getProductList(7L, 2, 20, 1, () -> expected);

        assertThat(result).isSameAs(expected);
        verify(redisUtil).queryWithCache(
                eq(RedisConstants.PREFIX_PRODUCT_LIST + GENERATION + ":7:2:20:1"),
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
                eq(RedisConstants.PREFIX_PRODUCT_SEARCH + GENERATION + ":"
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

        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":22");
        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_DETAIL + GENERATION + ":11");
        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND + GENERATION + ":");
        verify(redisUtil).evictByPatternWithLock(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        verify(redisUtil).evictByPatternWithLock(RedisConstants.PREFIX_PRODUCT_SEARCH + "*");
    }

    @Test
    void invalidateAfterSkuBatchChange_evictsSkuInfoOnceAndProductCachesOnce() {
        productCacheService.invalidateAfterSkuBatchChange(11L, List.of(22L, 23L, 22L));

        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":22");
        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":23");
        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_DETAIL + GENERATION + ":11");
        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND + GENERATION + ":");
        verify(redisUtil).evictByPatternWithLock(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        verify(redisUtil).evictByPatternWithLock(RedisConstants.PREFIX_PRODUCT_SEARCH + "*");
    }

    @Test
    void invalidateAfterSpuChange_keepsRecommendCacheWhenFlagUnchanged() {
        productCacheService.invalidateAfterSpuChange(11L, false);

        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_DETAIL + GENERATION + ":11");
        verify(redisUtil).evictByPatternWithLock(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        verify(redisUtil).evictByPatternWithLock(RedisConstants.PREFIX_PRODUCT_SEARCH + "*");
        verify(redisUtil, never()).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND + GENERATION + ":");
    }

    @Test
    void invalidateAfterSpuChange_deletesRecommendCacheWhenAffected() {
        productCacheService.invalidateAfterSpuChange(11L, true);

        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND + GENERATION + ":");
    }

    @Test
    void getProductDetail_usesSharedDetailCacheKey() {
        ProductDetailCache expected = new ProductDetailCache();
        when(redisUtil.queryWithCache(
                eq(RedisConstants.PREFIX_PRODUCT_DETAIL + GENERATION + ":11"),
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
                eq(RedisConstants.PREFIX_PRODUCT_RECOMMEND + GENERATION + ":"),
                eq(ProductListResult.class),
                eq(RedisConstants.CACHE_TTL_HOUR),
                any())).thenReturn(expected);

        ProductListResult result = productCacheService.getRecommend(() -> expected);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getSkusByIds_usesBatchCacheWhenAllHit() {
        Sku first = sku(1L);
        Sku second = sku(2L);
        when(redisUtil.multiGet(List.of(
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":1",
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":2")))
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
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":1",
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":2",
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":3")))
                .thenReturn(java.util.Arrays.asList(first, null, third));
        when(redisLockUtil.tryLock(RedisConstants.PREFIX_SKU_INFO + GENERATION + ":2"))
                .thenReturn(true);

        List<Sku> result = productCacheService.getSkusByIds(
                List.of(1L, 2L, 3L),
                ids -> {
                    assertThat(ids).containsExactly(2L);
                    return List.of(second);
                });

        assertThat(result).containsExactly(first, second, third);
        verify(redisUtil).set(
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":2",
                second,
                RedisConstants.CACHE_TTL_HOUR,
                TimeUnit.SECONDS);
        verify(redisLockUtil).unlock(RedisConstants.PREFIX_SKU_INFO + GENERATION + ":2");
    }

    @Test
    void getSkusByIds_cachesNullSentinelForMissingIds() {
        when(redisUtil.multiGet(List.of(RedisConstants.PREFIX_SKU_INFO + GENERATION + ":9")))
                .thenReturn(Collections.singletonList(null));
        when(redisLockUtil.tryLock(RedisConstants.PREFIX_SKU_INFO + GENERATION + ":9"))
                .thenReturn(true);

        List<Sku> result = productCacheService.getSkusByIds(
                List.of(9L),
                ids -> Collections.emptyList());

        assertThat(result).containsExactly((Sku) null);
        verify(redisUtil).set(
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":9",
                RedisConstants.CACHE_NULL,
                RedisConstants.CACHE_NULL_TTL,
                TimeUnit.SECONDS);
        verify(redisLockUtil).unlock(RedisConstants.PREFIX_SKU_INFO + GENERATION + ":9");
    }

    @Test
    void getSkusByIds_nullSentinelHit_preservesNullPlaceholderWithoutLoading() {
        when(redisUtil.multiGet(List.of(RedisConstants.PREFIX_SKU_INFO + GENERATION + ":9")))
                .thenReturn(Collections.singletonList(RedisConstants.CACHE_NULL));
        when(redisUtil.isNull(RedisConstants.CACHE_NULL)).thenReturn(true);

        List<Sku> result = productCacheService.getSkusByIds(List.of(9L), ids -> {
            throw new AssertionError("空值哨兵命中时不应回源数据库");
        });

        assertThat(result).containsExactly((Sku) null);
        verify(redisUtil, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void getSkusByIds_lockUnavailable_returnsDbDataWithoutWritingCache() {
        when(redisUtil.multiGet(List.of(RedisConstants.PREFIX_SKU_INFO + GENERATION + ":9")))
                .thenReturn(Collections.singletonList(null));
        when(redisLockUtil.tryLock(RedisConstants.PREFIX_SKU_INFO + GENERATION + ":9"))
                .thenReturn(false);
        Sku loaded = sku(9L);

        List<Sku> result = productCacheService.getSkusByIds(List.of(9L), ids -> List.of(loaded));

        assertThat(result).containsExactly(loaded);
        verify(redisUtil, never()).set(any(), any(), anyLong(), any());
        verify(redisLockUtil, never()).unlock(any());
    }

    private Sku sku(Long id) {
        Sku sku = new Sku();
        sku.setId(id);
        return sku;
    }
}
