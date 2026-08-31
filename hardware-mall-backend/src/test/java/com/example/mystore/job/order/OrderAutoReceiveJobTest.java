package com.example.mystore.job.order;

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
        ReflectionTestUtils.setField(job, "autoReceiveDays", 7);
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
        Order o1 = new Order();
        o1.setId(1L);
        Order o2 = new Order();
        o2.setId(2L);
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
        Order o1 = new Order();
        o1.setId(1L);
        Order o2 = new Order();
        o2.setId(2L);
        when(orderMapper.selectStaleShippedOrders(any(), any(), any())).thenReturn(Arrays.asList(o1, o2));
        when(orderService.autoConfirmReceive(1L)).thenThrow(new RuntimeException("db error"));
        when(orderService.autoConfirmReceive(2L)).thenReturn(true);

        job.autoReceiveShippedOrders();

        verify(orderService).autoConfirmReceive(2L);
        verify(redisLockUtil).unlock("job:order-auto-receive");
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
