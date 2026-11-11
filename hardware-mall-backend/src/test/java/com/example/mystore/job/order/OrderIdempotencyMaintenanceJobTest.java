package com.example.mystore.job.order;

import com.example.mystore.service.OrderIdempotencyService;
import com.example.mystore.util.RedisLockUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderIdempotencyMaintenanceJobTest {

    @Mock
    private OrderIdempotencyService orderIdempotencyService;
    @Mock
    private RedisLockUtil redisLockUtil;

    @InjectMocks
    private OrderIdempotencyMaintenanceJob job;

    @Test
    void reclaimExpiredProcessing_usesConfiguredTimeoutAndReleasesLock() {
        job.setProcessingTimeoutMinutes(10);
        when(redisLockUtil.tryLock("job:order-idempotency-reclaim")).thenReturn(true);
        when(orderIdempotencyService.reclaimExpiredProcessing(any())).thenReturn(2);

        job.reclaimExpiredProcessing();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderIdempotencyService).reclaimExpiredProcessing(cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusMinutes(9));
        assertThat(cutoff.getValue()).isAfter(LocalDateTime.now().minusMinutes(11));
        verify(redisLockUtil).unlock("job:order-idempotency-reclaim");
    }

    @Test
    void reclaimExpiredProcessing_skipsWhenLockUnavailable() {
        when(redisLockUtil.tryLock("job:order-idempotency-reclaim")).thenReturn(false);

        job.reclaimExpiredProcessing();

        verify(orderIdempotencyService, never()).reclaimExpiredProcessing(any());
        verify(redisLockUtil, never()).unlock(any());
    }

    @Test
    void cleanupSettledRecords_usesSevenDayRetentionAndReleasesLock() {
        job.setRetentionDays(7);
        when(redisLockUtil.tryLock("job:order-idempotency-cleanup")).thenReturn(true);
        when(orderIdempotencyService.cleanupSettledBefore(any())).thenReturn(3);

        job.cleanupSettledRecords();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderIdempotencyService).cleanupSettledBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusDays(7).plusMinutes(1));
        assertThat(cutoff.getValue()).isAfter(LocalDateTime.now().minusDays(7).minusMinutes(1));
        verify(redisLockUtil).unlock("job:order-idempotency-cleanup");
    }

    @Test
    void cleanupSettledRecords_skipsWhenLockUnavailable() {
        when(redisLockUtil.tryLock("job:order-idempotency-cleanup")).thenReturn(false);

        job.cleanupSettledRecords();

        verify(orderIdempotencyService, never()).cleanupSettledBefore(any());
    }
}
