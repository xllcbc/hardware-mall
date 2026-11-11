package com.example.mystore.service.impl;

import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.OrderIdempotency;
import com.example.mystore.mapper.OrderIdempotencyMapper;
import com.example.mystore.service.OrderIdempotencyService.ClaimOutcome;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderIdempotencyServiceImplTest {

    private static final String HASH = "a".repeat(64);

    @Mock
    private OrderIdempotencyMapper idempotencyMapper;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private OrderIdempotencyServiceImpl service;

    @Test
    void claim_newRecord_acquiresAndStoresProcessing() {
        when(redisUtil.setIfAbsentRaw(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(idempotencyMapper.selectByUserAndKey(7L, "k")).thenReturn(null);
        when(idempotencyMapper.insert(any(OrderIdempotency.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, OrderIdempotency.class).setId(1L);
            return 1;
        });

        ClaimOutcome outcome = service.claim(7L, "k", HASH);

        assertThat(outcome.kind()).isEqualTo(ClaimOutcome.Kind.ACQUIRED);
        assertThat(outcome.recordId()).isEqualTo(1L);
        assertThat(outcome.ownerToken()).isNotBlank();
        assertThat(outcome.lockAcquired()).isTrue();

        ArgumentCaptor<OrderIdempotency> captor = ArgumentCaptor.forClass(OrderIdempotency.class);
        verify(idempotencyMapper).insert(captor.capture());
        OrderIdempotency stored = captor.getValue();
        assertThat(stored.getUserId()).isEqualTo(7L);
        assertThat(stored.getIdemKey()).isEqualTo("k");
        assertThat(stored.getRequestHash()).isEqualTo(HASH);
        assertThat(stored.getState()).isEqualTo(OrderIdempotency.STATE_PROCESSING);
        assertThat(stored.getOwnerToken()).isEqualTo(outcome.ownerToken());
        assertThat(stored.getCreateTime()).isNotNull();
        assertThat(stored.getUpdateTime()).isNotNull();
    }

    @Test
    void claim_duplicateInsertReadsExistingProcessing() {
        OrderIdempotency processing = record(OrderIdempotency.STATE_PROCESSING, "other", LocalDateTime.now(), null);
        when(redisUtil.setIfAbsentRaw(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(idempotencyMapper.selectByUserAndKey(7L, "k")).thenReturn(null, processing);
        when(idempotencyMapper.insert(any(OrderIdempotency.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        ClaimOutcome outcome = service.claim(7L, "k", HASH);

        assertThat(outcome.kind()).isEqualTo(ClaimOutcome.Kind.PROCESSING);
        assertThat(outcome.lockAcquired()).isFalse();
        verify(redisUtil).deleteIfValueMatches(eq("order:idem:7:k"), eq(outcome.ownerToken()));
    }

    @Test
    void claim_succeededSameFingerprint_returnsOriginalOrder() {
        OrderIdempotency succeeded = record(OrderIdempotency.STATE_SUCCEEDED, "owner", LocalDateTime.now(), 99L);
        when(idempotencyMapper.selectByUserAndKey(7L, "k")).thenReturn(succeeded);

        ClaimOutcome outcome = service.claim(7L, "k", HASH);

        assertThat(outcome.kind()).isEqualTo(ClaimOutcome.Kind.SUCCEEDED);
        assertThat(outcome.orderId()).isEqualTo(99L);
    }

    @Test
    void claim_succeededDifferentFingerprint_conflicts() {
        OrderIdempotency succeeded = record(OrderIdempotency.STATE_SUCCEEDED, "owner", LocalDateTime.now(), 99L);
        succeeded.setRequestHash("b".repeat(64));
        when(idempotencyMapper.selectByUserAndKey(7L, "k")).thenReturn(succeeded);

        ClaimOutcome outcome = service.claim(7L, "k", HASH);

        assertThat(outcome.kind()).isEqualTo(ClaimOutcome.Kind.CONFLICT);
    }

    @Test
    void claim_failedRecord_isReacquired() {
        OrderIdempotency failed = record(OrderIdempotency.STATE_FAILED, "old-owner", LocalDateTime.now(), null);
        when(idempotencyMapper.selectByUserAndKey(7L, "k")).thenReturn(failed);
        when(idempotencyMapper.claimFailedRecord(eq(1L), anyString(), eq(HASH), any()))
                .thenReturn(1);

        ClaimOutcome outcome = service.claim(7L, "k", HASH);

        assertThat(outcome.kind()).isEqualTo(ClaimOutcome.Kind.ACQUIRED);
        assertThat(outcome.recordId()).isEqualTo(1L);
    }

    @Test
    void claim_expiredProcessing_isTakenOver() {
        OrderIdempotency stale = record(OrderIdempotency.STATE_PROCESSING, "old-owner",
                LocalDateTime.now().minusMinutes(11), null);
        when(idempotencyMapper.selectByUserAndKey(7L, "k")).thenReturn(stale);
        when(idempotencyMapper.takeOverExpiredProcessing(
                eq(1L), eq("old-owner"), anyString(), eq(HASH), any(), any())).thenReturn(1);

        ClaimOutcome outcome = service.claim(7L, "k", HASH);

        assertThat(outcome.kind()).isEqualTo(ClaimOutcome.Kind.ACQUIRED);
        assertThat(outcome.ownerToken()).isNotEqualTo("old-owner");
    }

    @Test
    void claim_freshProcessing_returnsProcessing() {
        OrderIdempotency fresh = record(OrderIdempotency.STATE_PROCESSING, "other", LocalDateTime.now(), null);
        when(idempotencyMapper.selectByUserAndKey(7L, "k")).thenReturn(fresh);

        ClaimOutcome outcome = service.claim(7L, "k", HASH);

        assertThat(outcome.kind()).isEqualTo(ClaimOutcome.Kind.PROCESSING);
    }

    @Test
    void claim_redisUnavailable_stillUsesDatabase() {
        when(redisUtil.setIfAbsentRaw(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("redis down"));
        when(idempotencyMapper.selectByUserAndKey(7L, "k")).thenReturn(null);
        when(idempotencyMapper.insert(any(OrderIdempotency.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, OrderIdempotency.class).setId(2L);
            return 1;
        });

        ClaimOutcome outcome = service.claim(7L, "k", HASH);

        assertThat(outcome.kind()).isEqualTo(ClaimOutcome.Kind.ACQUIRED);
        assertThat(outcome.lockAcquired()).isFalse();
        verify(redisUtil, never()).deleteIfValueMatches(anyString(), anyString());
    }

    @Test
    void claim_lockHeldByOther_stillUsesDatabase() {
        when(redisUtil.setIfAbsentRaw(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);
        when(idempotencyMapper.selectByUserAndKey(7L, "k")).thenReturn(null);
        when(idempotencyMapper.insert(any(OrderIdempotency.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, OrderIdempotency.class).setId(3L);
            return 1;
        });

        ClaimOutcome outcome = service.claim(7L, "k", HASH);

        assertThat(outcome.kind()).isEqualTo(ClaimOutcome.Kind.ACQUIRED);
        assertThat(outcome.lockAcquired()).isFalse();
        verify(redisUtil, never()).deleteIfValueMatches(anyString(), anyString());
    }

    @Test
    void markSucceeded_staleOwner_throwsForRollback() {
        when(idempotencyMapper.markSucceeded(eq(1L), eq("old-owner"), eq(99L), any())).thenReturn(0);

        assertThatThrownBy(() -> service.markSucceeded(1L, "old-owner", 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("幂等记录状态已变更，请重试");
    }

    @Test
    void markFailed_updatesOnlyOwnedProcessingRecord() {
        when(idempotencyMapper.markFailed(eq(1L), eq("owner"), any())).thenReturn(1);

        service.markFailed(1L, "owner");

        verify(idempotencyMapper).markFailed(eq(1L), eq("owner"), any());
    }

    @Test
    void releaseLock_onlyReleasesAcquiredOwnerToken() {
        service.releaseLock(7L, "k", "token", true);
        service.releaseLock(7L, "k", "token", false);

        verify(redisUtil, times(1)).deleteIfValueMatches("order:idem:7:k", "token");
    }

    private OrderIdempotency record(String state, String ownerToken,
                                    LocalDateTime updateTime, Long orderId) {
        OrderIdempotency record = new OrderIdempotency();
        record.setId(1L);
        record.setUserId(7L);
        record.setIdemKey("k");
        record.setRequestHash(HASH);
        record.setState(state);
        record.setOwnerToken(ownerToken);
        record.setOrderId(orderId);
        record.setCreateTime(LocalDateTime.now().minusDays(1));
        record.setUpdateTime(updateTime);
        return record;
    }
}
