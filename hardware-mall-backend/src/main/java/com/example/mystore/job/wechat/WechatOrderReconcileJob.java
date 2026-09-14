package com.example.mystore.job.wechat;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.OrderService;
import com.example.mystore.service.WechatOrderShippingService;
import com.example.mystore.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微信订单对账任务
 * 扫描已上报微信(wechat_order_state=2)的已发货订单, 用 get_order 取权威 order_state:
 *   3确认收货/4交易完成 → 本地 3→4; 同时把微信状态写入 wechat_order_state 供展示/对账
 * 兜底用户在微信订单中心确认收货(未经本小程序确认组件)的场景
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WechatOrderReconcileJob {

    private final OrderMapper orderMapper;
    private final WechatOrderShippingService wechatOrderShippingService;
    private final OrderService orderService;
    private final RedisLockUtil redisLockUtil;

    private static final int GRACE_MINUTES = 10;
    private static final int BATCH_SIZE = 100;
    private static final String LOCK_KEY = "job:wechat-order-reconcile";

    /** 默认每小时执行一次, 可通过 order.wechat-reconcile.cron 覆盖 */
    @Scheduled(cron = "${order.wechat-reconcile.cron:0 0 * * * ?}")
    public void reconcileShippedOrders() {
        boolean locked = redisLockUtil.tryLock(LOCK_KEY);
        if (!locked) {
            log.debug("未获取到分布式锁，跳过本次微信订单对账");
            return;
        }
        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusMinutes(GRACE_MINUTES);
            List<Order> orders = orderMapper.selectReconcileShippedOrders(
                    StatusConstants.ORDER_SHIPPED, beforeTime, BATCH_SIZE);
            if (orders.isEmpty()) {
                return;
            }
            log.info("微信订单对账: 发现 {} 条待收敛订单", orders.size());
            int synced = 0;
            int confirmed = 0;
            int fail = 0;
            for (Order order : orders) {
                try {
                    Integer state = wechatOrderShippingService.queryOrderState(order.getId());
                    if (state == null) {
                        continue;
                    }
                    orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                            .eq(Order::getId, order.getId())
                            .set(Order::getWechatOrderState, state)
                            .set(Order::getUpdateTime, LocalDateTime.now()));
                    synced++;
                    // 微信已确认收货/交易完成 → 本地 3→4(CAS 幂等)
                    if (state == 3 || state == 4) {
                        if (orderService.autoConfirmReceive(order.getId())) {
                            confirmed++;
                        }
                    }
                } catch (Exception e) {
                    fail++;
                    log.error("微信订单对账失败, orderId={}", order.getId(), e);
                }
            }
            log.info("微信订单对账完成: 同步状态={}, 自动完成={}, 失败={}", synced, confirmed, fail);
        } finally {
            redisLockUtil.unlock(LOCK_KEY);
        }
    }
}
