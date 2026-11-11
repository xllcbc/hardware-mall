package com.example.mystore.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.DiscountConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.common.exception.IdempotencyConflictException;
import com.example.mystore.entity.db.Address;
import com.example.mystore.entity.db.Category;
import com.example.mystore.entity.db.LevelCategoryRate;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderIdempotency;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.mapper.AddressMapper;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.mapper.LevelCategoryRateMapper;
import com.example.mystore.mapper.LogisticsMapper;
import com.example.mystore.mapper.OrderIdempotencyMapper;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.impl.OrderIdempotencyServiceImpl;
import com.example.mystore.util.OrderRequestFingerprint;
import com.example.mystore.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Tag("h2")
@ActiveProfiles("test")
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderIdempotencyIntegrationTest {

    private static final long USER_ID = 7001L;
    private static final long ADDRESS_ID = 7001L;
    private static final long LOGISTICS_ID = 7001L;
    private static final long CATEGORY_ID = 7001L;
    private static final long SPU_ID = 7001L;
    private static final long SKU_ID = 7001L;

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private OrderIdempotencyMapper orderIdempotencyMapper;
    @Autowired
    private AddressMapper addressMapper;
    @Autowired
    private LogisticsMapper logisticsMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private SpuMapper spuMapper;
    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private LevelCategoryRateMapper levelCategoryRateMapper;

    @SpyBean
    private OrderIdempotencyServiceImpl orderIdempotencyServiceSpy;

    @BeforeEach
    void setUp() {
        cleanup();
        UserContext.setLevelId(1L);

        Category category = new Category();
        category.setId(CATEGORY_ID);
        category.setName("幂等测试分类");
        category.setSortOrder(10);
        category.setStatus(1);
        category.setDeleteTime(0L);
        categoryMapper.insert(category);

        LevelCategoryRate rate = new LevelCategoryRate();
        rate.setLevelId(1L);
        rate.setCategoryId(CATEGORY_ID);
        rate.setRate(BigDecimal.ONE);
        levelCategoryRateMapper.insert(rate);

        Spu spu = new Spu();
        spu.setId(SPU_ID);
        spu.setCategoryId(CATEGORY_ID);
        spu.setName("幂等测试商品");
        spu.setOriginalPrice(new BigDecimal("10.00"));
        spu.setSalesCount(0);
        spu.setStatus(1);
        spu.setIsRecommend(0);
        spu.setExcludeDiscount(DiscountConstants.DISCOUNT_PARTICIPATION_ENABLED);
        spu.setDeleteTime(0L);
        spuMapper.insert(spu);

        Sku sku = new Sku();
        sku.setId(SKU_ID);
        sku.setSpuId(SPU_ID);
        sku.setSpecs(Collections.emptyList());
        sku.setPrice(new BigDecimal("10.00"));
        sku.setStock(10);
        sku.setStatus(1);
        sku.setSpecHash("idempotency-test");
        sku.setDeleteTime(0L);
        skuMapper.insert(sku);

        Address address = new Address();
        address.setId(ADDRESS_ID);
        address.setUserId(USER_ID);
        address.setConsignee("测试用户");
        address.setPhone("13800007001");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园");
        addressMapper.insert(address);

        Logistics logistics = new Logistics();
        logistics.setId(LOGISTICS_ID);
        logistics.setName("测试物流");
        logistics.setCode("idempotency-test");
        logistics.setStatus(1);
        logisticsMapper.insert(logistics);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        cleanup();
    }

    @Test
    void committedOrderWithVoFailure_isReplayedBySameKeyAndFingerprint() {
        CreateOrderRequest request = request(2);
        Order original = new Order();
        original.setOrderNo("SO-VO-FAILED");
        original.setUserId(USER_ID);
        original.setAddressId(ADDRESS_ID);
        original.setLogisticsId(LOGISTICS_ID);
        original.setStatus(StatusConstants.ORDER_PENDING_PAYMENT);
        original.setTotalAmount(new BigDecimal("20.00"));
        original.setFreightAmount(BigDecimal.ZERO);
        original.setPayAmount(new BigDecimal("20.00"));
        original.setCreateTime(LocalDateTime.now());
        original.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(original);

        OrderIdempotency succeeded = new OrderIdempotency();
        succeeded.setUserId(USER_ID);
        succeeded.setIdemKey("vo-failure-key");
        succeeded.setRequestHash(OrderRequestFingerprint.fingerprint(request));
        succeeded.setState(OrderIdempotency.STATE_SUCCEEDED);
        succeeded.setOwnerToken("previous-owner");
        succeeded.setOrderId(original.getId());
        succeeded.setCreateTime(LocalDateTime.now());
        succeeded.setUpdateTime(LocalDateTime.now());
        orderIdempotencyMapper.insert(succeeded);

        OrderVO retried = orderService.createOrder(USER_ID, request, "vo-failure-key");

        assertThat(retried.getId()).isEqualTo(original.getId());
        assertThat(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, USER_ID))).isEqualTo(1L);
        assertThat(orderIdempotencyMapper.selectCount(new LambdaQueryWrapper<OrderIdempotency>()
                .eq(OrderIdempotency::getUserId, USER_ID))).isEqualTo(1L);
    }

    @Test
    void markSucceededRunsInsideOrderTransaction() {
        AtomicBoolean inTransaction = new AtomicBoolean(false);
        doAnswer(invocation -> {
            inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(orderIdempotencyServiceSpy).markSucceeded(any(), any(), any());

        orderService.createOrder(USER_ID, request(2), "same-tx-key");

        assertThat(inTransaction).isTrue();
    }

    @Test
    void markSucceededFailureRollsBackCreatedOrder() {
        doThrow(new BusinessException("幂等记录状态已变更，请重试"))
                .when(orderIdempotencyServiceSpy).markSucceeded(any(), any(), any());

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, request(2), "rollback-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("幂等记录状态已变更，请重试");

        assertThat(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, USER_ID))).isZero();
        assertThat(orderIdempotencyMapper.selectByUserAndKey(USER_ID, "rollback-key").getState())
                .isEqualTo(OrderIdempotency.STATE_FAILED);
    }

    @Test
    void sameKeyDifferentPayload_conflictsAndKeepsOriginalOrder() {
        OrderVO original = orderService.createOrder(USER_ID, request(2), "conflict-key");
        assertThat(original.getId()).isNotNull();

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, request(3), "conflict-key"))
                .isInstanceOfSatisfying(IdempotencyConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(IdempotencyConflictException.CODE);
                    assertThat(e).hasMessage("幂等键已用于其他请求，请刷新后重试");
                });

        assertThat(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, USER_ID))).isEqualTo(1L);
    }

    @Test
    void failedCreation_marksRecordFailedAndAllowsSameKeyRetry() {
        Address address = addressMapper.selectById(ADDRESS_ID);
        address.setUserId(9999L);
        addressMapper.updateById(address);

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, request(2), "retry-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收货地址不存在");

        OrderIdempotency failed = orderIdempotencyMapper.selectByUserAndKey(USER_ID, "retry-key");
        assertThat(failed).isNotNull();
        assertThat(failed.getState()).isEqualTo(OrderIdempotency.STATE_FAILED);

        address.setUserId(USER_ID);
        addressMapper.updateById(address);

        OrderVO retried = orderService.createOrder(USER_ID, request(2), "retry-key");

        assertThat(retried.getId()).isNotNull();
        assertThat(orderIdempotencyMapper.selectByUserAndKey(USER_ID, "retry-key").getState())
                .isEqualTo(OrderIdempotency.STATE_SUCCEEDED);
        assertThat(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, USER_ID))).isEqualTo(1L);
    }

    private CreateOrderRequest request(int quantity) {
        CreateOrderRequest.CartItem item = new CreateOrderRequest.CartItem();
        item.setSkuId(SKU_ID);
        item.setQuantity(quantity);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));
        request.setAddressId(ADDRESS_ID);
        request.setLogisticsId(LOGISTICS_ID);
        request.setExpectedPayAmount(new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity)));
        return request;
    }

    private void cleanup() {
        List<Long> orderIds = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, USER_ID))
                .stream().map(Order::getId).toList();
        if (!orderIds.isEmpty()) {
            orderItemMapper.delete(new LambdaQueryWrapper<OrderItem>()
                    .in(OrderItem::getOrderId, orderIds));
        }
        orderMapper.delete(new LambdaQueryWrapper<Order>().eq(Order::getUserId, USER_ID));
        orderIdempotencyMapper.delete(new LambdaQueryWrapper<OrderIdempotency>()
                .eq(OrderIdempotency::getUserId, USER_ID));
        addressMapper.deleteById(ADDRESS_ID);
        logisticsMapper.deleteById(LOGISTICS_ID);
        skuMapper.deleteById(SKU_ID);
        spuMapper.deleteById(SPU_ID);
        levelCategoryRateMapper.delete(new LambdaQueryWrapper<LevelCategoryRate>()
                .eq(LevelCategoryRate::getCategoryId, CATEGORY_ID));
        categoryMapper.deleteById(CATEGORY_ID);
    }
}
