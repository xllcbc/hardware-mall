package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpecItemMapper;
import com.example.mystore.mapper.SpecTemplateMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.OssService;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.service.SpecItemService;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class StockCacheIntegrationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void concurrentStockMissLoadsDatabaseOnce() throws Exception {
        SkuMapper skuMapper = mock(SkuMapper.class);
        SkuServiceImpl skuService = skuService(skuMapper, 500L);
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger databaseCalls = new AtomicInteger();
        when(skuMapper.selectById(77L)).thenAnswer(invocation -> {
            databaseCalls.incrementAndGet();
            loaderStarted.countDown();
            await(releaseLoader);
            return stockSku(77L, 5);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = executor.submit(() -> skuService.getStockById(77L));
            assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Integer> second = executor.submit(() -> skuService.getStockById(77L));
            Thread.sleep(100);
            assertThat(second.isDone()).isFalse();

            releaseLoader.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(5);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(5);
            assertThat(databaseCalls).hasValue(1);
            verify(skuMapper, times(1)).selectById(77L);
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void stockEvictionWaitsForRebuildLockAndDeletesFreshValue() throws Exception {
        SkuMapper skuMapper = mock(SkuMapper.class);
        SkuServiceImpl skuService = skuService(skuMapper, 1000L);
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        when(skuMapper.selectById(78L)).thenAnswer(invocation -> {
            loaderStarted.countDown();
            await(releaseLoader);
            return stockSku(78L, 9);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> reader = executor.submit(() -> skuService.getStockById(78L));
            assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> eviction = executor.submit(() -> skuService.evictStockCache(78L));
            Thread.sleep(100);
            assertThat(eviction.isDone()).isFalse();

            releaseLoader.countDown();
            assertThat(reader.get(5, TimeUnit.SECONDS)).isEqualTo(9);
            eviction.get(5, TimeUnit.SECONDS);
            assertThat(redisUtil.hasKey(RedisConstants.PREFIX_SKU_STOCK + 78L)).isFalse();
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void stockLockTimeoutFallsBackToDatabaseWithoutWritingCache() throws Exception {
        SkuMapper skuMapper = mock(SkuMapper.class);
        SkuServiceImpl skuService = skuService(skuMapper, 100L);
        when(skuMapper.selectById(79L)).thenReturn(stockSku(79L, 3));
        String stockKey = RedisConstants.PREFIX_SKU_STOCK + 79L;
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> holder = executor.submit(() -> {
            assertThat(redisLockUtil.tryLock(stockKey)).isTrue();
            lockAcquired.countDown();
            await(releaseLock);
            redisLockUtil.unlock(stockKey);
        });

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            long startedAt = System.nanoTime();
            assertThat(skuService.getStockById(79L)).isEqualTo(3);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(elapsedMillis).isBetween(50L, 1000L);
            assertThat(redisUtil.hasKey(stockKey)).isFalse();
        } finally {
            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private SkuServiceImpl skuService(SkuMapper skuMapper, long lockWaitMillis) {
        SkuServiceImpl service = new SkuServiceImpl(
                skuMapper,
                mock(SpuMapper.class),
                mock(SpecTemplateMapper.class),
                mock(SpecItemMapper.class),
                mock(SpecTemplateService.class),
                mock(SpecItemService.class),
                mock(OssService.class),
                redisUtil,
                redisLockUtil,
                mock(ProductCacheService.class),
                mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "stockLockWaitMillis", lockWaitMillis);
        return service;
    }

    private Sku stockSku(Long id, int stock) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setStock(stock);
        return sku;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待并发测试信号时被中断", e);
        }
    }
}
