package com.example.mystore.util;

import com.example.mystore.common.constant.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisLockUtil redisLockUtil;

    private static final int LOCK_RETRY_TIMES = 5;
    private static final long LOCK_RETRY_SLEEP_MS = 50L;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        return (T) get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void delete(Collection<String> keys) {
        redisTemplate.delete(keys);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    public long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    public Long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long incr(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    public Long decr(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }

    public Long decr(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    public void sAdd(String key, Object... values) {
        redisTemplate.opsForSet().add(key, values);
    }

    public <T> Set<T> sMembers(String key, Class<T> clazz) {
        return (Set<T>) redisTemplate.opsForSet().members(key);
    }

    public void sRemove(String key, Object... values) {
        redisTemplate.opsForSet().remove(key, values);
    }

    public Boolean sIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    public void hDelete(String key, Object... fields) {
        redisTemplate.opsForHash().delete(key, fields);
    }

    public boolean isNull(Object value) {
        return RedisConstants.CACHE_NULL.equals(value);
    }

    public void setWithJitter(String key, Object value, long baseTtl, TimeUnit unit, long maxJitterSeconds) {
        long jitter = (long) (Math.random() * maxJitterSeconds);
        long ttlSeconds = unit.toSeconds(baseTtl) + jitter;
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 带防护的查询缓存：穿透（空值哨兵）+ 击穿（互斥锁 + double-check）+ 雪崩（TTL 抖动）
     * <p>缓存 miss 时通过分布式锁保证只有一个请求回源 DB，其余请求等待重读缓存；
     * 锁获取失败时重试读缓存，仍无则兜底直查库（不写缓存），保证锁永不阻塞请求。</p>
     *
     * @param key        缓存 key
     * @param type       缓存值类型（用于脏缓存检测）
     * @param ttlSeconds 正常缓存 TTL（秒）
     * @param dbQuery    查库逻辑，返回 null 表示数据不存在（会写空值哨兵）
     * @param <T>        缓存值类型
     */
    public <T> T queryWithCache(String key, Class<T> type, long ttlSeconds, Supplier<T> dbQuery) {
        return queryWithCache(key, type, ttlSeconds, RedisConstants.CACHE_NULL_TTL, RedisConstants.CACHE_JITTER_MAX, dbQuery);
    }

    /**
     * 带防护的查询缓存，可自定义哨兵 TTL 与抖动范围
     *
     * @param nullTtlSeconds   空值哨兵 TTL（秒），&lt;=0 表示不启用穿透防护
     * @param maxJitterSeconds 正常缓存 TTL 抖动上限（秒）
     */
    public <T> T queryWithCache(String key, Class<T> type, long ttlSeconds,
                                long nullTtlSeconds, long maxJitterSeconds, Supplier<T> dbQuery) {
        // 穿透：读缓存，命中哨兵直接拦截，命中脏缓存删除后重建
        Object cached = get(key);
        if (cached != null) {
            if (isNull(cached)) {
                return null;
            }
            T hit = castValue(key, type, cached);
            if (hit != null) {
                return hit;
            }
        }

        // 击穿：抢锁重建，double-check 防止重复回源
        if (redisLockUtil.tryLock(key)) {
            try {
                Object again = get(key);
                if (again != null) {
                    if (isNull(again)) {
                        return null;
                    }
                    T hit = castValue(key, type, again);
                    if (hit != null) {
                        return hit;
                    }
                }
                T value = dbQuery.get();
                if (value == null) {
                    if (nullTtlSeconds > 0) {
                        set(key, RedisConstants.CACHE_NULL, nullTtlSeconds, TimeUnit.SECONDS);
                    }
                    return null;
                }
                setWithJitter(key, value, ttlSeconds, TimeUnit.SECONDS, maxJitterSeconds);
                return value;
            } finally {
                redisLockUtil.unlock(key);
            }
        }

        // 抢锁失败：短暂重试读缓存，仍无则兜底直查库（不写缓存）
        return retryReadCache(key, type, dbQuery);
    }

    private <T> T retryReadCache(String key, Class<T> type, Supplier<T> dbQuery) {
        for (int i = 0; i < LOCK_RETRY_TIMES; i++) {
            sleepQuietly(LOCK_RETRY_SLEEP_MS);
            Object again = get(key);
            if (again != null) {
                if (isNull(again)) {
                    return null;
                }
                T hit = castValue(key, type, again);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return dbQuery.get();
    }

    private <T> T castValue(String key, Class<T> type, Object cached) {
        if (type.isInstance(cached)) {
            return type.cast(cached);
        }
        log.warn("缓存类型不匹配, key={}, 删除脏缓存", key);
        delete(key);
        return null;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}