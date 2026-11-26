package com.example.mystore.service.impl;

import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.OrderIdempotency;
import com.example.mystore.mapper.OrderIdempotencyMapper;
import com.example.mystore.service.OrderIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderIdempotencyServiceImpl implements OrderIdempotencyService {

    private static final int CLAIM_ATTEMPTS = 3;

    private final OrderIdempotencyMapper idempotencyMapper;
    private final PlatformTransactionManager transactionManager;

    @Value("${order.idempotency.processing-timeout-minutes:10}")
    private long processingTimeoutMinutes = 10L;

    @Override
    public ClaimOutcome claim(Long userId, String idemKey, String requestHash) {
        String ownerToken = newOwnerToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime processingCutoff = now.minusMinutes(processingTimeoutMinutes);

        for (int attempt = 0; attempt < CLAIM_ATTEMPTS; attempt++) {
            OrderIdempotency record = idempotencyMapper.selectByUserAndKey(userId, idemKey);
            if (record == null) {
                try {
                    OrderIdempotency created = new OrderIdempotency();
                    created.setUserId(userId);
                    created.setIdemKey(idemKey);
                    created.setRequestHash(requestHash);
                    created.setState(OrderIdempotency.STATE_PROCESSING);
                    created.setOwnerToken(ownerToken);
                    created.setCreateTime(now);
                    created.setUpdateTime(now);
                    idempotencyMapper.insert(created);
                    return acquired(created.getId(), ownerToken);
                } catch (DuplicateKeyException e) {
                    // 并发请求抢先插入，重新读取后按记录状态处理
                    continue;
                }
            }

            switch (record.getState()) {
                case OrderIdempotency.STATE_SUCCEEDED -> {
                    if (Objects.equals(record.getRequestHash(), requestHash)) {
                        return new ClaimOutcome(ClaimOutcome.Kind.SUCCEEDED, record.getId(),
                                ownerToken, record.getOrderId());
                    }
                    return new ClaimOutcome(ClaimOutcome.Kind.CONFLICT, record.getId(),
                            ownerToken, null);
                }
                case OrderIdempotency.STATE_FAILED -> {
                    int updated = idempotencyMapper.claimFailedRecord(
                            record.getId(), ownerToken, requestHash, now);
                    if (updated == 1) {
                        return acquired(record.getId(), ownerToken);
                    }
                    // 被其他请求抢先重新认领，重新读取
                }
                case OrderIdempotency.STATE_PROCESSING -> {
                    if (record.getUpdateTime() != null
                            && record.getUpdateTime().isBefore(processingCutoff)) {
                        int updated = idempotencyMapper.takeOverExpiredProcessing(
                                record.getId(), record.getOwnerToken(), ownerToken,
                                requestHash, processingCutoff, now);
                        if (updated == 1) {
                            log.warn("回收超时幂等处理中记录, recordId={}, previousOwner={}",
                                    record.getId(), record.getOwnerToken());
                            return acquired(record.getId(), ownerToken);
                        }
                        continue;
                    }
                    return new ClaimOutcome(ClaimOutcome.Kind.PROCESSING, record.getId(),
                            ownerToken, null);
                }
                default -> throw new BusinessException("幂等记录状态异常，请刷新后重试");
            }
        }
        throw new BusinessException("请求冲突，请刷新后重试");
    }

    @Override
    public void markSucceeded(Long recordId, String ownerToken, Long orderId) {
        int updated = idempotencyMapper.markSucceeded(recordId, ownerToken, orderId, LocalDateTime.now());
        if (updated != 1) {
            // 记录已被超时回收或新请求接管：抛出以回滚本次订单创建
            throw new BusinessException("幂等记录状态已变更，请重试");
        }
    }

    @Override
    public void markFailed(Long recordId, String ownerToken) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            requiresNew.executeWithoutResult(status -> {
                int updated = idempotencyMapper.markFailed(recordId, ownerToken, LocalDateTime.now());
                if (updated != 1) {
                    log.warn("幂等记录未置失败（可能已被回收或接管）, recordId={}, ownerToken={}",
                            recordId, ownerToken);
                }
            });
        } catch (RuntimeException e) {
            // 置失败本身失败时保留原异常语义，记录将由超时回收兜底
            log.error("幂等记录置失败异常, recordId={}, ownerToken={}", recordId, ownerToken, e);
        }
    }

    @Override
    public int reclaimExpiredProcessing(LocalDateTime cutoff) {
        return idempotencyMapper.reclaimExpiredProcessing(cutoff, LocalDateTime.now());
    }

    @Override
    public int cleanupSettledBefore(LocalDateTime cutoff) {
        return idempotencyMapper.deleteSettledBefore(cutoff);
    }

    private static ClaimOutcome acquired(Long recordId, String ownerToken) {
        return new ClaimOutcome(ClaimOutcome.Kind.ACQUIRED, recordId, ownerToken, null);
    }

    private static String newOwnerToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    void setProcessingTimeoutMinutes(long processingTimeoutMinutes) {
        this.processingTimeoutMinutes = processingTimeoutMinutes;
    }
}
