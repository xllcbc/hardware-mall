package com.example.mystore.job.order;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.OrderService;
import com.example.mystore.util.RedisLockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAutoReceiveJobTest {

    @Mock
    private OrderService orderService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RedisLockUtil redisLockUtil;

    @InjectMocks
    private OrderAutoReceiveJob job;

    @BeforeEach
    void setUp() {
        // @Value 在纯 Mockito 单测中不生效，手动注入与 application.yml 默认一致的配置
        ReflectionTestUtils.setField(job, "localDays", 2);
        ReflectionTestUtils.setField(job, "expressDays", 10);
    }

    @Test
    void lockNotAcquired_shouldSkip() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(false);

        job.autoReceiveShippedOrders();

        verify(orderMapper, never()).selectStaleShippedOrders(any(), any(), any());
        verify(redisLockUtil, never()).unlock(anyString());
    }

    @Test
    void noStaleOrders_shouldNotCallService() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        when(orderMapper.selectStaleShippedOrders(any(), any(), any())).thenReturn(Collections.emptyList());

        job.autoReceiveShippedOrders();

        verify(orderService, never()).autoConfirmReceive(any());
        verify(redisLockUtil).unlock("job:order-auto-receive");
    }

    @Test
    void staleOrders_shouldProcessEach() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        Order o1 = staleLocalOrder(1L);
        Order o2 = staleLocalOrder(2L);
        when(orderMapper.selectStaleShippedOrders(any(), any(), any())).thenReturn(Arrays.asList(o1, o2));
        when(orderService.autoConfirmReceive(1L)).thenReturn(true);
        when(orderService.autoConfirmReceive(2L)).thenReturn(false);

        job.autoReceiveShippedOrders();

        verify(orderService).autoConfirmReceive(1L);
        verify(orderService).autoConfirmReceive(2L);
        verify(redisLockUtil).unlock("job:order-auto-receive");
    }

    @Test
    void singleOrderFailure_shouldNotAffectOthers() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        Order o1 = staleLocalOrder(1L);
        Order o2 = staleLocalOrder(2L);
        when(orderMapper.selectStaleShippedOrders(any(), any(), any())).thenReturn(Arrays.asList(o1, o2));
        when(orderService.autoConfirmReceive(1L)).thenThrow(new RuntimeException("db error"));
        when(orderService.autoConfirmReceive(2L)).thenReturn(true);

        job.autoReceiveShippedOrders();

        verify(orderService).autoConfirmReceive(2L);
        verify(redisLockUtil).unlock("job:order-auto-receive");
    }

    @Test
    void localOrderNotYetDue_shouldSkip() {
        // 同城配送 T+2: 发货仅 1 天, 未到期 → 跳过
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        Order o = new Order();
        o.setId(1L);
        o.setDeliveryType(StatusConstants.DELIVERY_TYPE_LOCAL);
        o.setShipTime(LocalDateTime.now().minusDays(1));
        when(orderMapper.selectStaleShippedOrders(any(), any(), any())).thenReturn(Collections.singletonList(o));

        job.autoReceiveShippedOrders();

        verify(orderService, never()).autoConfirmReceive(any());
        verify(redisLockUtil).unlock("job:order-auto-receive");
    }

    @Test
    void expressOrderUsesT10_shouldSkipAt3Days() {
        // 未知/快递类型按 T+10: 发货 3 天, 未到期 → 跳过
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        Order o = new Order();
        o.setId(1L);
        o.setDeliveryType(null);
        o.setShipTime(LocalDateTime.now().minusDays(3));
        when(orderMapper.selectStaleShippedOrders(any(), any(), any())).thenReturn(Collections.singletonList(o));

        job.autoReceiveShippedOrders();

        verify(orderService, never()).autoConfirmReceive(any());
        verify(redisLockUtil).unlock("job:order-auto-receive");
    }

    private Order staleLocalOrder(Long id) {
        Order o = new Order();
        o.setId(id);
        o.setDeliveryType(StatusConstants.DELIVERY_TYPE_LOCAL);
        o.setShipTime(LocalDateTime.now().minusDays(3));
        return o;
    }

    @Test
    void query_shouldUseShippedStatusAndConfiguredDays() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        when(orderMapper.selectStaleShippedOrders(eq(3), any(LocalDateTime.class), eq(100)))
                .thenReturn(Collections.emptyList());

        job.autoReceiveShippedOrders();

        verify(orderMapper).selectStaleShippedOrders(eq(3), any(LocalDateTime.class), eq(100));
    }
}
