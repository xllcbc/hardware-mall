package com.example.mystore.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁工具类（基于 Redisson）
 * 使用看门狗自动续期机制，避免业务执行时间过长导致锁失效
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    private final RedissonClient redissonClient;
    private static final String LOCK_PREFIX = "lock:";

    /**
     * 尝试获取分布式锁（非阻塞）
     * 使用 Redisson 看门狗自动续期：
     * - 默认锁有效期 30 秒
     * - 业务线程存活期间，每 10 秒自动续期到 30 秒
     * - 业务完成后需手动调用 unlock 释放
     *
     * @param key 锁的标识（业务唯一键）
     * @return true=获取成功，false=获取失败（锁已被其他线程持有）
     */
    public boolean tryLock(String key) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
        try {
            // waitTime=0: 不等待，拿不到立刻返回 false
            // leaseTime=-1: 启用看门狗自动续期（默认 30 秒）
            return lock.tryLock(0, -1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取锁被中断, key={}", key);
            return false;
        }
    }

    /**
     * 释放分布式锁
     * Redisson 内部通过线程 ID 判断锁持有者，只有持有该锁的线程才能释放
     * 避免业务执行超时后锁自动过期，其他线程获取锁后，原线程误删新锁
     *
     * @param key 锁的标识（与 tryLock 时传入的 key 一致）
     */
    public void unlock(String key) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        } else {
            log.warn("当前线程不持有该锁，跳过释放, key={}", key);
        }
    }
}
