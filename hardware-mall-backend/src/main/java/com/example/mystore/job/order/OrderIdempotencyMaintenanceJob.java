package com.example.mystore.job.order;

import com.example.mystore.service.OrderIdempotencyService;
import com.example.mystore.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 幂等记录维护：超时 PROCESSING 回收为 FAILED；成功/失败记录保留 7 天后清理。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderIdempotencyMaintenanceJob {

    private static final String RECLAIM_LOCK_KEY = "job:order-idempotency-reclaim";
    private static final String CLEANUP_LOCK_KEY = "job:order-idempotency-cleanup";

    private final OrderIdempotencyService orderIdempotencyService;
    private final RedisLockUtil redisLockUtil;

    @Value("${order.idempotency.processing-timeout-minutes:10}")
    private long processingTimeoutMinutes = 10L;

    @Value("${order.idempotency.retention-days:7}")
    private long retentionDays = 7L;

    /**
     * 每 5 分钟回收一次超时处理中记录，避免进程崩溃后同一幂等键永久卡住。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void reclaimExpiredProcessing() {
        if (!redisLockUtil.tryLock(RECLAIM_LOCK_KEY)) {
            log.debug("未获取到幂等记录回收锁，跳过本次任务");
            return;
        }
        try {
            int reclaimed = orderIdempotencyService.reclaimExpiredProcessing(
                    LocalDateTime.now().minusMinutes(processingTimeoutMinutes));
            if (reclaimed > 0) {
                log.info("回收超时幂等处理中记录, count={}", reclaimed);
            }
        } finally {
            redisLockUtil.unlock(RECLAIM_LOCK_KEY);
        }
    }

    /**
     * 每日清理成功/失败记录，控制幂等表增长。
     */
    @Scheduled(cron = "${order.idempotency.cleanup-cron:0 30 4 * * *}")
    public void cleanupSettledRecords() {
        if (!redisLockUtil.tryLock(CLEANUP_LOCK_KEY)) {
            log.debug("未获取到幂等记录清理锁，跳过本次任务");
            return;
        }
        try {
            int deleted = orderIdempotencyService.cleanupSettledBefore(
                    LocalDateTime.now().minusDays(retentionDays));
            if (deleted > 0) {
                log.info("清理过期幂等记录, count={}", deleted);
            }
        } finally {
            redisLockUtil.unlock(CLEANUP_LOCK_KEY);
        }
    }

    void setProcessingTimeoutMinutes(long processingTimeoutMinutes) {
        this.processingTimeoutMinutes = processingTimeoutMinutes;
    }

    void setRetentionDays(long retentionDays) {
        this.retentionDays = retentionDays;
    }
}
