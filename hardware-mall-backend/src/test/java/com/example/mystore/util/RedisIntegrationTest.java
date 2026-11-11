package com.example.mystore.util;

import com.example.mystore.common.constant.PricingCacheConstants;
import com.example.mystore.common.constant.ProductCacheConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.vo.PricingLevelCache;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.event.ProductCacheInvalidationEvent;
import com.example.mystore.event.ProductCacheInvalidationListener;
import com.example.mystore.event.StockCacheEvent;
import com.example.mystore.event.StockCacheEventListener;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.service.SkuService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class RedisIntegrationTest {

    private static final String PRODUCT_GENERATION = "test-generation";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Autowired
    private ProductCacheService productCacheService;

    @Autowired
    private ProductCacheInvalidationListener productCacheInvalidationListener;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        redisUtil.set(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION, PRODUCT_GENERATION);
    }

    @Test
    void testSetAndGet() {
        redisUtil.set("test:key", "hello");
        Object value = redisUtil.get("test:key");
        assertThat(value).isEqualTo("hello");
    }

    @Test
    void testSetWithExpiration() throws InterruptedException {
        redisUtil.set("test:expire", "value", 1, TimeUnit.SECONDS);
        assertThat(redisUtil.get("test:expire")).isEqualTo("value");

        Thread.sleep(1500);
        assertThat(redisUtil.get("test:expire")).isNull();
    }

    @Test
    void testDelete() {
        redisUtil.set("test:del", "value");
        assertThat(redisUtil.get("test:del")).isEqualTo("value");

        redisUtil.delete("test:del");
        assertThat(redisUtil.get("test:del")).isNull();
    }

    @Test
    void deleteIfValueMatches_onlyDeletesMatchingOwnerToken() {
        String key = RedisConstants.PREFIX_ORDER_IDEMPOTENCY + "1:test-owner";
        assertThat(redisUtil.setIfAbsentRaw(key, "owner-a", 300, TimeUnit.SECONDS)).isTrue();

        assertThat(redisUtil.deleteIfValueMatches(key, "owner-b")).isFalse();

        assertThat(redisUtil.deleteIfValueMatches(key, "owner-a")).isTrue();
        assertThat(redisUtil.deleteIfValueMatches(key, "owner-a")).isFalse();
    }

    @Test
    void testHasKey() {
        redisUtil.set("test:exists", "value");
        assertThat(redisUtil.hasKey("test:exists")).isTrue();
        assertThat(redisUtil.hasKey("test:not_exists")).isFalse();
    }

    @Test
    void testIncrAndDecr() {
        redisUtil.set("test:counter", 10);
        assertThat(redisUtil.incr("test:counter")).isEqualTo(11);
        assertThat(redisUtil.decr("test:counter")).isEqualTo(10);
    }

    /**
     * 回归: incrWithExpire 必须真的设上 TTL。
     * 曾经因为用 Fastjson2 value 序列化器执行 Lua, 脚本参数 "60" 变成带引号的 JSON,
     * 导致 EXPIRE 报错且仅在首次(v==1)触发 → key 永久无 TTL、限流计数只涨不清。
     */
    @Test
    void testIncrWithExpire_setsTtlOnFirstCall() {
        long current = redisUtil.incrWithExpire("test:incr-expire", 60);

        assertThat(current).isEqualTo(1);
        assertThat(redisUtil.getExpire("test:incr-expire")).isGreaterThan(0);
    }

    /** 回归: 无 TTL 的历史 key(模拟旧版本遗留)在下次自增时被兜底补上 TTL。 */
    @Test
    void testIncrWithExpire_healsKeyWithoutTtl() {
        stringRedisTemplate.opsForValue().set("test:incr-expire-heal", "5");
        // 无 TTL(原始 TTL = -1; 此版本 Spring 的 getExpire 对无 TTL 返回 0)
        assertThat(redisUtil.getExpire("test:incr-expire-heal")).isLessThanOrEqualTo(0);

        long current = redisUtil.incrWithExpire("test:incr-expire-heal", 60);

        assertThat(current).isEqualTo(6);
        assertThat(redisUtil.getExpire("test:incr-expire-heal")).isGreaterThan(0);
    }

    @Test
    void testSetWithJitter_TTLInRange() {
        redisUtil.setWithJitter("test:jitter", "value", 60, TimeUnit.SECONDS, 10);
        long expire = redisUtil.getExpire("test:jitter");
        // TTL 应该在 60 ~ 70 秒之间
        assertThat(expire).isGreaterThanOrEqualTo(55).isLessThanOrEqualTo(75);
    }

    @Test
    void testIsNull() {
        assertThat(redisUtil.isNull("NULL")).isTrue();
        assertThat(redisUtil.isNull("other")).isFalse();
        assertThat(redisUtil.isNull(null)).isFalse();
    }

    @Test
    void testSetOperations() {
        redisUtil.sAdd("test:set", "a", "b", "c");
        var members = redisUtil.sMembers("test:set", String.class);
        assertThat(members).containsExactlyInAnyOrder("a", "b", "c");

        redisUtil.sRemove("test:set", "b");
        members = redisUtil.sMembers("test:set", String.class);
        assertThat(members).containsExactlyInAnyOrder("a", "c");
    }

    @Test
    void testHashOperations() {
        redisUtil.hSet("test:hash", "field1", "value1");
        assertThat(redisUtil.hGet("test:hash", "field1")).isEqualTo("value1");

        redisUtil.hDelete("test:hash", "field1");
        assertThat(redisUtil.hGet("test:hash", "field1")).isNull();
    }

    @Test
    void evictWithLock_waitsForConcurrentOldValueRebuildThenDeletesIt() throws Exception {
        String key = "test:concurrent-rebuild";
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        CountDownLatch evictionStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> reader = executor.submit(() -> redisUtil.queryWithCache(
                key,
                String.class,
                60,
                () -> {
                    loaderStarted.countDown();
                    await(releaseLoader);
                    return "old-rate";
                }));

        try {
            assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> eviction = executor.submit(() -> {
                evictionStarted.countDown();
                redisUtil.evictWithLock(key);
            });
            assertThat(evictionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(eviction.isDone()).isFalse();

            releaseLoader.countDown();
            assertThat(reader.get(5, TimeUnit.SECONDS)).isEqualTo("old-rate");
            eviction.get(5, TimeUnit.SECONDS);

            assertThat(redisUtil.get(key)).isNull();
            assertThat(redisUtil.queryWithCache(key, String.class, 60, () -> "new-rate"))
                    .isEqualTo("new-rate");
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void evictByPatternWithLock_waitsForConcurrentRebuildThenDeletesIt() throws Exception {
        String key = RedisConstants.PREFIX_PRODUCT_LIST + "concurrent-pattern";
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> reader = executor.submit(() -> redisUtil.queryWithCache(
                key,
                String.class,
                60,
                () -> {
                    loaderStarted.countDown();
                    await(releaseLoader);
                    return "old-product-list";
                }));

        try {
            assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Long> eviction = executor.submit(() ->
                    redisUtil.evictByPatternWithLock(RedisConstants.PREFIX_PRODUCT_LIST + "*"));
            Thread.sleep(100);
            assertThat(eviction.isDone()).isFalse();

            releaseLoader.countDown();
            assertThat(reader.get(5, TimeUnit.SECONDS)).isEqualTo("old-product-list");
            assertThat(eviction.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(redisUtil.get(key)).isNull();
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void skuInfoBatchLoadIsDeletedWhenInvalidationWaitsForItsRebuildLock() throws Exception {
        Long skuId = 88L;
        String key = RedisConstants.PREFIX_SKU_INFO + PRODUCT_GENERATION + ":" + skuId;
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<List<Sku>> reader = executor.submit(() ->
                productCacheService.getSkusByIds(List.of(skuId), ids -> {
                    loaderStarted.countDown();
                    await(releaseLoader);
                    Sku sku = new Sku();
                    sku.setId(skuId);
                    return List.of(sku);
                }));

        try {
            assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> invalidation = executor.submit(() ->
                    productCacheService.invalidateAfterSkuChange(1L, skuId));
            Thread.sleep(100);
            assertThat(invalidation.isDone()).isFalse();

            releaseLoader.countDown();
            assertThat(reader.get(5, TimeUnit.SECONDS)).extracting(
                    Sku::getId).containsExactly(skuId);
            invalidation.get(5, TimeUnit.SECONDS);
            assertThat(redisUtil.get(key)).isNull();
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void skuInfoLateWriteAfterEvictionTimeoutStaysInOldGeneration() throws Exception {
        Long skuId = 89L;
        String oldKey = RedisConstants.PREFIX_SKU_INFO + PRODUCT_GENERATION + ":" + skuId;
        CountDownLatch loaderStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Sku> staleRead = executor.submit(() ->
                productCacheService.getSkusByIds(List.of(skuId), ids -> {
                    loaderStarted.countDown();
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    Sku stale = new Sku();
                    stale.setId(skuId);
                    stale.setPrice(new BigDecimal("1.00"));
                    return List.of(stale);
                }).get(0));

        try {
            assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> productCacheService.invalidateAfterSkuChange(1L, skuId))
                    .hasMessageContaining("lock_timeout");
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            assertThat(elapsedMillis).isBetween(50L, 1000L);

            assertThat(staleRead.get(5, TimeUnit.SECONDS).getPrice())
                    .isEqualByComparingTo("1.00");
            assertThat(redisUtil.hasKey(oldKey)).isTrue();
            Object currentGeneration = redisUtil.get(RedisConstants.PREFIX_PRODUCT_CACHE_GENERATION);
            assertThat(currentGeneration).isNotEqualTo(PRODUCT_GENERATION);

            Sku fresh = new Sku();
            fresh.setId(skuId);
            fresh.setPrice(new BigDecimal("2.00"));
            List<Sku> freshRead = productCacheService.getSkusByIds(
                    List.of(skuId), ids -> List.of(fresh));
            assertThat(freshRead.get(0).getPrice()).isEqualByComparingTo("2.00");
            assertThat(redisUtil.hasKey(
                    RedisConstants.PREFIX_SKU_INFO + currentGeneration + ":" + skuId)).isTrue();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void batchSkuReadReloadsCacheAfterLockAndOnlyQueriesStillMissingSkus() throws Exception {
        Long skuId = 91L;
        String key = RedisConstants.PREFIX_SKU_INFO + PRODUCT_GENERATION + ":" + skuId;
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        AtomicInteger loaderCalls = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> holder = executor.submit(() -> {
            assertThat(redisLockUtil.tryLock(key)).isTrue();
            lockAcquired.countDown();
            await(releaseLock);
            redisLockUtil.unlock(key);
        });

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            Future<List<Sku>> reader = executor.submit(() ->
                    productCacheService.getSkusByIds(List.of(skuId), ids -> {
                        loaderCalls.incrementAndGet();
                        return List.of(sku(skuId, "1.00"));
                    }));

            Thread.sleep(50);
            Sku cachedByOtherRequest = sku(skuId, "2.00");
            redisUtil.set(key, cachedByOtherRequest, 60, TimeUnit.SECONDS);
            releaseLock.countDown();

            assertThat(reader.get(5, TimeUnit.SECONDS).get(0).getPrice())
                    .isEqualByComparingTo("2.00");
            assertThat(loaderCalls).hasValue(0);
        } finally {
            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void batchSkuReadTimesOutBoundedAndDoesNotWriteCache() throws Exception {
        Long skuId = 92L;
        String key = RedisConstants.PREFIX_SKU_INFO + PRODUCT_GENERATION + ":" + skuId;
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> holder = executor.submit(() -> {
            assertThat(redisLockUtil.tryLock(key)).isTrue();
            lockAcquired.countDown();
            await(releaseLock);
            redisLockUtil.unlock(key);
        });

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            long startedAt = System.nanoTime();
            List<Sku> result = productCacheService.getSkusByIds(
                    List.of(skuId), ids -> List.of(sku(skuId, "3.00")));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(result.get(0).getPrice()).isEqualByComparingTo("3.00");
            assertThat(elapsedMillis).isBetween(50L, 1000L);
            assertThat(redisUtil.hasKey(key)).isFalse();
        } finally {
            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void productQueryLockTimeoutFallsBackToDatabaseWithoutCaching() throws Exception {
        String cacheKey = RedisConstants.PREFIX_PRODUCT_LIST + PRODUCT_GENERATION + ":all:1:20:1";
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> holder = executor.submit(() -> {
            assertThat(redisLockUtil.tryLock(cacheKey)).isTrue();
            lockAcquired.countDown();
            await(releaseLock);
            redisLockUtil.unlock(cacheKey);
        });

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            AtomicInteger loaderCalls = new AtomicInteger();
            long startedAt = System.nanoTime();
            var result = productCacheService.getProductList(
                    null, 1, 20, 1, () -> {
                        loaderCalls.incrementAndGet();
                        return new com.example.mystore.entity.vo.ProductListResult(List.of(), 0);
                    });
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(result.getTotal()).isZero();
            assertThat(loaderCalls).hasValue(1);
            assertThat(elapsedMillis).isBetween(50L, 1000L);
            assertThat(redisUtil.hasKey(cacheKey)).isFalse();
        } finally {
            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void priceRelatedProductCachesStayBelowFiveMinuteHardLimit() {
        productCacheService.getProductList(
                null, 1, 20, 1,
                () -> new com.example.mystore.entity.vo.ProductListResult(List.of(), 0));
        productCacheService.getProductDetail(1L, ProductDetailCache::new);
        productCacheService.getRecommend(
                () -> new com.example.mystore.entity.vo.ProductListResult(List.of(), 0));
        productCacheService.searchProducts(
                "drill", null, 1, 20, 1,
                () -> new com.example.mystore.entity.vo.ProductListResult(List.of(), 0));
        productCacheService.getSkusByIds(List.of(93L), ids -> List.of(sku(93L, "4.00")));

        assertThat(redisUtil.getExpire(
                RedisConstants.PREFIX_PRODUCT_LIST + PRODUCT_GENERATION + ":all:1:20:1"))
                .isBetween(ProductCacheConstants.TTL_SECONDS - 5,
                        ProductCacheConstants.MAX_STALE_SECONDS);
        assertThat(redisUtil.getExpire(
                RedisConstants.PREFIX_PRODUCT_DETAIL + PRODUCT_GENERATION + ":1"))
                .isBetween(ProductCacheConstants.TTL_SECONDS - 5,
                        ProductCacheConstants.MAX_STALE_SECONDS);
        assertThat(redisUtil.getExpire(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND + PRODUCT_GENERATION + ":"))
                .isBetween(ProductCacheConstants.TTL_SECONDS - 5,
                        ProductCacheConstants.MAX_STALE_SECONDS);
        assertThat(redisUtil.getExpire(
                RedisConstants.PREFIX_PRODUCT_SEARCH + PRODUCT_GENERATION + ":"
                        + "b76419d9ad059fdfdc06637cb4db8ac93be25deb5bdf3cd2a7bbedaffe99d014"
                        + ":all:1:20:1"))
                .isBetween(ProductCacheConstants.TTL_SECONDS - 5,
                        ProductCacheConstants.MAX_STALE_SECONDS);
        assertThat(redisUtil.getExpire(
                RedisConstants.PREFIX_SKU_INFO + PRODUCT_GENERATION + ":93"))
                .isBetween(ProductCacheConstants.TTL_SECONDS - 5,
                        ProductCacheConstants.MAX_STALE_SECONDS);
        assertThat(ProductCacheConstants.MAX_STALE_SECONDS).isEqualTo(300L);
    }

    @Test
    void productInvalidationFailureRecordsMetricWithoutFailingCaller() throws Exception {
        Long skuId = 94L;
        String skuKey = RedisConstants.PREFIX_SKU_INFO + PRODUCT_GENERATION + ":" + skuId;
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> holder = executor.submit(() -> {
            assertThat(redisLockUtil.tryLock(skuKey)).isTrue();
            lockAcquired.countDown();
            await(releaseLock);
            redisLockUtil.unlock(skuKey);
        });

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            Counter before = meterRegistry.find("product.cache.invalidation")
                    .tags("result", "failure", "cause", "lock_timeout")
                    .counter();
            double beforeCount = before == null ? 0.0 : before.count();

            assertThatCode(() -> productCacheInvalidationListener.onProductCacheInvalidation(
                    new ProductCacheInvalidationEvent(
                            ProductCacheInvalidationEvent.Type.SKU_CHANGED,
                            1L,
                            skuId,
                            false)))
                    .doesNotThrowAnyException();

            Counter after = meterRegistry.find("product.cache.invalidation")
                    .tags("result", "failure", "cause", "lock_timeout")
                    .counter();
            assertThat(after).isNotNull();
            assertThat(after.count()).isGreaterThan(beforeCount);
        } finally {
            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void queryWithCache_concurrentMissRebuildsOnceAndSharesResult() throws Exception {
        String key = RedisConstants.PREFIX_PRICING_LEVEL + "concurrent-fill";
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger loaderCalls = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        PricingLevelCache cachedRates = pricingCache("0.8000");

        try {
            Future<PricingLevelCache> first = executor.submit(() -> redisUtil.queryWithCache(
                    key,
                    PricingLevelCache.class,
                    PricingCacheConstants.TTL_SECONDS,
                    RedisConstants.CACHE_NULL_TTL,
                    PricingCacheConstants.JITTER_SECONDS,
                    500L,
                    () -> {
                        loaderCalls.incrementAndGet();
                        loaderStarted.countDown();
                        await(releaseLoader);
                        return cachedRates;
                    }));
            assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<PricingLevelCache> second = executor.submit(() -> redisUtil.queryWithCache(
                    key,
                    PricingLevelCache.class,
                    PricingCacheConstants.TTL_SECONDS,
                    RedisConstants.CACHE_NULL_TTL,
                    PricingCacheConstants.JITTER_SECONDS,
                    500L,
                    () -> {
                        loaderCalls.incrementAndGet();
                        return pricingCache("0.7000");
                    }));

            Thread.sleep(100);
            assertThat(second.isDone()).isFalse();
            releaseLoader.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).getRates())
                    .containsEntry(9L, new BigDecimal("0.8000"));
            assertThat(second.get(5, TimeUnit.SECONDS).getRates())
                    .containsEntry(9L, new BigDecimal("0.8000"));
            assertThat(loaderCalls).hasValue(1);
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void evictWithLock_returnsAfterBoundedWaitWhenLockRemainsHeld() throws Exception {
        String key = "test:bounded-eviction";
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        redisUtil.set(key, "old-rate", 60, TimeUnit.SECONDS);

        Future<?> holder = executor.submit(() -> {
            assertThat(redisUtilAcquireLock(key)).isTrue();
            lockAcquired.countDown();
            await(releaseLock);
            redisUtilReleaseLock(key);
        });

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            long startedAt = System.nanoTime();
            boolean evicted = redisUtil.evictWithLock(key, 100);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(evicted).isFalse();
            assertThat(elapsedMillis).isBetween(80L, 1000L);
            assertThat(redisUtil.get(key)).isEqualTo("old-rate");
        } finally {
            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void stockEvictionFailureIsRecordedInRedisAndLeavesFiveMinuteTtlFallback() {
        SkuService failingSkuService = mock(SkuService.class);
        doThrow(new RuntimeException("redis unavailable"))
                .when(failingSkuService)
                .evictStockCache(77L);
        StockCacheEventListener listener = new StockCacheEventListener(failingSkuService, redisUtil);
        String stockKey = RedisConstants.PREFIX_SKU_STOCK + 77L;
        redisUtil.set(stockKey, 5, RedisConstants.SKU_STOCK_TTL_SECONDS, TimeUnit.SECONDS);

        assertThatCode(() -> listener.onStockCacheEvent(new StockCacheEvent(List.of(77L))))
                .doesNotThrowAnyException();

        assertThat(redisUtil.get(stockKey)).isEqualTo(5);
        assertThat(redisUtil.getExpire(stockKey))
                .isBetween(RedisConstants.SKU_STOCK_TTL_SECONDS - 5,
                        RedisConstants.SKU_STOCK_TTL_SECONDS);
        assertThat(Boolean.TRUE.equals(redisUtil.sIsMember(
                RedisConstants.PREFIX_STOCK_EVICT_FAILED, 77L))).isTrue();
    }

    private boolean redisUtilAcquireLock(String key) {
        return redisLockUtil.tryLock(key);
    }

    private void redisUtilReleaseLock(String key) {
        redisLockUtil.unlock(key);
    }

    private PricingLevelCache pricingCache(String rate) {
        PricingLevelCache cache = new PricingLevelCache();
        cache.setRates(Map.of(9L, new BigDecimal(rate)));
        return cache;
    }

    private Sku sku(Long id, String price) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setPrice(new BigDecimal(price));
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
