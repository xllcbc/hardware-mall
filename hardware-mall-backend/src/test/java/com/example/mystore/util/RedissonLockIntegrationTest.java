package com.example.mystore.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedissonLockIntegrationTest {

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Autowired
    private RedissonClient redissonClient;

    @BeforeEach
    void setUp() {
        redissonClient.getKeys().flushall();
    }

    @Test
    void testTryLock_SuccessAndUnlock() {
        boolean locked = redisLockUtil.tryLock("test:lock:1");
        assertThat(locked).isTrue();

        redisLockUtil.unlock("test:lock:1");
        // 再次获取应该成功（已释放）
        boolean lockedAgain = redisLockUtil.tryLock("test:lock:1");
        assertThat(lockedAgain).isTrue();
        redisLockUtil.unlock("test:lock:1");
    }

    @Test
    void testTryLock_DoubleLockSameThread() {
        // Redisson 可重入锁：同一线程可以多次获取
        boolean first = redisLockUtil.tryLock("test:lock:reentrant");
        assertThat(first).isTrue();

        // 同一线程再次获取同一把锁（可重入）
        boolean second = redisLockUtil.tryLock("test:lock:reentrant");
        assertThat(second).isTrue();

        redisLockUtil.unlock("test:lock:reentrant");
        redisLockUtil.unlock("test:lock:reentrant");
    }

    @Test
    void testTryLock_ConcurrentCompetition() throws InterruptedException {
        String lockKey = "test:lock:concurrent";
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    boolean locked = redisLockUtil.tryLock(lockKey);
                    if (locked) {
                        successCount.incrementAndGet();
                        // 持有锁一小段时间
                        Thread.sleep(100);
                        redisLockUtil.unlock(lockKey);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // 所有线程同时开始竞争
        boolean allFinished = endLatch.await(10, TimeUnit.SECONDS);
        assertThat(allFinished).isTrue();
        // 只有一个线程能成功获取锁（非重入、不等待）
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    void testUnlock_NotHeldByCurrentThread() {
        // 先获取锁
        redisLockUtil.tryLock("test:lock:safe");
        // 当前线程释放
        redisLockUtil.unlock("test:lock:safe");

        // 再次释放（已不持有）应该安全不报错
        redisLockUtil.unlock("test:lock:safe");
        // 测试通过即表示没有异常抛出
    }
}
