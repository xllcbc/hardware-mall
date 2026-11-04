package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.PricingCacheConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryDiscount;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.dto.LevelDiscountItemRequest;
import com.example.mystore.event.PricingCacheInvalidationEvent;
import com.example.mystore.event.PricingCacheInvalidationListener;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryDiscountMapper;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.service.UserLevelService;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = {"/db/schema-test.sql", "/db/data-test.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PricingCacheInvalidationIntegrationTest {

    @Autowired
    private PricingRuleService pricingRuleService;

    @Autowired
    private UserLevelService userLevelService;

    @Autowired
    private PricingCacheInvalidationListener invalidationListener;

    @Autowired
    private UserLevelMapper userLevelMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private LevelCategoryDiscountMapper levelCategoryDiscountMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private final List<Long> createdLevelIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        redisUtil.deleteByPattern("*");
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        if (!createdLevelIds.isEmpty()) {
            levelCategoryDiscountMapper.delete(
                    new LambdaQueryWrapper<LevelCategoryDiscount>()
                            .in(LevelCategoryDiscount::getLevelId, createdLevelIds));
            userLevelMapper.deleteBatchIds(createdLevelIds);
        }
        if (!createdCategoryIds.isEmpty()) {
            categoryMapper.deleteBatchIds(createdCategoryIds);
        }
        createdLevelIds.clear();
        createdCategoryIds.clear();
    }

    @Test
    void commitInvalidatesPricingCacheOnlyAfterDatabaseCommit() {
        PricingFixture fixture = createFixture("commit");
        String cacheKey = RedisConstants.PREFIX_PRICING_LEVEL + fixture.levelId();
        String productCacheKey = RedisConstants.PREFIX_PRODUCT_DETAIL + 77L;
        redisUtil.set(cacheKey, "old-rate", PricingCacheConstants.MAX_STALE_SECONDS, TimeUnit.SECONDS);
        redisUtil.set(productCacheKey, "neutral-product", 60, TimeUnit.SECONDS);
        double successesBefore = counter("success");

        transactionTemplate.executeWithoutResult(status -> {
            pricingRuleService.saveDiscounts(fixture.levelId(),
                    List.of(discountRequest(fixture.categoryId(), "0.6200")));
            assertThat(redisUtil.hasKey(cacheKey)).isTrue();
        });

        assertThat(redisUtil.hasKey(cacheKey)).isFalse();
        assertThat(redisUtil.hasKey(productCacheKey)).isTrue();
        assertThat(counter("success")).isEqualTo(successesBefore + 1.0);
    }

    @Test
    void rollbackDoesNotInvalidatePricingCache() {
        PricingFixture fixture = createFixture("rollback");
        String cacheKey = RedisConstants.PREFIX_PRICING_LEVEL + fixture.levelId();
        redisUtil.set(cacheKey, "old-rate", PricingCacheConstants.MAX_STALE_SECONDS, TimeUnit.SECONDS);
        double successesBefore = counter("success");
        double lockTimeoutsBefore = counter("failure", "lock_timeout");

        transactionTemplate.executeWithoutResult(status -> {
            pricingRuleService.saveDiscounts(fixture.levelId(),
                    List.of(discountRequest(fixture.categoryId(), "0.6200")));
            status.setRollbackOnly();
        });

        assertThat(redisUtil.hasKey(cacheKey)).isTrue();
        assertThat(counter("success")).isEqualTo(successesBefore);
        assertThat(counter("failure", "lock_timeout")).isEqualTo(lockTimeoutsBefore);
    }

    @Test
    void fillDiscountsInvalidatesLevelCacheAfterCommit() {
        PricingFixture fixture = createFixture("fill");
        String cacheKey = RedisConstants.PREFIX_PRICING_LEVEL + fixture.levelId();
        redisUtil.set(cacheKey, "old-rate", PricingCacheConstants.MAX_STALE_SECONDS, TimeUnit.SECONDS);

        transactionTemplate.executeWithoutResult(status ->
                pricingRuleService.fillDiscounts(fixture.levelId(), new BigDecimal("0.6600")));

        assertThat(redisUtil.hasKey(cacheKey)).isFalse();
    }

    @Test
    void copyDiscountsInvalidatesTargetLevelCacheAfterCommit() {
        PricingFixture source = createFixture("copy-source");
        PricingFixture target = createFixture("copy-target");
        String cacheKey = RedisConstants.PREFIX_PRICING_LEVEL + target.levelId();
        redisUtil.set(cacheKey, "old-rate", PricingCacheConstants.MAX_STALE_SECONDS, TimeUnit.SECONDS);

        transactionTemplate.executeWithoutResult(status ->
                pricingRuleService.copyDiscounts(target.levelId(), source.levelId()));

        assertThat(redisUtil.hasKey(cacheKey)).isFalse();
    }

    @Test
    void levelStatusChangeInvalidatesLevelAndRetailCachesAfterCommit() {
        PricingFixture fixture = createFixture("status");
        pricingRuleService.fillDiscounts(fixture.levelId(), new BigDecimal("0.8800"));
        String levelCacheKey = RedisConstants.PREFIX_PRICING_LEVEL + fixture.levelId();
        redisUtil.set(levelCacheKey, "old-rate",
                PricingCacheConstants.MAX_STALE_SECONDS, TimeUnit.SECONDS);
        redisUtil.set(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID, 1L,
                PricingCacheConstants.MAX_STALE_SECONDS, TimeUnit.SECONDS);

        transactionTemplate.executeWithoutResult(status ->
                userLevelService.updateStatus(fixture.levelId(), UserLevelStatusConstants.STATUS_ENABLED));

        assertThat(redisUtil.hasKey(levelCacheKey)).isFalse();
        assertThat(redisUtil.hasKey(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID)).isFalse();
    }

    @Test
    void levelDeletionInvalidatesLevelAndRetailCachesAfterCommit() {
        PricingFixture fixture = createFixture("delete");
        String levelCacheKey = RedisConstants.PREFIX_PRICING_LEVEL + fixture.levelId();
        redisUtil.set(levelCacheKey, "old-rate",
                PricingCacheConstants.MAX_STALE_SECONDS, TimeUnit.SECONDS);
        redisUtil.set(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID, 1L,
                PricingCacheConstants.MAX_STALE_SECONDS, TimeUnit.SECONDS);

        transactionTemplate.executeWithoutResult(status -> userLevelService.delete(fixture.levelId()));

        assertThat(redisUtil.hasKey(levelCacheKey)).isFalse();
        assertThat(redisUtil.hasKey(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID)).isFalse();
    }

    @Test
    void pricingCacheTtlStaysWithinFiveMinuteHardLimit() {
        PricingFixture fixture = createFixture("ttl");
        String levelCacheKey = RedisConstants.PREFIX_PRICING_LEVEL + fixture.levelId();
        redisUtil.delete(levelCacheKey);
        redisUtil.delete(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID);

        assertThat(pricingRuleService.rateFor(fixture.levelId(), fixture.categoryId(), false))
                .isEqualByComparingTo("0.8800");

        assertThat(redisUtil.getExpire(levelCacheKey))
                .isBetween(PricingCacheConstants.TTL_SECONDS - 5, PricingCacheConstants.MAX_STALE_SECONDS);
        assertThat(redisUtil.getExpire(RedisConstants.PREFIX_PRICING_RETAIL_LEVEL_ID))
                .isBetween(PricingCacheConstants.TTL_SECONDS - 5, PricingCacheConstants.MAX_STALE_SECONDS);
        assertThat(PricingCacheConstants.TTL_SECONDS + PricingCacheConstants.JITTER_SECONDS)
                .isEqualTo(PricingCacheConstants.MAX_STALE_SECONDS)
                .isLessThanOrEqualTo(300L);
    }

    @Test
    void lockTimeoutRecordsFailureAndNextSuccessfulEvictionRecordsRecovery() throws Exception {
        String cacheKey = RedisConstants.PREFIX_PRICING_LEVEL + 902L;
        redisUtil.set(cacheKey, "old-rate", PricingCacheConstants.MAX_STALE_SECONDS, TimeUnit.SECONDS);
        double recoveriesBefore = registryCounter("pricing.cache.invalidation.recoveries").count();
        double lockTimeoutsBefore = counter("failure", "lock_timeout");
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

            invalidationListener.onPricingCacheInvalidation(new PricingCacheInvalidationEvent(
                    PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 902L));

            assertThat(redisUtil.hasKey(cacheKey)).isTrue();
            assertThat(counter("failure", "lock_timeout")).isEqualTo(lockTimeoutsBefore + 1.0);
            assertThat(meterRegistry.get("pricing.cache.invalidation.consecutive.failures")
                    .gauge()
                    .value()).isEqualTo(1.0);

            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);

            invalidationListener.onPricingCacheInvalidation(new PricingCacheInvalidationEvent(
                    PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 902L));

            assertThat(redisUtil.hasKey(cacheKey)).isFalse();
            assertThat(registryCounter("pricing.cache.invalidation.recoveries").count())
                    .isEqualTo(recoveriesBefore + 1.0);
            assertThat(meterRegistry.get("pricing.cache.invalidation.consecutive.failures")
                    .gauge()
                    .value()).isZero();
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private PricingFixture createFixture(String prefix) {
        String suffix = prefix + "-" + System.nanoTime();
        UserLevel level = new UserLevel();
        level.setName("定价缓存测试-" + suffix);
        level.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        level.setSortOrder(0);
        level.setStatus(UserLevelStatusConstants.STATUS_DISABLED);
        userLevelMapper.insert(level);
        createdLevelIds.add(level.getId());

        Category category = new Category();
        category.setParentId(0L);
        category.setName("定价缓存分类-" + suffix);
        category.setSortOrder(0);
        category.setStatus(StatusConstants.CATEGORY_STATUS_ENABLED);
        category.setDeleteTime(0L);
        categoryMapper.insert(category);
        createdCategoryIds.add(category.getId());

        LevelCategoryDiscount discount = new LevelCategoryDiscount();
        discount.setLevelId(level.getId());
        discount.setCategoryId(category.getId());
        discount.setRate(new BigDecimal("0.8800"));
        levelCategoryDiscountMapper.insert(discount);
        return new PricingFixture(level.getId(), category.getId());
    }

    private LevelDiscountItemRequest discountRequest(Long categoryId, String rate) {
        LevelDiscountItemRequest request = new LevelDiscountItemRequest();
        request.setCategoryId(categoryId);
        request.setRate(new BigDecimal(rate));
        return request;
    }

    private double counter(String result, String cause) {
        Counter counter = meterRegistry.find("pricing.cache.invalidation")
                .tags("result", result, "cause", cause)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double counter(String result) {
        Counter counter = meterRegistry.find("pricing.cache.invalidation")
                .tag("result", result)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private Counter registryCounter(String name) {
        return meterRegistry.counter(name);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待并发测试信号时被中断", e);
        }
    }

    private record PricingFixture(Long levelId, Long categoryId) {
    }
}
