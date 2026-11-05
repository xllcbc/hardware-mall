package com.example.mystore.service.impl;

import com.example.mystore.common.constant.ProductCacheConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.exception.ProductCacheInvalidationException;
import com.example.mystore.common.metrics.ProductCacheMetrics;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCacheServiceImpl implements ProductCacheService {

    private final RedisUtil redisUtil;
    private final RedisLockUtil redisLockUtil;

    @Value("${product.cache.lock-wait-ms:500}")
    private long lockWaitMillis = ProductCacheConstants.DEFAULT_LOCK_WAIT_MILLIS;

    @Override
    public ProductListResult getProductList(Long categoryId, int page, int limit, int status,
                                            Supplier<ProductListResult> loader) {
        String key = productCacheKey(RedisConstants.PREFIX_PRODUCT_LIST)
                + (categoryId == null ? "all" : categoryId)
                + ":" + page + ":" + limit + ":" + status;
        return queryProductList(key, loader);
    }

    @Override
    public ProductListResult searchProducts(String keyword, Long categoryId, int page, int limit, int status,
                                            Supplier<ProductListResult> loader) {
        String key = productCacheKey(RedisConstants.PREFIX_PRODUCT_SEARCH)
                + sha256Hex(normalizeKeyword(keyword))
                + ":" + (categoryId == null ? "all" : categoryId)
                + ":" + page + ":" + limit + ":" + status;
        return queryProductList(key, loader);
    }

    @Override
    public void invalidateAfterSkuChange(Long spuId, Long skuId) {
        invalidateAfterSkuBatchChange(spuId, Collections.singletonList(skuId));
    }

    @Override
    public void invalidateAfterSkuBatchChange(Long spuId, Collection<Long> skuIds) {
        String previousGeneration = productCacheGeneration();
        rotateProductCacheGeneration();
        List<ProductCacheInvalidationException.Failure> failures = new ArrayList<>();
        if (skuIds != null && !skuIds.isEmpty()) {
            List<String> skuInfoKeys = skuIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(id -> productCacheKey(RedisConstants.PREFIX_SKU_INFO, previousGeneration) + id)
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
        String previousGeneration = productCacheGeneration();
        rotateProductCacheGeneration();
        List<ProductCacheInvalidationException.Failure> failures = new ArrayList<>();
        invalidateProductAggregates(spuId, recommendChanged, previousGeneration, failures);
        throwIfInvalidationFailed(failures);
    }

    private void invalidateProductAggregates(Long spuId, boolean recommendChanged,
                                             String previousGeneration,
                                             List<ProductCacheInvalidationException.Failure> failures) {
        evictKey(productCacheKey(RedisConstants.PREFIX_PRODUCT_DETAIL, previousGeneration) + spuId,
                failures);
        if (recommendChanged) {
            evictKey(productCacheKey(RedisConstants.PREFIX_PRODUCT_RECOMMEND, previousGeneration),
                    failures);
        }
        evictByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*", failures);
        evictByPattern(RedisConstants.PREFIX_PRODUCT_SEARCH + "*", failures);
    }

    @Override
    public ProductDetailCache getProductDetail(Long spuId, Supplier<ProductDetailCache> loader) {
        return redisUtil.queryWithCache(
                productCacheKey(RedisConstants.PREFIX_PRODUCT_DETAIL) + spuId,
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
                productCacheKey(RedisConstants.PREFIX_PRODUCT_RECOMMEND),
                ProductListResult.class,
                ProductCacheConstants.TTL_SECONDS,
                RedisConstants.CACHE_NULL_TTL,
                ProductCacheConstants.JITTER_SECONDS,
                lockWaitMillis,
                loader);
    }

    @Override
    public List<Sku> getSkusByIds(List<Long> skuIds, Function<List<Long>, List<Sku>> loader) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> requestedIds = skuIds.stream()
                .filter(Objects::nonNull)
                .toList();
        if (requestedIds.isEmpty()) {
            return new ArrayList<>(Collections.nCopies(skuIds.size(), null));
        }
        String generation = productCacheGeneration();
        List<String> keys = requestedIds.stream()
                .map(id -> productCacheKey(RedisConstants.PREFIX_SKU_INFO, generation) + id)
                .toList();
        List<Object> cachedValues = redisUtil.multiGet(keys);
        List<Sku> result = new ArrayList<>(Collections.nCopies(requestedIds.size(), null));
        Set<Long> unresolvedIds = new LinkedHashSet<>();
        for (int i = 0; i < requestedIds.size(); i++) {
            Object value = cachedValues == null ? null : cachedValues.get(i);
            if (redisUtil.isNull(value)) {
                continue;
            }
            if (value instanceof Sku sku) {
                result.set(i, sku);
            } else {
                unresolvedIds.add(requestedIds.get(i));
            }
        }
        if (unresolvedIds.isEmpty()) {
            return result;
        }

        List<Long> missingIds = unresolvedIds.stream().sorted().toList();
        List<String> acquiredLockKeys = new ArrayList<>();
        long deadline = deadline(lockWaitMillis);
        try {
            for (Long id : missingIds) {
                String key = productCacheKey(RedisConstants.PREFIX_SKU_INFO, generation) + id;
                if (!redisLockUtil.tryLock(key, remainingMillis(deadline))) {
                    log.warn("批量 SKU 缓存回源等锁超时, skuId={}, maxWaitMs={}, 直接查库且不写缓存",
                            id, lockWaitMillis);
                    mergeLoadedSkus(requestedIds, result, loader.apply(missingIds),
                            false, generation);
                    return result;
                }
                acquiredLockKeys.add(key);
            }

            List<Long> stillMissing = recheckSkusAfterLock(
                    requestedIds, result, missingIds, generation);
            if (!stillMissing.isEmpty()) {
                mergeLoadedSkus(requestedIds, result, loader.apply(stillMissing), true, generation);
            }
            return result;
        } finally {
            for (int i = acquiredLockKeys.size() - 1; i >= 0; i--) {
                redisLockUtil.unlock(acquiredLockKeys.get(i));
            }
        }
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

    private List<Long> recheckSkusAfterLock(List<Long> requestedIds, List<Sku> result,
                                            List<Long> missingIds, String generation) {
        List<String> keys = missingIds.stream()
                .map(id -> productCacheKey(RedisConstants.PREFIX_SKU_INFO, generation) + id)
                .toList();
        List<Object> cachedValues = redisUtil.multiGet(keys);
        List<Long> stillMissing = new ArrayList<>();
        for (int i = 0; i < missingIds.size(); i++) {
            Long id = missingIds.get(i);
            Object value = cachedValues == null ? null : cachedValues.get(i);
            if (redisUtil.isNull(value)) {
                continue;
            }
            if (value instanceof Sku sku) {
                fillResultById(requestedIds, result, sku);
            } else {
                stillMissing.add(id);
            }
        }
        return stillMissing;
    }

    private void fillResultById(List<Long> requestedIds, List<Sku> result, Sku sku) {
        for (int i = 0; i < requestedIds.size(); i++) {
            if (Objects.equals(requestedIds.get(i), sku.getId())) {
                result.set(i, sku);
            }
        }
    }

    private void mergeLoadedSkus(List<Long> requestedIds, List<Sku> result,
                                 List<Sku> loaded, boolean writeCache, String generation) {
        Map<Long, Sku> loadedById = new HashMap<>();
        if (loaded != null) {
            loaded.forEach(sku -> loadedById.put(sku.getId(), sku));
        }
        for (int i = 0; i < requestedIds.size(); i++) {
            if (result.get(i) != null) {
                continue;
            }
            Long id = requestedIds.get(i);
            Sku loadedSku = loadedById.get(id);
            if (loadedSku != null) {
                result.set(i, loadedSku);
                if (writeCache) {
                    redisUtil.setWithJitter(
                            productCacheKey(RedisConstants.PREFIX_SKU_INFO, generation) + id,
                            loadedSku,
                            ProductCacheConstants.TTL_SECONDS,
                            TimeUnit.SECONDS,
                            ProductCacheConstants.JITTER_SECONDS);
                }
            }
        }
        if (!writeCache) {
            return;
        }
        for (int i = 0; i < requestedIds.size(); i++) {
            Long id = requestedIds.get(i);
            if (result.get(i) == null && !loadedById.containsKey(id)) {
                redisUtil.set(
                        productCacheKey(RedisConstants.PREFIX_SKU_INFO, generation) + id,
                        RedisConstants.CACHE_NULL,
                        RedisConstants.CACHE_NULL_TTL,
                        TimeUnit.SECONDS);
            }
        }
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

    private long deadline(long maxWaitMillis) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, maxWaitMillis));
    }

    private long remainingMillis(long deadline) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            return 0L;
        }
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
    }

    private String productCacheKey(String prefix) {
        return productCacheKey(prefix, productCacheGeneration());
    }

    private String productCacheKey(String prefix, String generation) {
        return prefix + generation + ":";
    }

    private String productCacheGeneration() {
        Object current = redisUtil.get(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION);
        if (current != null) {
            return current.toString();
        }
        String candidate = UUID.randomUUID().toString();
        redisUtil.setIfAbsent(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION, candidate);
        Object stored = redisUtil.get(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION);
        return stored == null ? candidate : stored.toString();
    }

    private void rotateProductCacheGeneration() {
        redisUtil.set(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION, UUID.randomUUID().toString());
    }

    private String normalizeKeyword(String keyword) {
        return Normalizer.normalize(keyword.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFKC);
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
