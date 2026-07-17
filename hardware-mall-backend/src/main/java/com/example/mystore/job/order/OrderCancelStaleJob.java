package com.example.mystore.job.order;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.OrderService;
import com.example.mystore.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消定时任务
 * 扫描超期的待付款订单，自动取消并恢复库存
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelStaleJob {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final RedisLockUtil redisLockUtil;

    // 超时阈值：30分钟
    private static final int STALE_MINUTES = 30;
    // 单次处理上限
    private static final int BATCH_SIZE = 100;
    // 分布式锁 key
    private static final String LOCK_KEY = "job:order-cancel-stale";

    /**
     * 每 10 分钟执行一次，扫描超期待付款订单并自动取消
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void cancelStaleOrders() {
        boolean locked = redisLockUtil.tryLock(LOCK_KEY);
        if (!locked) {
            log.debug("未获取到分布式锁，跳过本次任务");
            return;
        }

        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusMinutes(STALE_MINUTES);
            List<Order> staleOrders = orderMapper.selectStalePendingOrders(
                    StatusConstants.ORDER_PENDING_PAYMENT, beforeTime, BATCH_SIZE);

            if (staleOrders.isEmpty()) {
                log.debug("没有超期待付款订单");
                return;
            }

            log.info("发现 {} 条超期待付款订单，开始自动取消", staleOrders.size());
            int successCount = 0;
            int skipCount = 0;

            for (Order order : staleOrders) {
                try {
                    boolean success = orderService.autoCancelOrder(
                            order.getId(), "超时未支付，系统自动取消");
                    if (success) {
                        successCount++;
                        log.info("自动取消成功, orderId={}", order.getId());
                    } else {
                        skipCount++;
                        log.info("自动取消跳过（订单已非待付款状态）, orderId={}", order.getId());
                    }
                } catch (Exception e) {
                    log.error("自动取消失败, orderId={}", order.getId(), e);
                }
            }

            log.info("自动取消任务完成, 成功={}, 跳过={}, 失败={}",
                    successCount, skipCount, staleOrders.size() - successCount - skipCount);

        } finally {
            redisLockUtil.unlock(LOCK_KEY);
        }
    }
}
