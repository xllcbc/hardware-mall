package com.example.mystore.service.impl;

import com.example.mystore.common.cache.BatchCacheLoader;
import com.example.mystore.common.cache.CachePolicy;
import com.example.mystore.common.constant.ProductCacheConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.exception.ProductCacheInvalidationException;
import com.example.mystore.common.metrics.ProductCacheMetrics;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@Slf4j
public class ProductCacheServiceImpl implements ProductCacheService {

    private final RedisUtil redisUtil;
    private final BatchCacheLoader batchCacheLoader;
    private final ProductCacheKeys productCacheKeys;

    @Value("${product.cache.lock-wait-ms:500}")
    private long lockWaitMillis = ProductCacheConstants.DEFAULT_LOCK_WAIT_MILLIS;

    public ProductCacheServiceImpl(RedisUtil redisUtil, BatchCacheLoader batchCacheLoader) {
        this.redisUtil = redisUtil;
        this.batchCacheLoader = batchCacheLoader;
        this.productCacheKeys = new ProductCacheKeys(redisUtil);
    }

    @Override
    public ProductListResult getProductList(Long categoryId, int page, int limit, int status,
                                            Supplier<ProductListResult> loader) {
        return queryProductList(
                productCacheKeys.listKey(categoryId, page, limit, status), loader);
    }

    @Override
    public ProductListResult searchProducts(String keyword, Long categoryId, int page, int limit, int status,
                                            Supplier<ProductListResult> loader) {
        return queryProductList(
                productCacheKeys.searchKey(keyword, categoryId, page, limit, status), loader);
    }

    @Override
    public void invalidateAfterSkuChange(Long spuId, Long skuId) {
        invalidateAfterSkuBatchChange(spuId, Collections.singletonList(skuId));
    }

    @Override
    public void invalidateAfterSkuBatchChange(Long spuId, Collection<Long> skuIds) {
        String previousGeneration = productCacheKeys.currentGeneration();
        productCacheKeys.rotateGeneration();
        List<ProductCacheInvalidationException.Failure> failures = new ArrayList<>();
        if (skuIds != null && !skuIds.isEmpty()) {
            List<String> skuInfoKeys = skuIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(id -> productCacheKeys.skuInfoKey(id, previousGeneration))
                    .toList();
            for (String key : skuInfoKeys) {
                evictKey(key, failures);
            }
        }
        invalidateProductAggregates(spuId, true, previousGeneration, failures);
        throwIfInvalidationFailed(failures);
    }

    @Override
    public void invalidateAfterSpuChange(Long spuId, boolean recommendChanged) {
        String previousGeneration = productCacheKeys.currentGeneration();
        productCacheKeys.rotateGeneration();
        List<ProductCacheInvalidationException.Failure> failures = new ArrayList<>();
        invalidateProductAggregates(spuId, recommendChanged, previousGeneration, failures);
        throwIfInvalidationFailed(failures);
    }

    private void invalidateProductAggregates(Long spuId, boolean recommendChanged,
                                             String previousGeneration,
                                             List<ProductCacheInvalidationException.Failure> failures) {
        evictKey(productCacheKeys.detailKey(spuId, previousGeneration), failures);
        if (recommendChanged) {
            evictKey(productCacheKeys.recommendKey(previousGeneration), failures);
        }
        evictByPattern(productCacheKeys.listPattern(), failures);
        evictByPattern(productCacheKeys.searchPattern(), failures);
    }

    @Override
    public ProductDetailCache getProductDetail(Long spuId, Supplier<ProductDetailCache> loader) {
        return redisUtil.queryWithCache(
                productCacheKeys.detailKey(spuId),
                ProductDetailCache.class,
                ProductCacheConstants.TTL_SECONDS,
                RedisConstants.CACHE_NULL_TTL,
                ProductCacheConstants.JITTER_SECONDS,
                lockWaitMillis,
                loader);
    }

    @Override
    public ProductListResult getRecommend(Supplier<ProductListResult> loader) {
        return redisUtil.queryWithCache(
                productCacheKeys.recommendKey(),
                ProductListResult.class,
                ProductCacheConstants.TTL_SECONDS,
                RedisConstants.CACHE_NULL_TTL,
                ProductCacheConstants.JITTER_SECONDS,
                lockWaitMillis,
                loader);
    }

    @Override
    public List<Sku> getSkusByIds(List<Long> skuIds, Function<List<Long>, List<Sku>> loader) {
        return batchCacheLoader.load(skuIds,
                new SkuInfoCacheCell(productCacheKeys, skuInfoPolicy(), loader));
    }

    private CachePolicy skuInfoPolicy() {
        return new CachePolicy(
                ProductCacheConstants.TTL_SECONDS,
                ProductCacheConstants.JITTER_SECONDS,
                RedisConstants.CACHE_NULL_TTL,
                lockWaitMillis);
    }

    private ProductListResult queryProductList(String key, Supplier<ProductListResult> loader) {
        return redisUtil.queryWithCache(
                key,
                ProductListResult.class,
                ProductCacheConstants.TTL_SECONDS,
                RedisConstants.CACHE_NULL_TTL,
                ProductCacheConstants.JITTER_SECONDS,
                lockWaitMillis,
                loader);
    }

    private void evictKey(String key, List<ProductCacheInvalidationException.Failure> failures) {
        try {
            if (!redisUtil.evictWithLock(key, lockWaitMillis)) {
                failures.add(new ProductCacheInvalidationException.Failure(
                        key, ProductCacheMetrics.CAUSE_LOCK_TIMEOUT));
            }
        } catch (RuntimeException e) {
            log.warn("商品缓存 key 失效异常, key={}", key, e);
            failures.add(new ProductCacheInvalidationException.Failure(
                    key, ProductCacheMetrics.CAUSE_REDIS_ERROR));
        }
    }

    private void evictByPattern(String pattern,
                                List<ProductCacheInvalidationException.Failure> failures) {
        try {
            RedisUtil.PatternEvictionResult result =
                    redisUtil.evictByPatternWithLockDetailed(pattern, lockWaitMillis);
            result.failedKeys().forEach(key -> failures.add(
                    new ProductCacheInvalidationException.Failure(
                            key, ProductCacheMetrics.CAUSE_LOCK_TIMEOUT)));
        } catch (RuntimeException e) {
            log.warn("商品缓存 pattern 失效异常, pattern={}", pattern, e);
            failures.add(new ProductCacheInvalidationException.Failure(
                    pattern, ProductCacheMetrics.CAUSE_REDIS_ERROR));
        }
    }

    private void throwIfInvalidationFailed(List<ProductCacheInvalidationException.Failure> failures) {
        if (!failures.isEmpty()) {
            throw new ProductCacheInvalidationException(failures);
        }
    }
}
