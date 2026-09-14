package com.example.mystore.job.pay;

import com.example.mystore.entity.db.Order;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.PayService;
import com.example.mystore.util.RedisLockUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class RefundReconcileJobTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private PayService payService;
    @Mock
    private RedisLockUtil redisLockUtil;

    @InjectMocks
    private RefundReconcileJob job;

    @Test
    void lockNotAcquired_shouldSkip() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(false);

        job.reconcileRefundingOrders();

        verify(orderMapper, never()).selectStaleRefundingOrders(any(), any(), any());
        verify(redisLockUtil, never()).unlock(anyString());
    }

    @Test
    void noStaleOrders_shouldNotCallService() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        when(orderMapper.selectStaleRefundingOrders(any(), any(), any())).thenReturn(Collections.emptyList());

        job.reconcileRefundingOrders();

        verify(payService, never()).reconcileRefund(any());
        verify(redisLockUtil).unlock("job:refund-reconcile");
    }

    @Test
    void staleOrders_shouldProcessEach() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        Order o1 = new Order();
        o1.setId(1L);
        Order o2 = new Order();
        o2.setId(2L);
        when(orderMapper.selectStaleRefundingOrders(any(), any(), any())).thenReturn(Arrays.asList(o1, o2));

        job.reconcileRefundingOrders();

        verify(payService).reconcileRefund(1L);
        verify(payService).reconcileRefund(2L);
        verify(redisLockUtil).unlock("job:refund-reconcile");
    }

    @Test
    void singleOrderFailure_shouldNotAffectOthers() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        Order o1 = new Order();
        o1.setId(1L);
        Order o2 = new Order();
        o2.setId(2L);
        when(orderMapper.selectStaleRefundingOrders(any(), any(), any())).thenReturn(Arrays.asList(o1, o2));
        org.mockito.Mockito.doThrow(new RuntimeException("wechat query error"))
                .when(payService).reconcileRefund(1L);

        job.reconcileRefundingOrders();

        verify(payService).reconcileRefund(2L);
        verify(redisLockUtil).unlock("job:refund-reconcile");
    }

    @Test
    void query_shouldUseRefundingStatusAndLimit() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        when(orderMapper.selectStaleRefundingOrders(eq(6), any(LocalDateTime.class), eq(100)))
                .thenReturn(Collections.emptyList());

        job.reconcileRefundingOrders();

        verify(orderMapper).selectStaleRefundingOrders(eq(6), any(LocalDateTime.class), eq(100));
    }
}
