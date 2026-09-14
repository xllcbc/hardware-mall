package com.example.mystore.job.wechat;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.WechatOrderShippingService;
import com.example.mystore.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微信发货信息上报重试任务
 * 扫描已发货(同城/自提)但未成功上报微信的订单, 重试 upload_shipping_info
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WechatShippingRetryJob {

    private final OrderMapper orderMapper;
    private final WechatOrderShippingService wechatOrderShippingService;
    private final RedisLockUtil redisLockUtil;

    /** 宽限期(分钟): 给 shipOrder 内的即时上报留时间 */
    private static final int GRACE_MINUTES = 5;
    private static final int BATCH_SIZE = 100;
    private static final String LOCK_KEY = "job:wechat-shipping-retry";

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void retryUnreportedShipping() {
        boolean locked = redisLockUtil.tryLock(LOCK_KEY);
        if (!locked) {
            log.debug("未获取到分布式锁，跳过本次微信发货上报重试");
            return;
        }
        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusMinutes(GRACE_MINUTES);
            List<Order> orders = orderMapper.selectUnreportedShippedOrders(
                    StatusConstants.ORDER_SHIPPED, beforeTime, BATCH_SIZE);
            if (orders.isEmpty()) {
                return;
            }
            log.info("微信发货上报重试: 发现 {} 条未上报订单", orders.size());
            int success = 0;
            int fail = 0;
            for (Order order : orders) {
                try {
                    wechatOrderShippingService.uploadShippingInfo(order.getId());
                    success++;
                } catch (Exception e) {
                    fail++;
                    log.error("微信发货上报重试失败, orderId={}", order.getId(), e);
                }
            }
            log.info("微信发货上报重试完成: 成功={}, 失败={}", success, fail);
        } finally {
            redisLockUtil.unlock(LOCK_KEY);
        }
    }
}
