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
 * 扫描发货超期未确认收货的订单，自动置为已完成。
 * 周期按发货方式分级(对齐微信结算规则): 同城配送/自提 T+2, 快递 T+10(预留)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderAutoReceiveJob {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final RedisLockUtil redisLockUtil;

    /** 同城配送/自提 自动确认收货周期(天) */
    @Value("${order.auto-receive.local-days:2}")
    private int localDays;

    /** 快递 自动确认收货周期(天), 预留 */
    @Value("${order.auto-receive.express-days:10}")
    private int expressDays;

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
            // 以最短周期为扫描下界, 命中后再按发货方式精确判定是否到期
            int minDays = Math.min(localDays, expressDays);
            LocalDateTime beforeTime = LocalDateTime.now().minusDays(minDays);
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
                    if (!isStaleEnough(order)) {
                        skipCount++;
                        continue;
                    }
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

    /** 按发货方式判定是否已达自动收货周期 */
    private boolean isStaleEnough(Order order) {
        if (order.getShipTime() == null) {
            return false;
        }
        int days = isLocalOrPickup(order.getDeliveryType()) ? localDays : expressDays;
        return order.getShipTime().isBefore(LocalDateTime.now().minusDays(days));
    }

    private boolean isLocalOrPickup(Integer deliveryType) {
        return deliveryType != null
                && (deliveryType == StatusConstants.DELIVERY_TYPE_LOCAL
                    || deliveryType == StatusConstants.DELIVERY_TYPE_PICKUP);
    }
}
