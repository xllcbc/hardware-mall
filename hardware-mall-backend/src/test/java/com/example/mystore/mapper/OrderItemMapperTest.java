package com.example.mystore.mapper;

import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemMapperTest {

    @Test
    void insertBatch_persistsLevelSnapshot() throws Exception {
        Method method = OrderItemMapper.class.getMethod("insertBatch", java.util.List.class);
        String sql = String.join("", method.getAnnotation(Insert.class).value());
        assertThat(sql).contains("level_id", "level_name", "pricing_rate");
        assertThat(sql).contains("#{it.levelId}", "#{it.levelName}", "#{it.pricingRate}");
        assertThat(sql).doesNotContain("price_type");
        assertThat(sql).doesNotContain("discount_rate");
    }
}
