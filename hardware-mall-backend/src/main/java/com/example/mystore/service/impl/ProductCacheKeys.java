package com.example.mystore.service.impl;

import com.example.mystore.common.cache.CacheKeys;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.RedisUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/**
 * 商品缓存 namespace 的 key 所有者。
 *
 * <p>SKU 基础信息、商品详情、推荐、列表与搜索的 key，以及代际的读取与旋转都只在这里产出；
 * 回填模块与失效路径都通过它取 key，服务内不再自行拼 key。</p>
 */
class ProductCacheKeys implements CacheKeys {

    private static final String ALL_CATEGORIES = "all";

    private final RedisUtil redisUtil;

    ProductCacheKeys(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    String skuInfoKey(long skuId) {
        return skuInfoKey(skuId, currentGeneration());
    }

    String skuInfoKey(long skuId, String generation) {
        return generationPrefix(RedisConstants.PREFIX_SKU_INFO, generation) + skuId;
    }

    String detailKey(long spuId) {
        return detailKey(spuId, currentGeneration());
    }

    String detailKey(long spuId, String generation) {
        return generationPrefix(RedisConstants.PREFIX_PRODUCT_DETAIL, generation) + spuId;
    }

    String recommendKey() {
        return recommendKey(currentGeneration());
    }

    String recommendKey(String generation) {
        return RedisConstants.PREFIX_PRODUCT_RECOMMEND + generation + ":";
    }

    String listKey(Long categoryId, int page, int limit, int status) {
        return generationPrefix(RedisConstants.PREFIX_PRODUCT_LIST, currentGeneration())
                + categorySegment(categoryId) + ":" + page + ":" + limit + ":" + status;
    }

    String searchKey(String keyword, Long categoryId, int page, int limit, int status) {
        return generationPrefix(RedisConstants.PREFIX_PRODUCT_SEARCH, currentGeneration())
                + sha256Hex(normalizeKeyword(keyword))
                + ":" + categorySegment(categoryId) + ":" + page + ":" + limit + ":" + status;
    }

    String listPattern() {
        return RedisConstants.PREFIX_PRODUCT_LIST + "*";
    }

    String searchPattern() {
        return RedisConstants.PREFIX_PRODUCT_SEARCH + "*";
    }

    @Override
    public String currentKey(long id) {
        return skuInfoKey(id);
    }

    @Override
    public String keyFor(long id, String generation) {
        return skuInfoKey(id, generation);
    }

    @Override
    public String currentGeneration() {
        Object current = redisUtil.get(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION);
        if (current != null) {
            return current.toString();
        }
        String candidate = UUID.randomUUID().toString();
        redisUtil.setIfAbsent(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION, candidate);
        Object stored = redisUtil.get(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION);
        return stored == null ? candidate : stored.toString();
    }

    @Override
    public void rotateGeneration() {
        redisUtil.set(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION, UUID.randomUUID().toString());
    }

    private String generationPrefix(String prefix, String generation) {
        return prefix + generation + ":";
    }

    private String categorySegment(Long categoryId) {
        return categoryId == null ? ALL_CATEGORIES : categoryId.toString();
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
