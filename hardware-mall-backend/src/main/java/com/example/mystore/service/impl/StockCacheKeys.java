package com.example.mystore.service.impl;

import com.example.mystore.common.cache.CacheKeys;
import com.example.mystore.common.constant.RedisConstants;

/**
 * 库存缓存 namespace 的 key 所有者。
 *
 * <p>库存 key 的读取、批量填充、失效与预热都通过这里取 key，服务内不再自行拼 key，
 * 避免拼出一个永远不会被失效删除的 key。</p>
 *
 * <p>库存 key 没有代际概念：同一 SKU 的失效与回填共用同一个 key 上的锁，
 * 因此代际参数被忽略、旋转代际为空操作。</p>
 */
class StockCacheKeys implements CacheKeys {

    /**
     * 库存没有代际，固定返回同一个代际标识，供批量回填模块按角色取值。
     */
    private static final String NO_GENERATION = "";

    String stockKey(long skuId) {
        return RedisConstants.PREFIX_SKU_STOCK + skuId;
    }

    @Override
    public String currentKey(long id) {
        return stockKey(id);
    }

    @Override
    public String keyFor(long id, String generation) {
        return stockKey(id);
    }

    @Override
    public String currentGeneration() {
        return NO_GENERATION;
    }

    @Override
    public void rotateGeneration() {
        // 库存 key 不随代际失效：失效按 SKU 逐 key 带锁删除。
    }
}
