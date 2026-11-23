package com.example.mystore.common.cache;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 有界批量回填协议的契约测试。
 *
 * <p>协议用例通过 {@link BatchCacheLoader} 的唯一批量入口观察行为，
 * 配合真策略、真 key 所有者、真解码与假回源；缓存与锁原语是系统边界，使用 mock。</p>
 */
@ExtendWith(MockitoExtension.class)
class BatchCacheLoaderTest {

    private static final String PREFIX = "test:thing:v1:";
    private static final String GENERATION = "gen-1";
    private static final CachePolicy POLICY = new CachePolicy(240L, 60L, 300L, 500L);

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    @InjectMocks
    private BatchCacheLoader batchCacheLoader;

    private TestKeys keys;

    @BeforeEach
    void setUp() {
        keys = new TestKeys(GENERATION);
        lenient().when(redisUtil.isNull(any()))
                .thenAnswer(invocation -> RedisConstants.CACHE_NULL.equals(invocation.getArgument(0)));
    }

    @Test
    void returnsEmptyListForNullAndEmptyInput() {
        TestCell cell = cell(ids -> Map.of());

        assertThat(batchCacheLoader.load(null, cell)).isEmpty();
        assertThat(batchCacheLoader.load(List.of(), cell)).isEmpty();

        assertThat(cell.sourceCalls()).isZero();
        verifyNoInteractions(redisUtil, redisLockUtil);
    }

    @Test
    void returnsSameLengthNullListWhenAllIdsAreNull() {
        TestCell cell = cell(ids -> Map.of());

        assertThat(batchCacheLoader.load(Arrays.asList(null, null, null), cell))
                .containsExactly(null, null, null);

        assertThat(cell.sourceCalls()).isZero();
        verifyNoInteractions(redisUtil, redisLockUtil);
    }

