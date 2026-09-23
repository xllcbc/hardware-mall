package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ProductCacheServiceImpl implements ProductCacheService {

    private final RedisUtil redisUtil;

    @Override
    public ProductListResult getProductList(Long categoryId, int page, int limit, int status,
                                            Supplier<ProductListResult> loader) {
        String key = RedisConstants.PREFIX_PRODUCT_LIST
                + (categoryId == null ? "all" : categoryId)
                + ":" + page + ":" + limit + ":" + status;
        return redisUtil.queryWithCache(key, ProductListResult.class, RedisConstants.CACHE_TTL_HOUR, loader);
    }

    @Override
    public ProductListResult searchProducts(String keyword, Long categoryId, int page, int limit, int status,
                                            Supplier<ProductListResult> loader) {
        String key = RedisConstants.PREFIX_PRODUCT_SEARCH
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
        redisUtil.delete(RedisConstants.PREFIX_SKU_INFO + skuId);
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_DETAIL + spuId);
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_RECOMMEND);
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_SEARCH + "*");
    }

    @Override
    public void invalidateAfterStockChange(Long skuId) {
        redisUtil.delete(RedisConstants.PREFIX_SKU_STOCK + skuId);
    }

    @Override
    public void invalidateAfterSpuChange(Long spuId, boolean recommendChanged) {
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_DETAIL + spuId);
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_SEARCH + "*");
        if (recommendChanged) {
            redisUtil.delete(RedisConstants.PREFIX_PRODUCT_RECOMMEND);
        }
    }

    @Override
    public ProductDetailCache getProductDetail(Long spuId, Supplier<ProductDetailCache> loader) {
        return redisUtil.queryWithCache(
                RedisConstants.PREFIX_PRODUCT_DETAIL + spuId,
                ProductDetailCache.class,
                RedisConstants.CACHE_TTL_HOUR,
                loader);
    }

    @Override
    public ProductListResult getRecommend(Supplier<ProductListResult> loader) {
        return redisUtil.queryWithCache(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND,
                ProductListResult.class,
                RedisConstants.CACHE_TTL_HOUR,
                loader);
    }

    @Override
    public List<Sku> getSkusByIds(List<Long> skuIds, Function<List<Long>, List<Sku>> loader) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keys = skuIds.stream()
                .map(id -> RedisConstants.PREFIX_SKU_INFO + id)
                .toList();
        List<Object> cachedValues = redisUtil.multiGet(keys);
        List<Sku> result = new ArrayList<>(Collections.nCopies(skuIds.size(), null));
        List<Long> missingIds = new ArrayList<>();
        for (int i = 0; i < skuIds.size(); i++) {
            Object value = cachedValues.get(i);
            if (value instanceof Sku sku && !redisUtil.isNull(value)) {
                result.set(i, sku);
            } else {
                missingIds.add(skuIds.get(i));
            }
        }
        if (!missingIds.isEmpty()) {
            Map<Long, Sku> loadedById = new HashMap<>();
            List<Sku> loaded = loader.apply(missingIds);
            if (loaded != null) {
                loaded.forEach(sku -> loadedById.put(sku.getId(), sku));
            }
            for (int i = 0; i < skuIds.size(); i++) {
                if (result.get(i) != null) {
                    continue;
                }
                Long id = skuIds.get(i);
                Sku loadedSku = loadedById.get(id);
                if (loadedSku != null) {
                    result.set(i, loadedSku);
                    redisUtil.set(
                            RedisConstants.PREFIX_SKU_INFO + id,
                            loadedSku,
                            RedisConstants.CACHE_TTL_HOUR,
                            TimeUnit.SECONDS);
                }
            }
        }
        return result;
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
