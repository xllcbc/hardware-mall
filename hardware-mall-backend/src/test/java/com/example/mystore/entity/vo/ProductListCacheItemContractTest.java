package com.example.mystore.entity.vo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.support.spring6.data.redis.GenericFastJsonRedisSerializer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProductListCacheItemContractTest {

    private final GenericFastJsonRedisSerializer serializer = new GenericFastJsonRedisSerializer();

    @Test
    void cacheJsonUsesNeutralBasePriceRangeFields() {
        ProductListCacheItem item = new ProductListCacheItem();
        item.setId(1L);
        item.setBasePriceMin(new BigDecimal("10.00"));
        item.setBasePriceMax(new BigDecimal("20.00"));

        byte[] cached = serializer.serialize(item);
        String json = new String(cached, StandardCharsets.UTF_8);

        assertThat(json)
                .contains("basePriceMin")
                .contains("basePriceMax")
                .doesNotContain("retailMinPrice")
                .doesNotContain("retailMaxPrice");
        ProductListCacheItem parsed = (ProductListCacheItem) serializer.deserialize(cached);
        assertThat(parsed.getBasePriceMin()).isEqualByComparingTo("10.00");
        assertThat(parsed.getBasePriceMax()).isEqualByComparingTo("20.00");
    }

    @Test
    void legacyRetailRangeJsonDoesNotSatisfyNewContract() {
        String legacyJson = "{\"retailMinPrice\":10.00,\"retailMaxPrice\":20.00}";

        ProductListCacheItem parsed =
                JSON.parseObject(legacyJson, ProductListCacheItem.class);

        assertThat(parsed.getBasePriceMin()).isNull();
        assertThat(parsed.getBasePriceMax()).isNull();
    }
}
