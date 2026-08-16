package com.example.mystore.util;

import com.example.mystore.common.constant.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisLockUtil redisLockUtil;

    /**
     * 未抢到锁时的告警轮数，达到后只告警，循环继续等待缓存重建
     */
    @Value("${cache.query.lock-warn-loops:10}")
    private int lockWarnLoops = 10;

    /**
     * 每轮随机休眠时长上限（毫秒），实际取 [sleepMs/2, sleepMs]，抖动避免唤醒风暴
     */
    @Value("${cache.query.lock-sleep-ms:100}")
    private long lockSleepMs = 100L;

    private static final long ALERT_COOLDOWN_SECONDS = 30L;

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

    /**
     * 按模式删除 key，使用 SCAN 避免阻塞（生产环境禁用 KEYS）
     *
     * @param pattern 匹配模式，如 "product:list:*"
     * @return 删除的 key 数量
     */
    public long deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Set<String>>) connection -> {
            Set<String> matched = new java.util.HashSet<>();
            try (org.springframework.data.redis.core.Cursor<byte[]> cursor =
                         connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                                 .match(pattern).count(100).build())) {
                while (cursor.hasNext()) {
                    matched.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return matched;
        });
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        Long deleted = redisTemplate.delete(keys);
        return deleted == null ? 0 : deleted;
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
     * 带防护的查询缓存：穿透（空值哨兵）+ 击穿（互斥锁 + 循环等待）+ 雪崩（TTL 抖动）
     * <p>缓存 miss 时通过分布式锁保证只有一个请求回源 DB；未抢到锁的请求休眠后重读缓存、
     * 未命中再抢锁，循环等待重建完成；等待轮数达到告警阈值时记录告警，但不会绕过互斥锁直接查库。</p>
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

        // 击穿：首次尝试抢锁，成功则重建（double-check 防止重复回源）
        if (redisLockUtil.tryLock(key)) {
            return rebuildWithLock(key, type, ttlSeconds, nullTtlSeconds, maxJitterSeconds, dbQuery);
        }

        // 抢锁失败：休眠 -> 重读缓存 -> 再抢锁，持续循环等待重建完成
        int loops = 0;
        while (true) {
            sleepQuietly(nextSleep());
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
            if (redisLockUtil.tryLock(key)) {
                return rebuildWithLock(key, type, ttlSeconds, nullTtlSeconds, maxJitterSeconds, dbQuery);
            }
            if (++loops == lockWarnLoops) {
                alertRebuildTimeout(key, loops);
            }
        }
    }

    private <T> T rebuildWithLock(String key, Class<T> type, long ttlSeconds,
                                  long nullTtlSeconds, long maxJitterSeconds, Supplier<T> dbQuery) {
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

    private long nextSleep() {
        long half = Math.max(1L, lockSleepMs / 2);
        return ThreadLocalRandom.current().nextLong(half, lockSleepMs + 1);
    }

    private void alertRebuildTimeout(String key, int loops) {
        // 每 key 冷却 30s，防止持续异常时刷爆日志（与钉钉告警防抖同款写法）
        String cooldownKey = "alert:cache:rebuild:" + key;
        boolean first = Boolean.TRUE.equals(setIfAbsent(cooldownKey, "1", ALERT_COOLDOWN_SECONDS, TimeUnit.SECONDS));
        if (first) {
            log.warn("缓存重建等待过久, key={}, loops={}, 继续等待缓存重建", key, loops);
        }
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
            throw new IllegalStateException("等待缓存重建时被中断", e);
        }
    }

    void setLockWarnLoops(int lockWarnLoops) {
        this.lockWarnLoops = lockWarnLoops;
    }

    void setLockSleepMs(long lockSleepMs) {
        this.lockSleepMs = lockSleepMs;
    }
}
