package com.example.mystore.job.pay;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.PayService;
import com.example.mystore.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款对账兜底定时任务
 * 扫描滞留"退款中"的订单, 查询微信退款权威状态并收敛:
 *   SUCCESS → 确认退款(6→7 + 还库存); CLOSED/ABNORMAL 或微信无此单 → 回退(6→9 退款失败)
 * 覆盖两类场景: 退款回调丢失 / 外呼超时导致本地结果未知
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundReconcileJob {

    private final OrderMapper orderMapper;
    private final PayService payService;
    private final RedisLockUtil redisLockUtil;

    /** 滞留判定宽限期(分钟): 避开正常回调处理中的订单 */
    private static final int GRACE_MINUTES = 10;
    /** 单轮处理上限 */
    private static final int BATCH_SIZE = 100;
    /** 单次执行最多轮数, 防无限循环 */
    private static final int MAX_ROUNDS = 20;
    /** 分布式锁 key */
    private static final String LOCK_KEY = "job:refund-reconcile";

    /**
     * 默认每天 03:00 执行一次, 可通过 order.refund-reconcile.cron 覆盖
     */
    @Scheduled(cron = "${order.refund-reconcile.cron:0 0 3 * * ?}")
    public void reconcileRefundingOrders() {
        boolean locked = redisLockUtil.tryLock(LOCK_KEY);
        if (!locked) {
            log.debug("未获取到分布式锁，跳过本次退款对账");
            return;
        }

        try {
            int successCount = 0;
            int failCount = 0;
            int round = 0;

            while (round++ < MAX_ROUNDS) {
                LocalDateTime beforeTime = LocalDateTime.now().minusMinutes(GRACE_MINUTES);
                List<Order> staleOrders = orderMapper.selectStaleRefundingOrders(
                        StatusConstants.ORDER_REFUNDING, beforeTime, BATCH_SIZE);

                if (staleOrders.isEmpty()) {
                    break;
                }

                log.info("退款对账: 第 {} 轮发现 {} 条滞留退款中订单", round, staleOrders.size());

                for (Order order : staleOrders) {
                    try {
                        payService.reconcileRefund(order.getId());
                        successCount++;
                    } catch (Exception e) {
                        failCount++;
                        log.error("退款对账失败, orderId={}", order.getId(), e);
                    }
                }

                if (staleOrders.size() < BATCH_SIZE) {
                    break;
                }
            }

            log.info("退款对账完成: 成功={}, 失败={}", successCount, failCount);
        } finally {
            redisLockUtil.unlock(LOCK_KEY);
        }
    }
}
