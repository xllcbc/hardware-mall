package com.example.mystore.job.pay;

import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.mapper.PaymentRecordMapper;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundReconcileJobTest {

    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private PayService payService;
    @Mock
    private RedisLockUtil redisLockUtil;

    @InjectMocks
    private RefundReconcileJob job;

    private static PaymentRecord record(Long id) {
        PaymentRecord record = new PaymentRecord();
        record.setId(id);
        return record;
    }

    @Test
    void lockNotAcquired_shouldSkip() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(false);

        job.reconcileRefundRecords();

        verify(paymentRecordMapper, never()).selectStaleRefundRecords(any(), any());
        verify(redisLockUtil, never()).unlock(anyString());
    }

    @Test
    void noStaleRecords_shouldNotCallService() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        when(paymentRecordMapper.selectStaleRefundRecords(any(), any())).thenReturn(Collections.emptyList());

        job.reconcileRefundRecords();

        verify(payService, never()).reconcileRefund(any());
        verify(redisLockUtil).unlock("job:refund-reconcile");
    }

    @Test
    void staleRecords_shouldProcessEach() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        PaymentRecord r1 = record(1L);
        PaymentRecord r2 = record(2L);
        when(paymentRecordMapper.selectStaleRefundRecords(any(), any())).thenReturn(Arrays.asList(r1, r2));

        job.reconcileRefundRecords();

        verify(payService).reconcileRefund(r1);
        verify(payService).reconcileRefund(r2);
        verify(redisLockUtil).unlock("job:refund-reconcile");
    }

    @Test
    void singleRecordFailure_shouldNotAffectOthers() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        PaymentRecord r1 = record(1L);
        PaymentRecord r2 = record(2L);
        when(paymentRecordMapper.selectStaleRefundRecords(any(), any())).thenReturn(Arrays.asList(r1, r2));
        org.mockito.Mockito.doThrow(new RuntimeException("wechat query error"))
                .when(payService).reconcileRefund(r1);

        job.reconcileRefundRecords();

        verify(payService).reconcileRefund(r2);
        verify(redisLockUtil).unlock("job:refund-reconcile");
    }

    @Test
    void query_shouldUseGraceAndBatchLimit() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        when(paymentRecordMapper.selectStaleRefundRecords(any(LocalDateTime.class), eq(100)))
                .thenReturn(Collections.emptyList());

        LocalDateTime beforeCall = LocalDateTime.now().minusMinutes(10);
        job.reconcileRefundRecords();
        LocalDateTime afterCall = LocalDateTime.now().minusMinutes(10);

        verify(paymentRecordMapper).selectStaleRefundRecords(
                org.mockito.ArgumentMatchers.argThat(t -> !t.isBefore(beforeCall) && !t.isAfter(afterCall)),
                eq(100));
    }

    @Test
    void unchangedRecordReturnedEveryRound_shouldProcessOnce() {
        when(redisLockUtil.tryLock(anyString())).thenReturn(true);
        PaymentRecord r1 = record(1L);
        when(paymentRecordMapper.selectStaleRefundRecords(any(), any())).thenReturn(List.of(r1));

        job.reconcileRefundRecords();

        verify(payService, times(1)).reconcileRefund(r1);
    }
}
