package com.example.mystore.service;

import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderServiceConcurrencyTest {

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SpuMapper spuMapper;

    private final List<Long> skuIdsToClean = new ArrayList<>();
    private final List<Long> spuIdsToClean = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long id : skuIdsToClean) {
            skuMapper.deleteById(id);
        }
        for (Long id : spuIdsToClean) {
            spuMapper.deleteById(id);
        }
        skuIdsToClean.clear();
        spuIdsToClean.clear();
    }

    @Test
    void deductStock_concurrent20ThreadsOf1_only10Succeed() throws InterruptedException {
        Sku sku = new Sku();
        sku.setSpuId(1L);
        sku.setSpecs(Collections.emptyList());
        sku.setPrice(new BigDecimal("10.00"));
        sku.setStock(10);
        sku.setStatus(1);
        sku.setSpecHash("concurrent_test_hash_001");
        skuMapper.insert(sku);
        skuIdsToClean.add(sku.getId());

        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger okCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    int result = skuMapper.deductStock(sku.getId(), 1);
                    if (result > 0) {
                        okCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        Sku finalSku = skuMapper.selectById(sku.getId());
        assertThat(okCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);
        assertThat(finalSku.getStock()).isEqualTo(0);
    }

    @Test
    void incrementSalesCount_concurrent100_sumShouldBe100() throws InterruptedException {
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("test SPU");
        spu.setSalesCount(0);
        spu.setStatus(1);
        spuMapper.insert(spu);
        spuIdsToClean.add(spu.getId());

        int threads = 100;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(20);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    spuMapper.incrementSalesCount(spu.getId(), 1);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        Spu finalSpu = spuMapper.selectById(spu.getId());
        assertThat(finalSpu.getSalesCount()).isEqualTo(100);
    }
}
