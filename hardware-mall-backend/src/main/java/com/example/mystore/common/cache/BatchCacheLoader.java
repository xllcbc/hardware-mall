package com.example.mystore.common.cache;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 有界批量回填模块：缓存批量读取、单次回源、写回与解锁的唯一实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchCacheLoader {

    private final RedisUtil redisUtil;
    private final RedisLockUtil redisLockUtil;

    /**
     * 批量读取缓存，未命中的 ID 由缓存单元回源一次并写回。
     *
     * @param ids  有序 ID 列表；空或 {@code null} 返回空列表，全 {@code null} 返回等长全 {@code null} 列表
     * @param cell 本次调用临时组装的缓存单元
     * @return 与过滤掉 {@code null} 后的 ID 列表逐位对齐的结果
     */
    public <T> List<T> load(List<Long> ids, CacheCell<T> cell) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> requestedIds = ids.stream()
                .filter(Objects::nonNull)
                .toList();
        if (requestedIds.isEmpty()) {
            return new ArrayList<>(Collections.nCopies(ids.size(), null));
        }

        CachePolicy policy = cell.policy();
        CacheKeys cacheKeys = cell.keys();
        String generation = cacheKeys.currentGeneration();
        List<Object> cachedValues = redisUtil.multiGet(
                requestedIds.stream().map(id -> cacheKeys.keyFor(id, generation)).toList());
        List<T> result = new ArrayList<>(Collections.nCopies(requestedIds.size(), null));
        Set<Long> unresolvedIds = new LinkedHashSet<>();
        for (int i = 0; i < requestedIds.size(); i++) {
            Object value = cachedValues == null ? null : cachedValues.get(i);
            if (redisUtil.isNull(value)) {
                continue;
            }
            T decoded = cell.decode(value);
            if (decoded != null) {
                result.set(i, decoded);
            } else {
                unresolvedIds.add(requestedIds.get(i));
            }
        }
        if (unresolvedIds.isEmpty()) {
            return result;
        }

        List<Long> missingIds = unresolvedIds.stream().sorted().toList();
        List<String> acquiredLockKeys = new ArrayList<>();
        long deadline = deadline(policy.lockWaitMillis());
        try {
            for (Long id : missingIds) {
                String key = cacheKeys.keyFor(id, generation);
                if (!redisLockUtil.tryLock(key, remainingMillis(deadline))) {
                    log.warn("批量缓存回源等锁超时, id={}, maxWaitMs={}, 直接查库且不写缓存",
                            id, policy.lockWaitMillis());
                    mergeLoaded(requestedIds, result, cell.loadFromSource(missingIds),
                            false, cell, generation);
                    return result;
                }
                acquiredLockKeys.add(key);
            }

            List<Long> stillMissing = recheckAfterLock(
                    requestedIds, result, missingIds, cell, generation);
            if (!stillMissing.isEmpty()) {
                mergeLoaded(requestedIds, result, cell.loadFromSource(stillMissing), true, cell, generation);
            }
            return result;
        } finally {
            for (int i = acquiredLockKeys.size() - 1; i >= 0; i--) {
                redisLockUtil.unlock(acquiredLockKeys.get(i));
            }
        }
    }

    private <T> List<Long> recheckAfterLock(List<Long> requestedIds, List<T> result, List<Long> missingIds,
                                            CacheCell<T> cell, String generation) {
        List<Object> cachedValues = redisUtil.multiGet(missingIds.stream()
                .map(id -> cell.keys().keyFor(id, generation))
                .toList());
        List<Long> stillMissing = new ArrayList<>();
        for (int i = 0; i < missingIds.size(); i++) {
            Object value = cachedValues == null ? null : cachedValues.get(i);
            Long id = missingIds.get(i);
            if (redisUtil.isNull(value)) {
                continue;
            }
            T decoded = cell.decode(value);
            if (decoded != null) {
                fillResultById(requestedIds, result, id, decoded);
            } else {
                stillMissing.add(id);
            }
        }
        return stillMissing;
    }

    private <T> void fillResultById(List<Long> requestedIds, List<T> result, Long id, T value) {
        for (int i = 0; i < requestedIds.size(); i++) {
            if (Objects.equals(requestedIds.get(i), id)) {
                result.set(i, value);
            }
        }
    }

    private <T> void mergeLoaded(List<Long> requestedIds, List<T> result, Map<Long, T> loaded,
                                 boolean writeCache, CacheCell<T> cell, String generation) {
        Map<Long, T> loadedById = loaded == null ? Collections.emptyMap() : loaded;
        for (int i = 0; i < requestedIds.size(); i++) {
            if (result.get(i) != null) {
                continue;
            }
            T value = loadedById.get(requestedIds.get(i));
            if (value != null) {
                result.set(i, value);
            }
        }
        if (!writeCache) {
            return;
        }
        CachePolicy policy = cell.policy();
        Set<Long> writtenIds = new LinkedHashSet<>();
        for (Long id : requestedIds) {
            T value = loadedById.get(id);
            if (value != null && writtenIds.add(id)) {
                redisUtil.setWithJitter(cell.keys().keyFor(id, generation), value,
                        policy.ttlSeconds(), TimeUnit.SECONDS, policy.jitterSeconds());
            }
        }
        if (policy.nullTtlSeconds() <= 0) {
            return;
        }
        // 只对本次回源确认不存在、且结果仍为空的 ID 写哨兵；已命中的 ID 不会被误伤。
        for (int i = 0; i < requestedIds.size(); i++) {
            if (result.get(i) != null) {
                continue;
            }
            Long id = requestedIds.get(i);
            if (loadedById.containsKey(id)) {
                continue;
            }
            if (writtenIds.add(id)) {
                redisUtil.set(cell.keys().keyFor(id, generation), RedisConstants.CACHE_NULL,
                        policy.nullTtlSeconds(), TimeUnit.SECONDS);
            }
        }
    }

    private long deadline(long maxWaitMillis) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, maxWaitMillis));
    }

    private long remainingMillis(long deadline) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            return 0L;
        }
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
    }
}
