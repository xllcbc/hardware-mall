package com.example.mystore.common.cache;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
        List<T> result = new ArrayList<>(Collections.nCopies(requestedIds.size(), null));
        LoadContext<T> context = new LoadContext<>(
                requestedIds, result, cell, policy, id -> cacheKeys.keyFor(id, generation));

        Set<Long> unresolvedIds = readCachedValues(context);
        if (unresolvedIds.isEmpty()) {
            return result;
        }
        List<Long> missingIds = unresolvedIds.stream().sorted().toList();
        return loadMissing(context, missingIds);
    }

    private <T> Set<Long> readCachedValues(LoadContext<T> context) {
        List<Long> requestedIds = context.requestedIds();
        List<Object> cachedValues = redisUtil.multiGet(
                requestedIds.stream().map(context.keyOf()).toList());
        Set<Long> unresolvedIds = new LinkedHashSet<>();
        for (int i = 0; i < requestedIds.size(); i++) {
            Object value = cachedValues == null ? null : cachedValues.get(i);
            if (redisUtil.isNull(value)) {
                continue;
            }
            T decoded = context.cell().decode(value);
            if (decoded != null) {
                context.result().set(i, decoded);
            } else {
                unresolvedIds.add(requestedIds.get(i));
            }
        }
        return unresolvedIds;
    }

    /**
     * 按 ID 升序取锁，整批共享一个等待预算；任一锁取不到就停止取锁，
     * 把所有未解析 ID 一次性交给回源函数，且不写任何缓存。
     */
    private <T> List<T> loadMissing(LoadContext<T> context, List<Long> missingIds) {
        List<String> acquiredLockKeys = new ArrayList<>();
        long deadline = deadline(context.policy().lockWaitMillis());
        try {
            for (Long id : missingIds) {
                String key = context.keyOf().apply(id);
                if (!redisLockUtil.tryLock(key, remainingMillis(deadline))) {
                    log.warn("批量缓存回源等锁超时, id={}, maxWaitMs={}, 直接查库且不写缓存",
                            id, context.policy().lockWaitMillis());
                    applySourceResult(context, missingIds,
                            context.cell().loadFromSource(missingIds), false);
                    return context.result();
                }
                acquiredLockKeys.add(key);
            }

            List<Long> stillMissing = recheckAfterLock(context, missingIds);
            if (!stillMissing.isEmpty()) {
                applySourceResult(context, stillMissing,
                        context.cell().loadFromSource(stillMissing), true);
            }
            return context.result();
        } finally {
            for (int i = acquiredLockKeys.size() - 1; i >= 0; i--) {
                redisLockUtil.unlock(acquiredLockKeys.get(i));
            }
        }
    }

    private <T> List<Long> recheckAfterLock(LoadContext<T> context, List<Long> missingIds) {
        List<Object> cachedValues = redisUtil.multiGet(
                missingIds.stream().map(context.keyOf()).toList());
        List<Long> stillMissing = new ArrayList<>();
        for (int i = 0; i < missingIds.size(); i++) {
            Object value = cachedValues == null ? null : cachedValues.get(i);
            Long id = missingIds.get(i);
            if (redisUtil.isNull(value)) {
                continue;
            }
            T decoded = context.cell().decode(value);
            if (decoded != null) {
                fillResultById(context, id, decoded);
            } else {
                stillMissing.add(id);
            }
        }
        return stillMissing;
    }

    private <T> void fillResultById(LoadContext<T> context, Long id, T value) {
        List<Long> requestedIds = context.requestedIds();
        for (int i = 0; i < requestedIds.size(); i++) {
            if (Objects.equals(requestedIds.get(i), id)) {
                context.result().set(i, value);
            }
        }
    }

    /**
     * 合并回源结果。只有真正交给回源函数的 ID 才会写值或写空值哨兵：
     * 缓存里已有哨兵的 ID 不会被回源，也不会被顺手续期。
     */
    private <T> void applySourceResult(LoadContext<T> context, List<Long> sourcedIds,
                                       Map<Long, T> loaded, boolean writeCache) {
        Map<Long, T> loadedById = loaded == null ? Collections.emptyMap() : loaded;
        Set<Long> sourced = new HashSet<>(sourcedIds);
        CachePolicy policy = context.policy();
        for (int i = 0; i < context.requestedIds().size(); i++) {
            Long id = context.requestedIds().get(i);
            T value = loadedById.get(id);
            if (context.result().get(i) == null && value != null) {
                context.result().set(i, value);
            }
            if (!writeCache || !sourced.contains(id)) {
                continue;
            }
            if (value != null) {
                redisUtil.setWithJitter(context.keyOf().apply(id), value,
                        policy.ttlSeconds(), TimeUnit.SECONDS, policy.jitterSeconds());
            } else if (context.result().get(i) == null && policy.nullTtlSeconds() > 0) {
                redisUtil.set(context.keyOf().apply(id), RedisConstants.CACHE_NULL,
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

    /**
     * 单次调用内共享的请求上下文：有序 ID、对齐结果、缓存单元、策略与 key 函数。
     */
    private record LoadContext<T>(List<Long> requestedIds, List<T> result, CacheCell<T> cell,
                                  CachePolicy policy, Function<Long, String> keyOf) {
    }
}
