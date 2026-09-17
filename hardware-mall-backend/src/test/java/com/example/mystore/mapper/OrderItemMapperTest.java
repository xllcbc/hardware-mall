package com.example.mystore.mapper;

import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemMapperTest {

    @Test
    void insertBatch_persistsPriceType() throws Exception {
        Method method = OrderItemMapper.class.getMethod("insertBatch", java.util.List.class);
        String sql = String.join("", method.getAnnotation(Insert.class).value());
        assertThat(sql).contains("price_type");
        assertThat(sql).contains("#{it.priceType}");
    }
}
