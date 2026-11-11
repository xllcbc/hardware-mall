package com.example.mystore.job.pay;

import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.service.PayService;
import com.example.mystore.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 退款对账兜底定时任务
 * 以支付记录为入口扫描并收敛退款:
 *   退款中的记录(不限订单状态) + 已支付且订单已取消的记录
 * 查询微信退款权威状态: 成功/处理中/无退款单/明确失败/查询异常分别处理
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundReconcileJob {

    private final PaymentRecordMapper paymentRecordMapper;
    private final PayService payService;
    private final RedisLockUtil redisLockUtil;

    /** 滞留判定宽限期(分钟): 避开正常回调处理中的记录 */
    private static final int GRACE_MINUTES = 10;
    /** 单轮处理上限 */
    private static final int BATCH_SIZE = 100;
    /** 单次执行最多轮数, 防无限循环 */
    private static final int MAX_ROUNDS = 20;
    /** 分布式锁 key */
    private static final String LOCK_KEY = "job:refund-reconcile";

    /**
     * 默认每小时执行一次, 可通过 order.refund-reconcile.cron 覆盖
     */
    @Scheduled(cron = "${order.refund-reconcile.cron:0 0 * * * ?}")
    public void reconcileRefundRecords() {
        boolean locked = redisLockUtil.tryLock(LOCK_KEY);
        if (!locked) {
            log.debug("未获取到分布式锁，跳过本次退款对账");
            return;
        }

        try {
            int successCount = 0;
            int failCount = 0;
            int round = 0;
            Set<Long> processedRecordIds = new HashSet<>();

            while (round++ < MAX_ROUNDS) {
                LocalDateTime beforeTime = LocalDateTime.now().minusMinutes(GRACE_MINUTES);
                // 处理中/查询失败的记录状态不变, 会在下一轮再次命中; 同一轮内不重复查询
                List<PaymentRecord> staleRecords = paymentRecordMapper.selectStaleRefundRecords(
                                beforeTime, BATCH_SIZE)
                        .stream()
                        .filter(record -> !processedRecordIds.contains(record.getId()))
                        .toList();

                if (staleRecords.isEmpty()) {
                    break;
                }

                log.info("退款对账: 第 {} 轮发现 {} 条滞留退款记录", round, staleRecords.size());

                for (PaymentRecord record : staleRecords) {
                    processedRecordIds.add(record.getId());
                    try {
                        payService.reconcileRefund(record);
                        successCount++;
                    } catch (Exception e) {
                        failCount++;
                        log.error("退款对账失败, paymentRecordId={}, orderId={}",
                                record.getId(), record.getOrderId(), e);
                    }
                }

                if (staleRecords.size() < BATCH_SIZE) {
                    break;
                }
            }

            log.info("退款对账完成: 成功={}, 失败={}", successCount, failCount);
        } finally {
            redisLockUtil.unlock(LOCK_KEY);
        }
    }
}
