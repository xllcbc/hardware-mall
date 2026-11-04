package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    @Override
    public ProductListResult getProductList(Long categoryId, int page, int limit, int status,
                                            Supplier<ProductListResult> loader) {
        String key = productCacheKey(RedisConstants.PREFIX_PRODUCT_LIST)
                + (categoryId == null ? "all" : categoryId)
                + ":" + page + ":" + limit + ":" + status;
        return redisUtil.queryWithCache(key, ProductListResult.class, RedisConstants.CACHE_TTL_HOUR, loader);
    }

    @Override
    public ProductListResult searchProducts(String keyword, Long categoryId, int page, int limit, int status,
                                            Supplier<ProductListResult> loader) {
        String key = productCacheKey(RedisConstants.PREFIX_PRODUCT_SEARCH)
                + sha256Hex(normalizeKeyword(keyword))
                + ":" + (categoryId == null ? "all" : categoryId)
                + ":" + page + ":" + limit + ":" + status;
        return redisUtil.queryWithCache(
                key,
                ProductListResult.class,
                RedisConstants.PRODUCT_SEARCH_TTL_SECONDS,
                RedisConstants.CACHE_NULL_TTL,
                RedisConstants.PRODUCT_SEARCH_JITTER_SECONDS,
                loader);
    }

    @Override
    public void invalidateAfterSkuChange(Long spuId, Long skuId) {
        invalidateAfterSkuBatchChange(spuId, Collections.singletonList(skuId));
    }

    @Override
    public void invalidateAfterSkuBatchChange(Long spuId, Collection<Long> skuIds) {
        String previousGeneration = productCacheGeneration();
        rotateProductCacheGeneration();
        if (skuIds != null && !skuIds.isEmpty()) {
            List<String> skuInfoKeys = skuIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(id -> productCacheKey(RedisConstants.PREFIX_SKU_INFO, previousGeneration) + id)
                    .toList();
            for (String key : skuInfoKeys) {
                redisUtil.evictWithLock(key);
            }
        }
        invalidateProductAggregates(spuId, true, previousGeneration);
    }

    @Override
    public void invalidateAfterSpuChange(Long spuId, boolean recommendChanged) {
        String previousGeneration = productCacheGeneration();
        rotateProductCacheGeneration();
        invalidateProductAggregates(spuId, recommendChanged, previousGeneration);
    }

    private void invalidateProductAggregates(Long spuId, boolean recommendChanged,
                                             String previousGeneration) {
        redisUtil.evictWithLock(
                productCacheKey(RedisConstants.PREFIX_PRODUCT_DETAIL, previousGeneration) + spuId);
        if (recommendChanged) {
            redisUtil.evictWithLock(
                    productCacheKey(RedisConstants.PREFIX_PRODUCT_RECOMMEND, previousGeneration));
        }
        redisUtil.evictByPatternWithLock(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        redisUtil.evictByPatternWithLock(RedisConstants.PREFIX_PRODUCT_SEARCH + "*");
    }

    @Override
    public ProductDetailCache getProductDetail(Long spuId, Supplier<ProductDetailCache> loader) {
        return redisUtil.queryWithCache(
                productCacheKey(RedisConstants.PREFIX_PRODUCT_DETAIL) + spuId,
                ProductDetailCache.class,
                RedisConstants.CACHE_TTL_HOUR,
                loader);
    }

    @Override
    public ProductListResult getRecommend(Supplier<ProductListResult> loader) {
        return redisUtil.queryWithCache(
                productCacheKey(RedisConstants.PREFIX_PRODUCT_RECOMMEND),
                ProductListResult.class,
                RedisConstants.CACHE_TTL_HOUR,
                loader);
    }

    @Override
    public List<Sku> getSkusByIds(List<Long> skuIds, Function<List<Long>, List<Sku>> loader) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        String generation = productCacheGeneration();
        List<String> keys = skuIds.stream()
                .map(id -> productCacheKey(RedisConstants.PREFIX_SKU_INFO, generation) + id)
                .toList();
        List<Object> cachedValues = redisUtil.multiGet(keys);
        List<Sku> result = new ArrayList<>(Collections.nCopies(skuIds.size(), null));
        List<Long> missingIds = new ArrayList<>();
        for (int i = 0; i < skuIds.size(); i++) {
            Object value = cachedValues.get(i);
            if (redisUtil.isNull(value)) {
                continue;
            }
            if (value instanceof Sku sku) {
                result.set(i, sku);
            } else {
                missingIds.add(skuIds.get(i));
            }
        }
        if (!missingIds.isEmpty()) {
            List<String> acquiredLockKeys = new ArrayList<>();
            try {
                for (Long id : missingIds.stream().sorted().toList()) {
                    String key = productCacheKey(RedisConstants.PREFIX_SKU_INFO, generation) + id;
                    if (!redisLockUtil.tryLock(key)) {
                        log.warn("批量 SKU 缓存回源抢锁失败, skuId={}, 本次不写缓存", id);
                        mergeLoadedSkus(skuIds, result, loader.apply(missingIds),
                                false, generation);
                        return result;
                    }
                    acquiredLockKeys.add(key);
                }

                mergeLoadedSkus(skuIds, result, loader.apply(missingIds), true, generation);
            } finally {
                for (int i = acquiredLockKeys.size() - 1; i >= 0; i--) {
                    redisLockUtil.unlock(acquiredLockKeys.get(i));
                }
            }
        }
        return result;
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
                    redisUtil.set(
                                productCacheKey(RedisConstants.PREFIX_SKU_INFO, generation) + id,
                            loadedSku,
                            RedisConstants.CACHE_TTL_HOUR,
                            TimeUnit.SECONDS);
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
