package com.example.mystore.mapper;

import com.example.mystore.entity.db.Sku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Transactional
class SkuMapperTest {

    @Autowired
    private SkuMapper skuMapper;

    private Long testSkuId;

    @BeforeEach
    void setUp() {
        // 插入测试 SKU
        Sku sku = new Sku();
        sku.setSpuId(1L);
        sku.setSpecs(Collections.emptyList());
        sku.setPrice(new BigDecimal("199.00"));
        sku.setStock(50);
        sku.setStatus(1);
        sku.setSpecHash("hash_test_001");
        skuMapper.insert(sku);
        testSkuId = sku.getId();
    }

    @Test
    void testDeductStock_Success() {
        int rows = skuMapper.deductStock(testSkuId, 10);
        assertThat(rows).isEqualTo(1);

        Sku updated = skuMapper.selectById(testSkuId);
        assertThat(updated.getStock()).isEqualTo(40);
    }

    @Test
    void testDeductStock_Insufficient() {
        int rows = skuMapper.deductStock(testSkuId, 100);
        assertThat(rows).isEqualTo(0);

        Sku unchanged = skuMapper.selectById(testSkuId);
        assertThat(unchanged.getStock()).isEqualTo(50);
    }

    @Test
    void testRestoreStock() {
        skuMapper.restoreStock(testSkuId, 20);

        Sku updated = skuMapper.selectById(testSkuId);
        assertThat(updated.getStock()).isEqualTo(70);
    }

    @Test
    void testSelectById() {
        Sku sku = skuMapper.selectById(testSkuId);
        assertThat(sku).isNotNull();
        assertThat(sku.getPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        assertThat(sku.getStock()).isEqualTo(50);
    }
}