    @Test
    void returnsAllCachedValuesWithoutLockingOrLoadingSource() {
        when(redisUtil.multiGet(List.of(key(2), key(1))))
                .thenReturn(List.of("v2", "v1"));
        TestCell cell = cell(ids -> Map.of());

        List<String> result = batchCacheLoader.load(List.of(2L, 1L), cell);

        assertThat(result).containsExactly("v2", "v1");
        assertThat(cell.sourceCalls()).isZero();
        verifyNoInteractions(redisLockUtil);
        verify(redisUtil, never()).setWithJitter(any(), any(), anyLong(), any(), anyLong());
        verify(redisUtil, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void loadsOnlyMissingIdsOnceAndWritesBackWithJitter() {
        when(redisUtil.multiGet(List.of(key(1), key(2))))
                .thenReturn(Arrays.asList("v1", null));
        when(redisUtil.multiGet(List.of(key(2))))
                .thenReturn(Collections.singletonList(null));
        when(redisLockUtil.tryLock(eq(key(2)), anyLong())).thenReturn(true);
        TestCell cell = cell(ids -> Map.of(2L, "v2"));

        List<String> result = batchCacheLoader.load(List.of(1L, 2L), cell);

        assertThat(result).containsExactly("v1", "v2");
        assertThat(cell.sourceRequests()).containsExactly(List.of(2L));
        verify(redisUtil).setWithJitter(key(2), "v2", 240L, TimeUnit.SECONDS, 60L);
        verify(redisLockUtil).unlock(key(2));
    }

    @Test
    void usesValueRebuiltByAnotherThreadAfterLockWithoutLoadingSource() {
        when(redisUtil.multiGet(List.of(key(2))))
                .thenReturn(Collections.singletonList(null), Collections.singletonList("v2-rebuilt"));
        when(redisLockUtil.tryLock(eq(key(2)), anyLong())).thenReturn(true);
        TestCell cell = cell(ids -> Map.of(2L, "v2"));

        List<String> result = batchCacheLoader.load(List.of(2L), cell);

        assertThat(result).containsExactly("v2-rebuilt");
        assertThat(cell.sourceCalls()).isZero();
        verify(redisUtil, never()).setWithJitter(any(), any(), anyLong(), any(), anyLong());
    }

    @Test
    void treatsUndecodableCachedValueAsMiss() {
        when(redisUtil.multiGet(List.of(key(1))))
                .thenReturn(Collections.singletonList(42));
        when(redisLockUtil.tryLock(eq(key(1)), anyLong())).thenReturn(true);
        TestCell cell = cell(ids -> Map.of(1L, "v1"));

        List<String> result = batchCacheLoader.load(List.of(1L), cell);

        assertThat(result).containsExactly("v1");
        assertThat(cell.sourceRequests()).containsExactly(List.of(1L));
        verify(redisUtil).setWithJitter(key(1), "v1", 240L, TimeUnit.SECONDS, 60L);
    }

    @Test
    void returnsNullForSentinelHitWithoutLoadingSource() {
        when(redisUtil.multiGet(List.of(key(1))))
                .thenReturn(Collections.singletonList(RedisConstants.CACHE_NULL));
        TestCell cell = cell(ids -> Map.of(1L, "v1"));

        List<String> result = batchCacheLoader.load(List.of(1L), cell);

        assertThat(result).containsExactly((String) null);
        assertThat(cell.sourceCalls()).isZero();
        verifyNoInteractions(redisLockUtil);
    }

    @Test
    void writesSentinelOnlyForIdsConfirmedMissingBySource() {
        when(redisUtil.multiGet(List.of(key(1), key(2))))
                .thenReturn(Arrays.asList(null, null), Arrays.asList(null, null));
        when(redisLockUtil.tryLock(eq(key(1)), anyLong())).thenReturn(true);
        when(redisLockUtil.tryLock(eq(key(2)), anyLong())).thenReturn(true);
        TestCell cell = cell(ids -> Map.of(2L, "v2"));

        List<String> result = batchCacheLoader.load(List.of(1L, 2L), cell);

        assertThat(result).containsExactly(null, "v2");
        verify(redisUtil).set(key(1), RedisConstants.CACHE_NULL, 300L, TimeUnit.SECONDS);
        verify(redisUtil, never()).set(eq(key(2)), any(), anyLong(), any());
        verify(redisUtil).setWithJitter(key(2), "v2", 240L, TimeUnit.SECONDS, 60L);
        verify(redisUtil, never()).setWithJitter(eq(key(1)), any(), anyLong(), any(), anyLong());
    }

    @Test
    void keepsInputOrderAndDuplicatesWhileLockingInAscendingIdOrder() {
        when(redisUtil.multiGet(List.of(key(2), key(1), key(2), key(3))))
                .thenReturn(Arrays.asList(null, null, null, null));
        when(redisUtil.multiGet(List.of(key(1), key(2), key(3))))
                .thenReturn(Arrays.asList(null, null, null));
        when(redisLockUtil.tryLock(any(), anyLong())).thenReturn(true);
        TestCell cell = cell(ids -> Map.of(1L, "v1", 2L, "v2", 3L, "v3"));

        List<String> result = batchCacheLoader.load(List.of(2L, 1L, 2L, 3L), cell);

        assertThat(result).containsExactly("v2", "v1", "v2", "v3");
        InOrder lockOrder = inOrder(redisLockUtil);
        lockOrder.verify(redisLockUtil).tryLock(eq(key(1)), anyLong());
        lockOrder.verify(redisLockUtil).tryLock(eq(key(2)), anyLong());
        lockOrder.verify(redisLockUtil).tryLock(eq(key(3)), anyLong());
        lockOrder.verify(redisLockUtil).unlock(key(3));
        lockOrder.verify(redisLockUtil).unlock(key(2));
        lockOrder.verify(redisLockUtil).unlock(key(1));
    }

    @Test
    void loadsAllUnresolvedIdsOnceAndWritesNothingWhenLockUnavailable() {
        when(redisUtil.multiGet(List.of(key(1), key(2))))
                .thenReturn(Arrays.asList(null, null));
        when(redisLockUtil.tryLock(eq(key(1)), anyLong())).thenReturn(false);
        TestCell cell = cell(ids -> Map.of(1L, "v1", 2L, "v2"));

        List<String> result = batchCacheLoader.load(List.of(1L, 2L), cell);

        assertThat(result).containsExactly("v1", "v2");
        assertThat(cell.sourceRequests()).containsExactly(List.of(1L, 2L));
        verify(redisLockUtil, never()).tryLock(eq(key(2)), anyLong());
        verify(redisUtil, never()).setWithJitter(any(), any(), anyLong(), any(), anyLong());
        verify(redisUtil, never()).set(any(), any(), anyLong(), any());
        verify(redisUtil, times(1)).multiGet(any());
    }

    @Test
    void loadsOnceAndWritesNothingWhenLockWaitBudgetIsExhausted() {
        when(redisUtil.multiGet(List.of(key(1), key(2))))
                .thenReturn(Arrays.asList(null, null));
        when(redisLockUtil.tryLock(eq(key(1)), anyLong())).thenReturn(true);
        when(redisLockUtil.tryLock(eq(key(2)), longThat(ms -> ms >= 0 && ms <= 500))).thenReturn(false);
        TestCell cell = cell(ids -> Map.of(1L, "v1", 2L, "v2"));

        List<String> result = batchCacheLoader.load(List.of(1L, 2L), cell);

        assertThat(result).containsExactly("v1", "v2");
        assertThat(cell.sourceRequests()).containsExactly(List.of(1L, 2L));
        ArgumentCaptor<Long> waits = ArgumentCaptor.forClass(Long.class);
        verify(redisLockUtil, times(2)).tryLock(any(), waits.capture());
        assertThat(waits.getAllValues()).allSatisfy(ms -> assertThat(ms).isBetween(0L, 500L));
        verify(redisLockUtil).unlock(key(1));
        verify(redisLockUtil, never()).unlock(key(2));
        verify(redisUtil, times(1)).multiGet(any());
        verify(redisUtil, never()).setWithJitter(any(), any(), anyLong(), any(), anyLong());
        verify(redisUtil, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void releasesAcquiredLocksAndWritesNothingWhenSourceThrows() {
        when(redisUtil.multiGet(List.of(key(1), key(2))))
                .thenReturn(Arrays.asList(null, null));
        when(redisLockUtil.tryLock(any(), anyLong())).thenReturn(true);
        TestCell cell = cell(ids -> {
            throw new IllegalStateException("db down");
        });

        assertThatThrownBy(() -> batchCacheLoader.load(List.of(1L, 2L), cell))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");

        InOrder lockOrder = inOrder(redisLockUtil);
        lockOrder.verify(redisLockUtil).tryLock(eq(key(1)), anyLong());
        lockOrder.verify(redisLockUtil).tryLock(eq(key(2)), anyLong());
        lockOrder.verify(redisLockUtil).unlock(key(2));
        lockOrder.verify(redisLockUtil).unlock(key(1));
        verify(redisUtil, never()).setWithJitter(any(), any(), anyLong(), any(), anyLong());
        verify(redisUtil, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void doesNotRefreshSentinelForIdThatWasAlreadyAbsentInCache() {
        when(redisUtil.multiGet(List.of(key(1), key(2))))
                .thenReturn(Arrays.asList(RedisConstants.CACHE_NULL, null));
        when(redisUtil.multiGet(List.of(key(2))))
                .thenReturn(Collections.singletonList(null));
        when(redisLockUtil.tryLock(eq(key(2)), anyLong())).thenReturn(true);
        TestCell cell = cell(ids -> Map.of(2L, "v2"));

        List<String> result = batchCacheLoader.load(List.of(1L, 2L), cell);

        assertThat(result).containsExactly(null, "v2");
        assertThat(cell.sourceRequests()).containsExactly(List.of(2L));
        verify(redisUtil, never()).set(any(), any(), anyLong(), any());
        verify(redisUtil).setWithJitter(key(2), "v2", 240L, TimeUnit.SECONDS, 60L);
    }

    @Test
    void writesNoSentinelWhenNullTtlIsDisabled() {
        CachePolicy noSentinelPolicy = new CachePolicy(240L, 60L, 0L, 500L);
        when(redisUtil.multiGet(List.of(key(1))))
                .thenReturn(Collections.singletonList(null), Collections.singletonList(null));
        when(redisLockUtil.tryLock(eq(key(1)), anyLong())).thenReturn(true);
        TestCell cell = cell(noSentinelPolicy, ids -> Map.of());

        List<String> result = batchCacheLoader.load(List.of(1L), cell);

        assertThat(result).containsExactly((String) null);
        verify(redisUtil, never()).set(any(), any(), anyLong(), any());
        verify(redisUtil, never()).setWithJitter(any(), any(), anyLong(), any(), anyLong());
    }

    @Nested
    class CachePolicyConstruction {

        @Test
        void acceptsLegalPolicy() {
            CachePolicy policy = new CachePolicy(240L, 60L, 300L, 500L);

            assertThat(policy.ttlSeconds()).isEqualTo(240L);
            assertThat(policy.jitterSeconds()).isEqualTo(60L);
            assertThat(policy.nullTtlSeconds()).isEqualTo(300L);
            assertThat(policy.lockWaitMillis()).isEqualTo(500L);
        }

        @Test
        void acceptsZeroForNonTtlFields() {
            CachePolicy policy = new CachePolicy(1L, 0L, 0L, 0L);

            assertThat(policy.jitterSeconds()).isZero();
            assertThat(policy.nullTtlSeconds()).isZero();
            assertThat(policy.lockWaitMillis()).isZero();
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -240L})
        void rejectsNonPositiveTtl(long ttlSeconds) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new CachePolicy(ttlSeconds, 60L, 300L, 500L));
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -60L})
        void rejectsNegativeJitter(long jitterSeconds) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new CachePolicy(240L, jitterSeconds, 300L, 500L));
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -300L})
        void rejectsNegativeNullTtl(long nullTtlSeconds) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new CachePolicy(240L, 60L, nullTtlSeconds, 500L));
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -500L})
        void rejectsNegativeLockWait(long lockWaitMillis) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new CachePolicy(240L, 60L, 300L, lockWaitMillis));
        }
    }

    private TestCell cell(Function<Collection<Long>, Map<Long, String>> source) {
        return new TestCell(POLICY, keys, source);
    }

    private TestCell cell(CachePolicy policy, Function<Collection<Long>, Map<Long, String>> source) {
        return new TestCell(policy, keys, source);
    }

    private String key(long id) {
        return PREFIX + GENERATION + ":" + id;
    }

    /**
     * 假 key 所有者：key 布局固定在测试里，模块只负责按角色取 key。
     */
    private static final class TestKeys implements CacheKeys {

        private String generation;

        private TestKeys(String generation) {
            this.generation = generation;
        }

        @Override
        public String currentKey(long id) {
            return keyFor(id, generation);
        }

        @Override
        public String keyFor(long id, String generation) {
            return PREFIX + generation + ":" + id;
        }

        @Override
        public String currentGeneration() {
            return generation;
        }

        @Override
        public void rotateGeneration() {
            generation = "gen-rotated";
        }
    }

    /**
     * 真实缓存单元：真策略 + 真 key 所有者 + 真解码 + 假回源。
     */
    private static final class TestCell implements CacheCell<String> {

        private final CachePolicy policy;
        private final CacheKeys keys;
        private final Function<Collection<Long>, Map<Long, String>> source;
        private final List<List<Long>> sourceRequests = new ArrayList<>();

        private TestCell(CachePolicy policy, CacheKeys keys,
                         Function<Collection<Long>, Map<Long, String>> source) {
            this.policy = policy;
            this.keys = keys;
            this.source = source;
        }

        @Override
        public CachePolicy policy() {
            return policy;
        }

        @Override
        public CacheKeys keys() {
            return keys;
        }

        @Override
        public String decode(Object cached) {
            return cached instanceof String value ? value : null;
        }

        @Override
        public Map<Long, String> loadFromSource(Collection<Long> ids) {
            sourceRequests.add(List.copyOf(ids));
            return new LinkedHashMap<>(source.apply(ids));
        }

        private int sourceCalls() {
            return sourceRequests.size();
        }

        private List<List<Long>> sourceRequests() {
            return sourceRequests;
        }
    }
}
