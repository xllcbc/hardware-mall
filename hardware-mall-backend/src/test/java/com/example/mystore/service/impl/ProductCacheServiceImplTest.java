package com.example.mystore.service.impl;

import com.example.mystore.common.cache.BatchCacheLoader;
import com.example.mystore.common.cache.CacheCell;
import com.example.mystore.common.cache.CachePolicy;
import com.example.mystore.common.constant.ProductCacheConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCacheServiceImplTest {

    private static final String GENERATION = "test-generation";

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private BatchCacheLoader batchCacheLoader;

    @InjectMocks
    private ProductCacheServiceImpl productCacheService;

    @BeforeEach
    void setUp() {
        when(redisUtil.get(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION))
                .thenReturn(GENERATION);
        ReflectionTestUtils.setField(productCacheService, "lockWaitMillis", 100L);
        lenient().when(redisUtil.evictWithLock(any(), eq(100L))).thenReturn(true);
        lenient().when(redisUtil.evictByPatternWithLockDetailed(any(), eq(100L)))
                .thenReturn(new RedisUtil.PatternEvictionResult(0, List.of()));
    }

    @Test
    void getProductList_usesSharedListCacheKey() {
        ProductListResult expected = new ProductListResult(Collections.emptyList(), 0);
        when(redisUtil.queryWithCache(
                any(),
                eq(ProductListResult.class),
                eq(ProductCacheConstants.TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(ProductCacheConstants.JITTER_SECONDS),
                eq(100L),
                any()))
                .thenReturn(expected);

        ProductListResult result = productCacheService.getProductList(7L, 2, 20, 1, () -> expected);

        assertThat(result).isSameAs(expected);
        // 用字面量断言缓存 key 版本，避免引用常量导致版本契约断言退化为恒真
        verify(redisUtil).queryWithCache(
                eq("product:list:v5:" + GENERATION + ":7:2:20:1"),
                eq(ProductListResult.class),
                eq(ProductCacheConstants.TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(ProductCacheConstants.JITTER_SECONDS),
                eq(100L),
                any());
    }

    @Test
    void searchProducts_normalizesKeywordToStableCacheKey() {
        ProductListResult expected = new ProductListResult(Collections.emptyList(), 0);
        when(redisUtil.queryWithCache(
                any(),
                eq(ProductListResult.class),
                eq(ProductCacheConstants.TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(ProductCacheConstants.JITTER_SECONDS),
                eq(100L),
                any())).thenReturn(expected);

        ProductListResult result = productCacheService.searchProducts(
                "  Door Lock  ", 7L, 2, 20, 1, () -> expected);

        assertThat(result).isSameAs(expected);
        verify(redisUtil).queryWithCache(
                eq("product:search:v3:" + GENERATION + ":"
                        + "343fa871f168dcb21aed1b140a969348b311c4e065854277095ecf8765c77a20"
                        + ":7:2:20:1"),
                eq(ProductListResult.class),
                eq(ProductCacheConstants.TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(ProductCacheConstants.JITTER_SECONDS),
                eq(100L),
                any());
    }

    @Test
    void invalidateAfterSkuChange_evictsAllDependentProductCaches() {
        productCacheService.invalidateAfterSkuChange(11L, 22L);

        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":22", 100L);
        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_DETAIL + GENERATION + ":11", 100L);
        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND + GENERATION + ":", 100L);
        verify(redisUtil).evictByPatternWithLockDetailed(
                RedisConstants.PREFIX_PRODUCT_LIST + "*", 100L);
        verify(redisUtil).evictByPatternWithLockDetailed(
                RedisConstants.PREFIX_PRODUCT_SEARCH + "*", 100L);
    }

    @Test
    void invalidateAfterSkuChange_rotatesGenerationBeforeEvictingOldGenerationKeys() {
        productCacheService.invalidateAfterSkuChange(11L, 22L);

        InOrder order = inOrder(redisUtil);
        // 先旋转代际，再按失效前读到的代际删除 key；顺序反过来会留下永久残留
        order.verify(redisUtil).set(eq(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION), any());
        order.verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":22", 100L);
        order.verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_DETAIL + GENERATION + ":11", 100L);
    }

    @Test
    void invalidateAfterSkuBatchChange_evictsSkuInfoOnceAndProductCachesOnce() {
        productCacheService.invalidateAfterSkuBatchChange(11L, List.of(22L, 23L, 22L));

        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":22", 100L);
        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_SKU_INFO + GENERATION + ":23", 100L);
        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_DETAIL + GENERATION + ":11", 100L);
        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND + GENERATION + ":", 100L);
        verify(redisUtil).evictByPatternWithLockDetailed(
                RedisConstants.PREFIX_PRODUCT_LIST + "*", 100L);
        verify(redisUtil).evictByPatternWithLockDetailed(
                RedisConstants.PREFIX_PRODUCT_SEARCH + "*", 100L);
    }

    @Test
    void invalidateAfterSpuChange_keepsRecommendCacheWhenFlagUnchanged() {
        productCacheService.invalidateAfterSpuChange(11L, false);

        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_DETAIL + GENERATION + ":11", 100L);
        verify(redisUtil).evictByPatternWithLockDetailed(
                RedisConstants.PREFIX_PRODUCT_LIST + "*", 100L);
        verify(redisUtil).evictByPatternWithLockDetailed(
                RedisConstants.PREFIX_PRODUCT_SEARCH + "*", 100L);
        verify(redisUtil, never()).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND + GENERATION + ":", 100L);
    }

    @Test
    void invalidateAfterSpuChange_deletesRecommendCacheWhenAffected() {
        productCacheService.invalidateAfterSpuChange(11L, true);

        verify(redisUtil).evictWithLock(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND + GENERATION + ":", 100L);
    }

    @Test
    void getProductDetail_usesSharedDetailCacheKey() {
        ProductDetailCache expected = new ProductDetailCache();
        when(redisUtil.queryWithCache(
                eq("product:detail:v5:" + GENERATION + ":11"),
                eq(ProductDetailCache.class),
                eq(ProductCacheConstants.TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(ProductCacheConstants.JITTER_SECONDS),
                eq(100L),
                any())).thenReturn(expected);

        ProductDetailCache result = productCacheService.getProductDetail(11L, () -> expected);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getRecommend_usesSharedRecommendCacheKey() {
        ProductListResult expected = new ProductListResult(Collections.emptyList(), 0);
        when(redisUtil.queryWithCache(
                eq("product:recommend:v5" + GENERATION + ":"),
                eq(ProductListResult.class),
                eq(ProductCacheConstants.TTL_SECONDS),
                eq(RedisConstants.CACHE_NULL_TTL),
                eq(ProductCacheConstants.JITTER_SECONDS),
                eq(100L),
                any())).thenReturn(expected);

        ProductListResult result = productCacheService.getRecommend(() -> expected);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getSkusByIds_delegatesToBatchCacheLoaderWithSkuInfoCell() {
        Sku first = sku(1L);
        List<Sku> moduleResult = List.of(first);
        when(batchCacheLoader.<Sku>load(eq(List.of(1L)), any())).thenReturn(moduleResult);

        List<Sku> result = productCacheService.getSkusByIds(List.of(1L), ids -> List.of(first));

        assertThat(result).isSameAs(moduleResult);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<CacheCell<Sku>> cellCaptor = ArgumentCaptor.forClass(CacheCell.class);
        verify(batchCacheLoader).load(eq(List.of(1L)), cellCaptor.capture());
        CacheCell<Sku> cell = cellCaptor.getValue();
        // 用字面量断言 key 布局：SKU 基础信息 key 由商品 key 所有者产出，服务内不再拼 key
        assertThat(cell.keys().currentKey(22L)).isEqualTo("sku:info:" + GENERATION + ":22");
        assertThat(cell.keys().keyFor(22L, "old-generation"))
                .isEqualTo("sku:info:old-generation:22");
        assertThat(cell.keys().currentGeneration()).isEqualTo(GENERATION);
        assertThat(cell.policy()).isEqualTo(new CachePolicy(240L, 60L, 300L, 100L));
    }

    private Sku sku(Long id) {
        Sku sku = new Sku();
        sku.setId(id);
        return sku;
    }
}
