package com.example.mystore.service.impl;

import com.example.mystore.common.cache.CachePolicy;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存缓存单元：key 由库存 key 所有者产出，库存值解码与脏值处理只在这里发生。
 */
class StockCacheCellTest {

    private final StockCacheKeys keys = new StockCacheKeys();
    private final CachePolicy policy = new CachePolicy(
            RedisConstants.SKU_STOCK_TTL_SECONDS, 0L, RedisConstants.CACHE_NULL_TTL, 500L);

    @Test
    void keysComeFromTheStockKeyOwnerWhichHasNoGeneration() {
        StockCacheCell cell = cell(ids -> List.of());

        // 用字面量断言 key 布局：库存 key 只由 key 所有者产出，且没有代际概念
        assertThat(cell.keys().currentKey(22L)).isEqualTo("sku:stock:22");
        assertThat(cell.keys().keyFor(22L, "ignored-generation")).isEqualTo("sku:stock:22");
        assertThat(cell.keys().currentGeneration()).isEqualTo(keys.currentGeneration());
        assertThat(cell.policy()).isEqualTo(policy);
    }

    @Test
    void decodesIntegerStockAndNumericText() {
        StockCacheCell cell = cell(ids -> List.of());

        assertThat(cell.decode(42)).isEqualTo(42);
        assertThat(cell.decode("42")).isEqualTo(42);
    }

    @Test
    void treatsUndecodableCachedValueAsMiss() {
        StockCacheCell cell = cell(ids -> List.of());

        assertThat(cell.decode("not-a-number")).isNull();
        assertThat(cell.decode(new Object())).isNull();
    }

    @Test
    void loadFromSourceKeepsOnlyStocksThatExistInSource() {
        StockCacheCell cell = cell(ids -> List.of(sku(1L, 5), sku(2L, null), sku(3L, 0)));

        assertThat(cell.loadFromSource(List.of(1L, 2L, 3L)))
                .containsOnly(Map.entry(1L, 5), Map.entry(3L, 0));
    }

    @Test
    void loadFromSourceTreatsNullSourceResultAsEmpty() {
        StockCacheCell cell = cell(ids -> null);

        assertThat(cell.loadFromSource(List.of(1L))).isEmpty();
    }

    private StockCacheCell cell(java.util.function.Function<java.util.Collection<Long>, List<Sku>> loader) {
        return new StockCacheCell(keys, policy, loader);
    }

    private Sku sku(Long id, Integer stock) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setStock(stock);
        return sku;
    }
}
