package com.example.mystore.service;

import java.time.LocalDateTime;

/**
 * 下单幂等记录：数据库是权威，Redis 仅作短时快锁。
 */
public interface OrderIdempotencyService {

    /**
     * 按“用户 + 幂等键”认领请求。
     */
    ClaimOutcome claim(Long userId, String idemKey, String requestHash);

    /**
     * 必须与订单创建在同一事务内调用；更新失败会抛出异常，由外层事务回滚订单。
     */
    void markSucceeded(Long recordId, String ownerToken, Long orderId);

    /**
     * 提交前失败时用独立事务把记录置为失败，允许同一幂等键重试。
     */
    void markFailed(Long recordId, String ownerToken);

    /**
     * 仅当本次请求实际持有 Redis 快锁且值仍是自己的 ownerToken 时才删除。
     */
    void releaseLock(Long userId, String idemKey, String ownerToken, boolean lockAcquired);

    int reclaimExpiredProcessing(LocalDateTime cutoff);

    int cleanupSettledBefore(LocalDateTime cutoff);

    record ClaimOutcome(Kind kind, Long recordId, String ownerToken, Long orderId, boolean lockAcquired) {

        public enum Kind {
            /** 本次请求获得处理权，可继续创建订单 */
            ACQUIRED,
            /** 同用户 + 同幂等键 + 同指纹，返回原订单 */
            SUCCEEDED,
            /** 另一请求仍在处理中 */
            PROCESSING,
            /** 同幂等键被用于不同请求指纹 */
            CONFLICT
        }

        public boolean isAcquired() {
            return kind == Kind.ACQUIRED;
        }
    }
}
