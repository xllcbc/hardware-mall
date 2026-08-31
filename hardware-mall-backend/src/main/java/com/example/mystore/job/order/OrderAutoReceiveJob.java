package com.example.mystore.job.order;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.OrderService;
import com.example.mystore.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 已发货订单超期自动收货定时任务
 * 扫描发货超过 N 天仍未确认收货的订单，自动置为已完成
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderAutoReceiveJob {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final RedisLockUtil redisLockUtil;

    @Value("${order.auto-receive.days:7}")
    private int autoReceiveDays;

    // 单次处理上限
    private static final int BATCH_SIZE = 100;
    // 分布式锁 key
    private static final String LOCK_KEY = "job:order-auto-receive";

    /**
     * 每 1 小时执行一次，扫描发货超期未收货订单并自动完成
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void autoReceiveShippedOrders() {
        boolean locked = redisLockUtil.tryLock(LOCK_KEY);
        if (!locked) {
            log.debug("未获取到分布式锁，跳过本次任务");
            return;
        }

        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusDays(autoReceiveDays);
            List<Order> staleOrders = orderMapper.selectStaleShippedOrders(
                    StatusConstants.ORDER_SHIPPED, beforeTime, BATCH_SIZE);

            if (staleOrders.isEmpty()) {
                log.debug("没有超期未收货订单");
                return;
            }

            log.info("发现 {} 条超期未收货订单，开始自动收货", staleOrders.size());
            int successCount = 0;
            int skipCount = 0;

            for (Order order : staleOrders) {
                try {
                    boolean success = orderService.autoConfirmReceive(order.getId());
                    if (success) {
                        successCount++;
                        log.info("自动收货成功, orderId={}", order.getId());
                    } else {
                        skipCount++;
                        log.info("自动收货跳过（订单已非已发货状态）, orderId={}", order.getId());
                    }
                } catch (Exception e) {
                    log.error("自动收货失败, orderId={}", order.getId(), e);
                }
            }

            log.info("自动收货任务完成, 成功={}, 跳过={}, 失败={}",
                    successCount, skipCount, staleOrders.size() - successCount - skipCount);

        } finally {
            redisLockUtil.unlock(LOCK_KEY);
        }
    }
}
