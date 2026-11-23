package com.example.mystore.service.impl;

import com.example.mystore.common.cache.CacheCell;
import com.example.mystore.common.cache.CacheKeys;
import com.example.mystore.common.cache.CachePolicy;
import com.example.mystore.entity.db.Sku;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 商品 SKU 基础信息的缓存单元：真 key 所有者 + 真策略 + 本单元解码 + 调用方传入的回源函数。
 */
class SkuInfoCacheCell implements CacheCell<Sku> {

    private final ProductCacheKeys keys;
    private final CachePolicy policy;
    private final Function<List<Long>, List<Sku>> loader;

    SkuInfoCacheCell(ProductCacheKeys keys, CachePolicy policy,
                     Function<List<Long>, List<Sku>> loader) {
        this.keys = keys;
        this.policy = policy;
        this.loader = loader;
    }

    @Override
    public CachePolicy policy() {
        return policy;
    }

    @Override
    public CacheKeys keys() {
        return keys;
    }

    @Override
    public Sku decode(Object cached) {
        return cached instanceof Sku sku ? sku : null;
    }

    @Override
    public Map<Long, Sku> loadFromSource(Collection<Long> ids) {
        List<Sku> loaded = loader.apply(new ArrayList<>(ids));
        Map<Long, Sku> loadedById = new HashMap<>();
        if (loaded != null) {
            loaded.forEach(sku -> loadedById.put(sku.getId(), sku));
        }
        return loadedById;
    }
}
