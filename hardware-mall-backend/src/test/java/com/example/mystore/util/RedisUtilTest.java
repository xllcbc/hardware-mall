package com.example.mystore.util;

import com.example.mystore.common.constant.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private RedisLockUtil redisLockUtil;

    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        redisUtil = new RedisUtil(redisTemplate, redisLockUtil);
    }

    @Test
    void cacheHit_returnsValue_supplierNotCalled() {
        when(valueOps.get("k")).thenReturn("cached-data");
        AtomicBoolean called = new AtomicBoolean(false);

        String result = redisUtil.queryWithCache("k", String.class, 60, () -> {
            called.set(true);
            return "db-data";
        });

        assertThat(result).isEqualTo("cached-data");
        assertThat(called).isFalse();
        verify(redisLockUtil, never()).tryLock(anyString());
    }

    @Test
    void cacheHit_sentinel_returnsNull() {
        when(valueOps.get("k")).thenReturn(RedisConstants.CACHE_NULL);
        AtomicBoolean called = new AtomicBoolean(false);

        String result = redisUtil.queryWithCache("k", String.class, 60, () -> {
            called.set(true);
            return "db-data";
        });

        assertThat(result).isNull();
        assertThat(called).isFalse();
    }

    @Test
    void cacheHit_wrongType_deletesAndRebuilds() {
        when(valueOps.get("k")).thenReturn(42, null);
        when(redisLockUtil.tryLock("k")).thenReturn(true);

        String result = redisUtil.queryWithCache("k", String.class, 60, () -> "db-data");

        assertThat(result).isEqualTo("db-data");
        verify(redisTemplate).delete("k");
        verify(valueOps).set(eq("k"), eq("db-data"), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisLockUtil).unlock("k");
    }

    @Test
    void miss_lockAcquired_rebuildsAndCaches() {
        when(redisLockUtil.tryLock("k")).thenReturn(true);

        String result = redisUtil.queryWithCache("k", String.class, 60, () -> "db-data");

        assertThat(result).isEqualTo("db-data");
        verify(valueOps).set(eq("k"), eq("db-data"), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisLockUtil).unlock("k");
    }

    @Test
    void miss_lockAcquired_supplierNull_writesSentinel() {
        when(redisLockUtil.tryLock("k")).thenReturn(true);

        String result = redisUtil.queryWithCache("k", String.class, 60, () -> null);

        assertThat(result).isNull();
        verify(valueOps).set(eq("k"), eq(RedisConstants.CACHE_NULL),
                eq(RedisConstants.CACHE_NULL_TTL), eq(TimeUnit.SECONDS));
        verify(redisLockUtil).unlock("k");
    }

    @Test
    void miss_lockNotAcquired_loopReadsCache() {
        redisUtil.setLockSleepMs(1);
        when(redisLockUtil.tryLock("k")).thenReturn(false);
        when(valueOps.get("k")).thenReturn(null, null, "cached-data");
        AtomicInteger supplierCalls = new AtomicInteger(0);

        String result = redisUtil.queryWithCache("k", String.class, 60, () -> {
            supplierCalls.incrementAndGet();
            return "db-data";
        });

        assertThat(result).isEqualTo("cached-data");
        assertThat(supplierCalls.get()).isZero();
    }

    @Test
    void miss_lockNotAcquired_eventuallyAcquiresLock() {
        redisUtil.setLockSleepMs(1);
        when(redisLockUtil.tryLock("k")).thenReturn(false, false, true);
        when(valueOps.get("k")).thenReturn(null, null, null, null);

        String result = redisUtil.queryWithCache("k", String.class, 60, () -> "db-data");

        assertThat(result).isEqualTo("db-data");
        verify(valueOps).set(eq("k"), eq("db-data"), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisLockUtil).unlock("k");
    }

    @Test
    void miss_lockNotAcquired_keepsWaitingAndNeverQueriesDb() throws Exception {
        redisUtil.setLockSleepMs(1);
        redisUtil.setLockWarnLoops(1);
        when(redisLockUtil.tryLock("k")).thenReturn(false);
        when(valueOps.get("k")).thenReturn(null);
        AtomicBoolean dbCalled = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> redisUtil.queryWithCache("k", String.class, 60, () -> {
            dbCalled.set(true);
            return "db-data";
        }));

        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> future.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThat(dbCalled).isFalse();
        } finally {
            future.cancel(true);
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }
}
