package com.example.mystore.service.impl;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.*;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.mapper.*;
import com.example.mystore.service.CartService;
import com.example.mystore.service.SkuService;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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

    @BeforeEach
    void setUp() {
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

        orderService.shipOrder(1L, "SF123456789");

        verify(orderMapper).updateById(org.mockito.Mockito.<Order>argThat(o ->
                o.getStatus() == StatusConstants.ORDER_SHIPPED &&
                "SF123456789".equals(o.getLogisticsNo()) &&
                o.getShipTime() != null
        ));
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
}
