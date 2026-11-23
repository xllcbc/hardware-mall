package com.example.mystore.service.impl;

import com.example.mystore.common.cache.CacheCell;
import com.example.mystore.common.cache.CacheKeys;
import com.example.mystore.common.cache.CachePolicy;
import com.example.mystore.entity.db.Sku;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 库存的缓存单元：库存 key 所有者 + 库存策略 + 库存值解码 + 调用方传入的批量回源函数。
 *
 * <p>值解码与「确认不存在的 SKU 不回源结果」的判断留在本单元，
 * 有界等待与写回协议由批量回填模块统一承担。</p>
 */
@Slf4j
class StockCacheCell implements CacheCell<Integer> {

    private final StockCacheKeys keys;
    private final CachePolicy policy;
    private final Function<Collection<Long>, List<Sku>> loader;

    StockCacheCell(StockCacheKeys keys, CachePolicy policy,
                   Function<Collection<Long>, List<Sku>> loader) {
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
    public Integer decode(Object cached) {
        if (cached == null) {
            return null;
        }
        try {
            return Integer.parseInt(cached.toString());
        } catch (NumberFormatException e) {
            log.warn("库存缓存值格式错误, value={}", cached);
            return null;
        }
    }

    /**
     * 库存为空的 SKU 视为确认不存在，由模块写入空值哨兵。
     */
    @Override
    public Map<Long, Integer> loadFromSource(Collection<Long> ids) {
        List<Sku> loaded = loader.apply(ids);
        Map<Long, Integer> stocks = new HashMap<>();
        if (loaded == null) {
            return stocks;
        }
        for (Sku sku : loaded) {
            if (sku != null && sku.getStock() != null) {
                stocks.put(sku.getId(), sku.getStock());
            }
        }
        return stocks;
    }
}
