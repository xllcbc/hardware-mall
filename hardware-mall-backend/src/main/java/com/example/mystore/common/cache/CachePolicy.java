package com.example.mystore.common.cache;

/**
 * 批量缓存回填策略值对象。
 *
 * <p>TTL、抖动、空值哨兵 TTL 与锁等待预算在构造时校验，写错的常量会立刻失败，
 * 而不是在运行期悄悄改变缓存陈旧窗口。</p>
 *
 * @param ttlSeconds      正常缓存 TTL（秒），必须大于 0
 * @param jitterSeconds   TTL 抖动上限（秒），不能为负数；0 表示不抖动
 * @param nullTtlSeconds  空值哨兵 TTL（秒），不能为负数；0 表示不写空值哨兵
 * @param lockWaitMillis  整批共享的锁等待预算（毫秒），不能为负数
 */
public record CachePolicy(long ttlSeconds, long jitterSeconds, long nullTtlSeconds, long lockWaitMillis) {

    public CachePolicy {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds 必须大于 0: " + ttlSeconds);
        }
        if (jitterSeconds < 0) {
            throw new IllegalArgumentException("jitterSeconds 不能为负数: " + jitterSeconds);
        }
        if (nullTtlSeconds < 0) {
            throw new IllegalArgumentException("nullTtlSeconds 不能为负数: " + nullTtlSeconds);
        }
        if (lockWaitMillis < 0) {
            throw new IllegalArgumentException("lockWaitMillis 不能为负数: " + lockWaitMillis);
        }
    }
}
