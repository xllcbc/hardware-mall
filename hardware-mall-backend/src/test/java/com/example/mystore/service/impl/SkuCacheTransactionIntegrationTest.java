package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.dto.SaveSkuConfigRequest;
import com.example.mystore.entity.dto.SaveSkuRowRequest;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.SpuService;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@Sql(scripts = {"/db/schema-test.sql", "/db/data-test.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SkuCacheTransactionIntegrationTest {

    private static final String GENERATION = "test-generation";

    @Autowired
    private SkuService skuService;

    @Autowired
    private SpuService spuService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private final List<Long> skuIds = new ArrayList<>();
    private final List<Long> spuIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        redisUtil.deleteByPattern("*");
        redisUtil.set(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION, GENERATION);
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        if (!skuIds.isEmpty()) {
            skuMapper.deleteBatchIds(skuIds);
        }
        if (!spuIds.isEmpty()) {
            spuMapper.deleteBatchIds(spuIds);
        }
        skuIds.clear();
        spuIds.clear();
    }

    @Test
    void rollbackDoesNotDeleteProductCachesOrWriteStockCache() {
        Spu spu = createSpu();
        Sku sku = createSku(spu.getId(), "legacy-default", 3);
        String detailKey = productKey(RedisConstants.PREFIX_PRODUCT_DETAIL, spu.getId());
        String listKey = RedisConstants.PREFIX_PRODUCT_LIST + GENERATION + ":all:1:10:1";
        String skuInfoKey = productKey(RedisConstants.PREFIX_SKU_INFO, sku.getId());
        String stockKey = RedisConstants.PREFIX_SKU_STOCK + sku.getId();
        redisUtil.set(detailKey, "old-detail", 60, TimeUnit.SECONDS);
        redisUtil.set(listKey, "old-list", 60, TimeUnit.SECONDS);
        redisUtil.set(skuInfoKey, "old-sku", 60, TimeUnit.SECONDS);
        redisUtil.set(stockKey, 3, 60, TimeUnit.SECONDS);

        transactionTemplate.executeWithoutResult(status -> {
            skuService.saveSkuConfig(spu.getId(), configRequest(sku.getId(), 9));
            status.setRollbackOnly();
        });

        assertThat(redisUtil.get(detailKey)).isEqualTo("old-detail");
        assertThat(redisUtil.get(listKey)).isEqualTo("old-list");
        assertThat(redisUtil.get(skuInfoKey)).isEqualTo("old-sku");
        assertThat(redisUtil.get(stockKey)).isEqualTo(3);
    }

    @Test
    void commitInvalidatesAfterCommitAndConcurrentMissesRefillCommittedStock() throws Exception {
        Spu spu = createSpu();
        Sku sku = createSku(spu.getId(), "legacy-commit", 3);
        String detailKey = productKey(RedisConstants.PREFIX_PRODUCT_DETAIL, spu.getId());
        String listKey = RedisConstants.PREFIX_PRODUCT_LIST + GENERATION + ":all:1:10:1";
        String skuInfoKey = productKey(RedisConstants.PREFIX_SKU_INFO, sku.getId());
        String stockKey = RedisConstants.PREFIX_SKU_STOCK + sku.getId();
        redisUtil.set(detailKey, "old-detail", 60, TimeUnit.SECONDS);
        redisUtil.set(listKey, "old-list", 60, TimeUnit.SECONDS);
        redisUtil.set(skuInfoKey, "old-sku", 60, TimeUnit.SECONDS);
        redisUtil.set(stockKey, 3, 60, TimeUnit.SECONDS);

        transactionTemplate.executeWithoutResult(status -> {
            skuService.saveSkuConfig(spu.getId(), configRequest(sku.getId(), 9));
            assertThat(redisUtil.hasKey(detailKey)).isTrue();
            assertThat(redisUtil.hasKey(listKey)).isTrue();
            assertThat(redisUtil.hasKey(skuInfoKey)).isTrue();
            assertThat(redisUtil.get(stockKey)).isEqualTo(3);
        });

        assertThat(redisUtil.hasKey(detailKey)).isFalse();
        assertThat(redisUtil.hasKey(listKey)).isFalse();
        assertThat(redisUtil.hasKey(skuInfoKey)).isFalse();
        awaitMissing(stockKey);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> readStockAfter(start, sku.getId()));
            Future<Integer> second = executor.submit(() -> readStockAfter(start, sku.getId()));
            start.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(9);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(9);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(redisUtil.get(stockKey)).isEqualTo(9);
        assertThat(redisUtil.getExpire(stockKey))
                .isBetween(RedisConstants.SKU_STOCK_TTL_SECONDS - 5,
                        RedisConstants.SKU_STOCK_TTL_SECONDS);

        Sku reloaded = skuService.getSkuById(sku.getId());
        assertThat(reloaded.getPrice()).isEqualByComparingTo("12.00");
        assertThat(reloaded.getStock()).isEqualTo(9);
        assertThat(redisUtil.hasKey(
                currentProductKey(RedisConstants.PREFIX_SKU_INFO, sku.getId()))).isTrue();
    }

    @Test
    void spuWriteInvalidatesProductCachesOnlyAfterCommit() {
        Spu spu = createSpu();
        String detailKey = productKey(RedisConstants.PREFIX_PRODUCT_DETAIL, spu.getId());
        String listKey = RedisConstants.PREFIX_PRODUCT_LIST + GENERATION + ":all:1:10:1";
        String recommendKey = RedisConstants.PREFIX_PRODUCT_RECOMMEND + GENERATION + ":";
        redisUtil.set(detailKey, "old-detail", 60, TimeUnit.SECONDS);
        redisUtil.set(listKey, "old-list", 60, TimeUnit.SECONDS);
        redisUtil.set(recommendKey, "old-recommend", 60, TimeUnit.SECONDS);
        Spu update = new Spu();
        update.setId(spu.getId());
        update.setName("committed product");
        update.setIsRecommend(1);

        transactionTemplate.executeWithoutResult(status -> {
            spuService.updateSpu(update);
            assertThat(redisUtil.get(detailKey)).isEqualTo("old-detail");
            assertThat(redisUtil.get(listKey)).isEqualTo("old-list");
            assertThat(redisUtil.get(recommendKey)).isEqualTo("old-recommend");
            status.setRollbackOnly();
        });

        assertThat(redisUtil.get(detailKey)).isEqualTo("old-detail");
        assertThat(redisUtil.get(listKey)).isEqualTo("old-list");
        assertThat(redisUtil.get(recommendKey)).isEqualTo("old-recommend");

        transactionTemplate.executeWithoutResult(status -> spuService.updateSpu(update));

        assertThat(redisUtil.hasKey(detailKey)).isFalse();
        assertThat(redisUtil.hasKey(listKey)).isFalse();
        assertThat(redisUtil.hasKey(recommendKey)).isFalse();
    }

    private Spu createSpu() {
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("cache transaction product");
        spu.setOriginalPrice(new BigDecimal("10.00"));
        spu.setSalesCount(0);
        spu.setStatus(1);
        spu.setIsRecommend(0);
        spu.setExcludeDiscount(0);
        spu.setDeleteTime(0L);
        spuMapper.insert(spu);
        spuIds.add(spu.getId());
        return spu;
    }

    private Sku createSku(Long spuId, String specHash, int stock) {
        Sku sku = new Sku();
        sku.setSpuId(spuId);
        sku.setSpecs(List.of(legacySpec()));
        sku.setPrice(new BigDecimal("10.00"));
        sku.setStock(stock);
        sku.setStatus(1);
        sku.setSpecHash(specHash);
        sku.setDeleteTime(0L);
        skuMapper.insert(sku);
        skuIds.add(sku.getId());
        return sku;
    }

    private SaveSkuConfigRequest configRequest(Long skuId, int stock) {
        SaveSkuRowRequest row = new SaveSkuRowRequest();
        row.setId(skuId);
        row.setSpecs(Collections.singletonList(legacySpec()));
        row.setPrice(new BigDecimal("12.00"));
        row.setStock(stock);
        row.setStatus(1);
        SaveSkuConfigRequest request = new SaveSkuConfigRequest();
        request.setSkus(List.of(row));
        return request;
    }

    private SpecVO legacySpec() {
        SpecVO spec = new SpecVO();
        spec.setName("default");
        spec.setValue("default");
        return spec;
    }

    private Integer readStockAfter(CountDownLatch start, Long skuId) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return skuService.getStockById(skuId);
    }

    private String productKey(String prefix, Long id) {
        return prefix + GENERATION + ":" + id;
    }

    private String currentProductKey(String prefix, Long id) {
        Object generation = redisUtil.get(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION);
        return prefix + generation + ":" + id;
    }

    private void awaitMissing(String key) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (redisUtil.hasKey(key) && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(redisUtil.hasKey(key)).isFalse();
    }
}
