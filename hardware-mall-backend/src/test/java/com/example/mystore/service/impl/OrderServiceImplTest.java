package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.*;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.mapper.*;
import com.example.mystore.service.CartService;
import com.example.mystore.service.PayService;
import com.example.mystore.service.SkuService;
import com.example.mystore.event.StockSyncEvent;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private SkuMapper skuMapper;
    @Mock
    private SpuMapper spuMapper;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private LogisticsMapper logisticsMapper;
    @Mock
    private CartMapper cartMapper;
    @Mock
    private CartService cartService;
    @Mock
    private SkuService skuService;
    @Mock
    private PayService payService;
    @Mock
    private RedisLockUtil redisLockUtil;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Address address;
    private Logistics logistics;
    private Sku sku;
    private Spu spu;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Cart.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderItem.class);
        // LambdaUpdateWrapper.set() 立即解析列名, 单测无 Spring 上下文需手动注册 Order 元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @BeforeEach
    void setUp() {
        // payService 在实现类中是 @Autowired(required=false) 字段注入,
        // @InjectMocks 走构造器注入不会覆盖它, 需手动注入
        ReflectionTestUtils.setField(orderService, "payService", payService);

        address = new Address();
        address.setId(1L);
        address.setUserId(2L);
        address.setConsignee("张三");
        address.setPhone("13800001001");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园南路88号");

        logistics = new Logistics();
        logistics.setId(1L);
        logistics.setName("德邦物流");
        logistics.setStatus(1);

        sku = new Sku();
        sku.setId(5L);
        sku.setSpuId(1L);
        sku.setPrice(new BigDecimal("219.00"));
        sku.setStock(40);
        sku.setStatus(1);

        spu = new Spu();
        spu.setId(1L);
        spu.setName("防盗门锁 C级");
        spu.setSalesCount(52);
    }

    @Test
    void testCreateOrder_AddressNotFound() {
        when(addressMapper.selectById(1L)).thenReturn(null);
        when(redisUtil.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(Collections.emptyList());

        assertThatThrownBy(() -> orderService.createOrder(2L, request, "test-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("收货地址不存在");
    }

    @Test
    void testCreateOrder_AddressNotBelongToUser() {
        Address others = new Address();
        others.setId(1L);
        others.setUserId(99L); // 不属于当前用户
        when(addressMapper.selectById(1L)).thenReturn(others);
        when(redisUtil.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(Collections.emptyList());

        assertThatThrownBy(() -> orderService.createOrder(2L, request, "test-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("收货地址不存在");
    }

    @Test
    void testCreateOrder_LogisticsDisabled() {
        when(addressMapper.selectById(1L)).thenReturn(address);
        Logistics disabled = new Logistics();
        disabled.setId(1L);
        disabled.setStatus(0);
        when(logisticsMapper.selectById(1L)).thenReturn(disabled);
        when(redisUtil.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(Collections.emptyList());

        assertThatThrownBy(() -> orderService.createOrder(2L, request, "test-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("物流方式不存在或不可用");
    }

    @Test
    void testCreateOrder_StockInsufficient() {
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(skuService.getSkuById(5L)).thenReturn(sku);
        when(redisUtil.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        CreateOrderRequest.CartItem item = new CreateOrderRequest.CartItem();
        item.setSkuId(5L);
        item.setQuantity(100); // 超过库存

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(Collections.singletonList(item));

        assertThatThrownBy(() -> orderService.createOrder(2L, request, "test-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("库存不足");
    }

    @Test
    void createOrder_idempotentKeyReused_shouldThrow() {
        when(redisUtil.setIfAbsent(eq(RedisConstants.PREFIX_ORDER_IDEMPOTENCY + "k1"),
                any(), eq(RedisConstants.IDEMPOTENCY_TTL), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(2L, stubValidRequest(), "k1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("重复");

        verify(orderMapper, never()).insert(any(Order.class));
    }

    private CreateOrderRequest stubValidRequest() {
        CreateOrderRequest r = new CreateOrderRequest();
        CreateOrderRequest.CartItem ci = new CreateOrderRequest.CartItem();
        ci.setSkuId(1L);
        ci.setQuantity(1);
        r.setItems(java.util.List.of(ci));
        r.setAddressId(1L);
        r.setLogisticsId(1L);
        return r;
    }

    @Test
    void testCancelOrder_NotPending() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(2L);
        order.setStatus(StatusConstants.ORDER_SHIPPED); // 已发货，不是待付款

        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.cancelOrder(2L, 1L, "不想要了"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只能取消待付款的订单");
    }

    @Test
    void testConfirmReceive_NotShipped() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(2L);
        order.setStatus(StatusConstants.ORDER_PENDING_SHIPMENT); // 待发货

        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.confirmReceive(2L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只能确认收货已发货的订单");
    }

    @Test
    void testShipOrder_Success() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_PENDING_SHIPMENT);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(logisticsMapper.selectById(2L)).thenReturn(logistics);

        orderService.shipOrder(1L, 2L, "SF123456789");

        verify(orderMapper).updateById(org.mockito.Mockito.<Order>argThat(o ->
                o.getStatus() == StatusConstants.ORDER_SHIPPED &&
                o.getLogisticsId() == 2L &&
                "SF123456789".equals(o.getLogisticsNo()) &&
                o.getShipTime() != null
        ));
    }

    @Test
    void testShipOrder_LogisticsNotFound() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_PENDING_SHIPMENT);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(logisticsMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.shipOrder(1L, 999L, "SF123456789"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("物流公司不存在");
    }

    @Test
    void testAutoCancelOrder_Idempotent_AlreadyCancelled() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_CANCELLED); // 已取消

        when(orderMapper.selectById(1L)).thenReturn(order);

        boolean result = orderService.autoCancelOrder(1L, "超时未支付");

        assertThat(result).isFalse();
        verify(orderMapper, never()).updateById(org.mockito.Mockito.<Order>any());
    }

    @Test
    void testAutoCancelOrder_Idempotent_NotPending() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_COMPLETED); // 已完成

        when(orderMapper.selectById(1L)).thenReturn(order);

        boolean result = orderService.autoCancelOrder(1L, "超时未支付");

        assertThat(result).isFalse();
    }

    @Test
    void testGetOrderStats() {
        when(orderMapper.selectCount(any()))
                .thenReturn(3L)  // pendingPay
                .thenReturn(2L)  // pendingShip
                .thenReturn(1L); // shipped

        Map<String, Object> stats = orderService.getOrderStats();

        assertThat(stats.get("pendingPay")).isEqualTo(3L);
        assertThat(stats.get("pendingShip")).isEqualTo(2L);
        assertThat(stats.get("shipped")).isEqualTo(1L);
    }

    @Test
    void testGetOrderById_OrderNotExist() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.getOrderById(2L, 999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("订单不存在");
    }

    @Test
    void testDeleteOrder_NotAllowed() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(2L);
        order.setStatus(StatusConstants.ORDER_PENDING_PAYMENT); // 待付款，不可删除

        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.deleteOrder(2L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("该订单不可删除");
    }

    @Test
    void testRefundOrder_WrongStatus() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_PENDING_PAYMENT); // 待付款不可退款

        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.refundOrder(1L, "质量问题"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("该订单状态不支持退款");
    }

    @Test
    void createOrder_spuMissingFromBatch_shouldThrow() {
        when(redisUtil.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(skuService.getSkuById(5L)).thenReturn(sku);
        when(spuMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        CreateOrderRequest.CartItem i1 = new CreateOrderRequest.CartItem();
        i1.setSkuId(5L);
        i1.setQuantity(1);
        CreateOrderRequest.CartItem i2 = new CreateOrderRequest.CartItem();
        i2.setSkuId(5L);
        i2.setQuantity(2);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(java.util.List.of(i1, i2));

        assertThatThrownBy(() -> orderService.createOrder(2L, request, "k"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("商品不存在或已下架");

        verify(orderMapper, never()).insert(any(Order.class));
        verify(spuMapper, times(1)).selectBatchIds(anyCollection());
        verify(spuMapper, never()).selectById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createOrder_buildsItemsFromBatchSpuMap() {
        when(redisUtil.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);

        Sku skuA = new Sku();
        skuA.setId(5L);
        skuA.setSpuId(1L);
        skuA.setPrice(new BigDecimal("219.00"));
        skuA.setStock(40);
        skuA.setStatus(1);

        Spu spuA = new Spu();
        spuA.setId(1L);
        spuA.setName("防盗门锁 C级");
        spuA.setImages(java.util.List.of("img-a.jpg"));

        Sku skuB = new Sku();
        skuB.setId(6L);
        skuB.setSpuId(2L);
        skuB.setPrice(new BigDecimal("99.00"));
        skuB.setStock(10);
        skuB.setStatus(1);

        Spu spuB = new Spu();
        spuB.setId(2L);
        spuB.setName("不锈钢合页");
        spuB.setImages(java.util.List.of("img-b.jpg"));

        when(skuService.getSkuById(5L)).thenReturn(skuA);
        when(skuService.getSkuById(6L)).thenReturn(skuB);
        when(spuMapper.selectBatchIds(any())).thenReturn(java.util.List.of(spuA, spuB));
        when(skuService.deductStock(anyLong(), anyInt())).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenAnswer(inv -> {
            inv.getArgument(0, Order.class).setId(100L);
            return 1;
        });
        when(orderMapper.selectById(100L)).thenAnswer(inv -> {
            Order o = new Order();
            o.setId(100L);
            o.setUserId(2L);
            o.setOrderNo("SO1");
            o.setStatus(StatusConstants.ORDER_PENDING_PAYMENT);
            o.setAddressId(1L);
            o.setLogisticsId(1L);
            return o;
        });
        when(orderItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        CreateOrderRequest.CartItem i1 = new CreateOrderRequest.CartItem();
        i1.setSkuId(5L);
        i1.setQuantity(2);
        CreateOrderRequest.CartItem i2 = new CreateOrderRequest.CartItem();
        i2.setSkuId(6L);
        i2.setQuantity(3);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(1L);
        request.setLogisticsId(1L);
        request.setItems(java.util.List.of(i1, i2));

        OrderVO vo = orderService.createOrder(2L, request, "k");

        assertThat(vo).isNotNull();
        verify(spuMapper, times(1)).selectBatchIds(anyCollection());
        verify(spuMapper, never()).selectById(any());

        ArgumentCaptor<java.util.List<OrderItem>> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(orderItemMapper).insertBatch(captor.capture());
        java.util.List<OrderItem> items = captor.getValue();

        assertThat(items).hasSize(2);
        OrderItem first = items.get(0);
        assertThat(first.getSkuId()).isEqualTo(5L);
        assertThat(first.getSpuId()).isEqualTo(1L);
        assertThat(first.getProductName()).isEqualTo("防盗门锁 C级");
        assertThat(first.getProductImage()).isEqualTo("img-a.jpg");
        assertThat(first.getQuantity()).isEqualTo(2);
        assertThat(first.getSubtotal()).isEqualByComparingTo("438.00");

        OrderItem second = items.get(1);
        assertThat(second.getSkuId()).isEqualTo(6L);
        assertThat(second.getSpuId()).isEqualTo(2L);
        assertThat(second.getProductName()).isEqualTo("不锈钢合页");
        assertThat(second.getProductImage()).isEqualTo("img-b.jpg");
        assertThat(second.getQuantity()).isEqualTo(3);
        assertThat(second.getSubtotal()).isEqualByComparingTo("297.00");
    }

    @Test
    void generateOrderNo_concurrent10000_allUnique() throws Exception {
        int n = 10_000;
        java.util.Set<String> nos = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(20);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    nos.add(orderService.generateOrderNo());
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();

        assertThat(nos).hasSize(n);
        assertThat(orderService.generateOrderNo()).startsWith("SO").hasSizeLessThan(32);
    }

    @Test
    void testAutoConfirmReceive_Success() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_SHIPPED);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        boolean result = orderService.autoConfirmReceive(1L);

        assertThat(result).isTrue();
        verify(orderMapper).update(isNull(), any());
    }

    @Test
    void testAutoConfirmReceive_OrderNotExist() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        boolean result = orderService.autoConfirmReceive(999L);

        assertThat(result).isFalse();
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void testAutoConfirmReceive_NotShipped() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_COMPLETED); // 已完成，无需自动收货

        when(orderMapper.selectById(1L)).thenReturn(order);

        boolean result = orderService.autoConfirmReceive(1L);

        assertThat(result).isFalse();
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void testAutoConfirmReceive_CasMiss_ShouldSkip() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_SHIPPED);

        when(orderMapper.selectById(1L)).thenReturn(order);
        // CAS 未命中：读取后状态被并发操作改走（如退款 3→6），条件更新 0 行命中
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        boolean result = orderService.autoConfirmReceive(1L);

        assertThat(result).isFalse();
        verify(orderMapper).update(isNull(), any());
    }

    // ==================== refundOrder claim-first 重构 ====================

    @Test
    void refundOrder_claimFirst_casBeforeRefund() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_SHIPPED);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        lenient().when(orderItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        orderService.refundOrder(1L, "质量问题");

        InOrder inOrder = inOrder(orderMapper, payService);
        inOrder.verify(orderMapper).update(isNull(), any());   // 先 CAS 占位
        inOrder.verify(payService).refund(1L, "质量问题");      // 后调微信退款
    }

    @Test
    void refundOrder_casMiss_throwsAndNoRefund() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_SHIPPED);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(0); // 竞态输了

        assertThatThrownBy(() -> orderService.refundOrder(1L, "质量问题"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("状态已变更");

        verify(payService, never()).refund(any(), any()); // 未占位成功绝不退款
    }

    @Test
    void refundOrder_noStockRestore() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_SHIPPED);

        OrderItem item = new OrderItem();
        item.setSkuId(5L);
        item.setQuantity(2);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        lenient().when(orderItemMapper.selectList(any())).thenReturn(java.util.List.of(item));

        orderService.refundOrder(1L, "质量问题");

        // 库存恢复已迁到退款回调(6→7 确认成功后), refundOrder 不再还库存不发事件
        verify(skuService, never()).restoreStock(any(), any());
        verify(applicationEventPublisher, never()).publishEvent(any(StockSyncEvent.class));
    }

    @Test
    void refundOrder_refundThrows_propagatesButClaimed() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(StatusConstants.ORDER_SHIPPED);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        lenient().when(orderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("wechat error")).when(payService).refund(any(), any());

        assertThatThrownBy(() -> orderService.refundOrder(1L, "质量问题"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("wechat error");

        verify(orderMapper).update(isNull(), any()); // claim 已提交, status 保持 6
    }

    @Test
    void refundOrder_payServiceMissing_throwsBeforeCasClaim() {
        // M6: payService 缺失时必须明确报错, 不得静默跳过; 且守卫在 CAS claim 之前, 订单不会卡"退款中"
        ReflectionTestUtils.setField(orderService, "payService", null);

        assertThatThrownBy(() -> orderService.refundOrder(1L, "质量问题"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("支付服务未启用，无法退款");

        verifyNoInteractions(orderMapper); // 连 selectById 都未发生, 任何 DB 写都不允许
    }

    // ==================== confirmReceive CAS 加固 ====================

    @Test
    void confirmReceive_casSuccess_neverBlindWrite() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(2L);
        order.setStatus(StatusConstants.ORDER_SHIPPED);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        orderService.confirmReceive(2L, 1L);

        verify(orderMapper).update(isNull(), any());
        verify(orderMapper, never()).updateById(any(Order.class)); // 不再盲写
    }

    @Test
    void confirmReceive_casMiss_throws() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(2L);
        order.setStatus(StatusConstants.ORDER_SHIPPED);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(0); // 竞态: 退款刚占位 6

        assertThatThrownBy(() -> orderService.confirmReceive(2L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("状态已变更");
    }
}
