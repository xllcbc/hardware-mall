package com.example.mystore.job.order;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.OrderService;
import com.example.mystore.service.PayService;
import com.example.mystore.util.RedisLockUtil;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消定时任务
 * 扫描超期的待付款订单, 自动取消前先查微信支付状态 ⑥:
 *   若微信侧已付 → 补单不取消, 若未付 → 正常取消, 查询异常 → O4 跳过不取消下轮再查
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelStaleJob {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final RedisLockUtil redisLockUtil;

    @Autowired(required = false)
    private PayService payService;

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
                    // ⑥ 取消前查微信支付状态: 有 PENDING 支付记录 → 先查微信
                    if (payService != null) {
                        PaymentRecord record = payService.queryByOrderId(order.getId());
                        if (record != null && record.getStatus() == PaymentRecord.STATUS_PENDING) {
                            try {
                                Transaction txn = payService.queryWechatOrder(record.getOutTradeNo());
                                if (Transaction.TradeStateEnum.SUCCESS.equals(txn.getTradeState())) {
                                    boolean patched = payService.processPaymentSuccess(
                                            record.getOutTradeNo(), txn.getTransactionId());
                                    skipCount++;
                                    if (patched) {
                                        log.info("⑥ 微信查单补单成功, orderId={}, 跳过取消", order.getId());
                                    } else {
                                        log.info("⑥ 微信查单补单-已被并发处理, orderId={}, 跳过取消", order.getId());
                                    }
                                    continue;
                                }
                                // trade_state 非 SUCCESS → 正常取消(用户真没付)
                            } catch (Exception queryErr) {
                                // O4: 微信查单异常 → 本轮跳过不取消, 下轮10分钟后再查
                                log.warn("⑥ 微信查单异常跳过取消 O4, orderId={}, 下轮再查: {}",
                                        order.getId(), queryErr.getMessage());
                                skipCount++;
                                continue;
                            }
                        }
                    }

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