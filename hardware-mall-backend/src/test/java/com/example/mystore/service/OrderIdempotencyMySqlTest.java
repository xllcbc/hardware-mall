package com.example.mystore.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.mapper.OrderIdempotencyMapper;
import com.example.mystore.service.OrderIdempotencyService.ClaimOutcome;
import com.example.mystore.entity.db.OrderIdempotency;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.util.UserContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class OrderIdempotencyMySqlTest {

    private static final String HASH = "c".repeat(64);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("hardware_mall")
            .withUsername("test")
            .withPassword("idempotency-test")
            .withInitScript("db/init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderIdempotencyService orderIdempotencyService;
    @Autowired
    private OrderIdempotencyMapper orderIdempotencyMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentClaims_sameKeyCreateOnlyOneRecord() throws Exception {
        long userId = 7001L;
        String key = "concurrent-key";

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<ClaimOutcome>> tasks = List.of(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return orderIdempotencyService.claim(userId, key, HASH);
                },
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return orderIdempotencyService.claim(userId, key, HASH);
                });

        List<ClaimOutcome> outcomes = new ArrayList<>();
        try {
            for (Future<ClaimOutcome> future : pool.invokeAll(tasks)) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdown();
        }

        assertThat(outcomes).filteredOn(ClaimOutcome::isAcquired).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.kind() == ClaimOutcome.Kind.PROCESSING).hasSize(1);
        assertThat(orderIdempotencyMapper.selectCount(new LambdaQueryWrapper<OrderIdempotency>()
                .eq(OrderIdempotency::getUserId, userId)
                .eq(OrderIdempotency::getIdemKey, key))).isEqualTo(1L);
    }

    @Test
    void concurrentCreateOrder_sameKeyCreatesSingleOrderAndSingleStockDeduction() throws Exception {
        long userId = 7101L;
        String key = "concurrent-create-key";
        long addressId = 7101L;
        long logisticsId = 7101L;
        long categoryId = 7101L;
        long spuId = 7101L;
        long skuId = 7101L;
        seedConcurrentOrderData(userId, addressId, logisticsId, categoryId, spuId, skuId);
        try {
            CreateOrderRequest request = new CreateOrderRequest();
            CreateOrderRequest.CartItem item = new CreateOrderRequest.CartItem();
            item.setSkuId(skuId);
            item.setQuantity(2);
            request.setItems(List.of(item));
            request.setAddressId(addressId);
            request.setLogisticsId(logisticsId);
            request.setExpectedPayAmount(new java.math.BigDecimal("20.00"));

            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Callable<Object>> tasks = List.of(
                    () -> createOrderThroughBarrier(barrier, userId, request, key),
                    () -> createOrderThroughBarrier(barrier, userId, request, key));
            List<Object> outcomes = new ArrayList<>();
            try {
                for (Future<Object> future : pool.invokeAll(tasks)) {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                }
            } finally {
                pool.shutdown();
            }

            List<OrderVO> created = outcomes.stream()
                    .filter(OrderVO.class::isInstance)
                    .map(OrderVO.class::cast)
                    .toList();
            assertThat(created).as("outcomes=%s", outcomes).isNotEmpty();
            assertThat(created).extracting(OrderVO::getId).containsOnly(created.get(0).getId());
            assertThat(outcomes).allMatch(outcome -> outcome instanceof OrderVO
                    || (outcome instanceof BusinessException be
                        && be.getMessage().contains("处理中")));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shop_order WHERE user_id = ?", Long.class, userId))
                    .isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM order_idempotency WHERE user_id = ?", Long.class, userId))
                    .isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT stock FROM sku WHERE id = ?", Integer.class, skuId))
                    .isEqualTo(8);
        } finally {
            cleanupConcurrentOrderData(userId, addressId, logisticsId, categoryId, spuId, skuId);
        }
    }

    @Test
    void oldOwnerCannotOverwriteAfterExpiredTakeover() {
        long userId = 7002L;
        String key = "takeover-key";

        ClaimOutcome oldOwner = orderIdempotencyService.claim(userId, key, HASH);
        assertThat(oldOwner.isAcquired()).isTrue();
        ageRecord(oldOwner.recordId(), 11);

        ClaimOutcome newOwner = orderIdempotencyService.claim(userId, key, HASH);
        assertThat(newOwner.isAcquired()).isTrue();
        assertThat(newOwner.ownerToken()).isNotEqualTo(oldOwner.ownerToken());

        assertThatThrownBy(() -> orderIdempotencyService.markSucceeded(
                oldOwner.recordId(), oldOwner.ownerToken(), 100L))
                .isInstanceOf(BusinessException.class);

        orderIdempotencyService.markSucceeded(newOwner.recordId(), newOwner.ownerToken(), 200L);

        OrderIdempotency stored = orderIdempotencyMapper.selectByUserAndKey(userId, key);
        assertThat(stored.getState()).isEqualTo(OrderIdempotency.STATE_SUCCEEDED);
        assertThat(stored.getOwnerToken()).isEqualTo(newOwner.ownerToken());
        assertThat(stored.getOrderId()).isEqualTo(200L);
    }

    @Test
    void expiredProcessingIsReclaimedAndKeyCanBeReused() {
        long userId = 7003L;
        String key = "reclaim-key";

        ClaimOutcome first = orderIdempotencyService.claim(userId, key, HASH);
        assertThat(first.isAcquired()).isTrue();
        ageRecord(first.recordId(), 11);

        int reclaimed = orderIdempotencyService.reclaimExpiredProcessing(
                LocalDateTime.now().minusMinutes(10));

        assertThat(reclaimed).isGreaterThanOrEqualTo(1);
        assertThat(orderIdempotencyMapper.selectByUserAndKey(userId, key).getState())
                .isEqualTo(OrderIdempotency.STATE_FAILED);

        ClaimOutcome retried = orderIdempotencyService.claim(userId, key, HASH);
        assertThat(retried.isAcquired()).isTrue();
    }

    @Test
    void cleanupDeletesOnlySettledOldRecords() {
        long userId = 7004L;
        LocalDateTime eightDaysAgo = LocalDateTime.now().minusDays(8);

        OrderIdempotency oldSucceeded = record(userId, "old-succeeded",
                OrderIdempotency.STATE_SUCCEEDED, eightDaysAgo);
        OrderIdempotency oldFailed = record(userId, "old-failed",
                OrderIdempotency.STATE_FAILED, eightDaysAgo);
        OrderIdempotency recentFailed = record(userId, "recent-failed",
                OrderIdempotency.STATE_FAILED, LocalDateTime.now());
        OrderIdempotency oldProcessing = record(userId, "old-processing",
                OrderIdempotency.STATE_PROCESSING, eightDaysAgo);
        orderIdempotencyMapper.insert(oldSucceeded);
        orderIdempotencyMapper.insert(oldFailed);
        orderIdempotencyMapper.insert(recentFailed);
        orderIdempotencyMapper.insert(oldProcessing);

        int deleted = orderIdempotencyService.cleanupSettledBefore(
                LocalDateTime.now().minusDays(7));

        assertThat(deleted).isEqualTo(2);
        List<String> remaining = orderIdempotencyMapper.selectList(
                        new LambdaQueryWrapper<OrderIdempotency>()
                                .eq(OrderIdempotency::getUserId, userId))
                .stream().map(OrderIdempotency::getIdemKey).sorted().toList();
        assertThat(remaining).containsExactly("old-processing", "recent-failed");
    }

    private Object createOrderThroughBarrier(CyclicBarrier barrier, long userId,
                                             CreateOrderRequest request, String key) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        UserContext.setLevelId(1L);
        try {
            return orderService.createOrder(userId, request, key);
        } catch (RuntimeException e) {
            return e;
        } finally {
            UserContext.clear();
        }
    }

    private void seedConcurrentOrderData(long userId, long addressId, long logisticsId,
                                         long categoryId, long spuId, long skuId) {
        jdbcTemplate.update("INSERT INTO user (id, openid, nickname, role, level_id, status) "
                + "VALUES (?, ?, '并发用户', 1, 1, 1)", userId, "idem-concurrent-" + userId);
        jdbcTemplate.update("INSERT INTO address (id, user_id, consignee, phone, province, city, "
                + "district, detail, is_default) VALUES (?, ?, '并发用户', '13800007101', "
                + "'广东省', '深圳市', '南山区', '科技园', 0)", addressId, userId);
        jdbcTemplate.update("INSERT INTO category (id, parent_id, name, sort_order, status) "
                + "VALUES (?, 0, '幂等并发分类', 0, 1)", categoryId);
        jdbcTemplate.update("INSERT INTO level_category_rate (level_id, category_id, rate) "
                + "VALUES (1, ?, 1.0000)", categoryId);
        jdbcTemplate.update("INSERT INTO spu (id, category_id, name, original_price, sales_count, "
                + "status, is_recommend, exclude_discount) "
                + "VALUES (?, ?, '幂等并发商品', 10.00, 0, 1, 0, 0)", spuId, categoryId);
        jdbcTemplate.update("INSERT INTO sku (id, spu_id, specs, price, stock, status, spec_hash) "
                + "VALUES (?, ?, JSON_ARRAY(), 10.00, 10, 1, 'idem-concurrent')", skuId, spuId);
        jdbcTemplate.update("INSERT INTO logistics (id, name, code, status) "
                + "VALUES (?, '幂等并发物流', 'idem-concurrent-logistics', 1)", logisticsId);
    }

    private void cleanupConcurrentOrderData(long userId, long addressId, long logisticsId,
                                            long categoryId, long spuId, long skuId) {
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN "
                + "(SELECT id FROM shop_order WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM shop_order WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM order_idempotency WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM address WHERE id = ?", addressId);
        jdbcTemplate.update("DELETE FROM sku WHERE id = ?", skuId);
        jdbcTemplate.update("DELETE FROM spu WHERE id = ?", spuId);
        jdbcTemplate.update("DELETE FROM level_category_rate WHERE category_id = ?", categoryId);
        jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
        jdbcTemplate.update("DELETE FROM logistics WHERE id = ?", logisticsId);
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
    }

    private void ageRecord(Long recordId, int minutes) {
        jdbcTemplate.update(
                "UPDATE order_idempotency SET update_time = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(minutes), recordId);
    }

    private OrderIdempotency record(long userId, String key, String state, LocalDateTime updateTime) {
        OrderIdempotency record = new OrderIdempotency();
        record.setUserId(userId);
        record.setIdemKey(key);
        record.setRequestHash(HASH);
        record.setState(state);
        record.setOwnerToken("owner-" + key);
        record.setCreateTime(updateTime);
        record.setUpdateTime(updateTime);
        return record;
    }
}
