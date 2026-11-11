package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.common.constant.PaymentAlertConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.WechatConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.db.User;
import com.example.mystore.event.LatePaymentRefundEvent;
import com.example.mystore.event.StockCacheEvent;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.PayService;
import com.example.mystore.service.RefundResult;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.WechatPayGateway;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private static final int HTTP_STATUS_BAD_REQUEST = 400;
    private static final int HTTP_STATUS_NOT_FOUND = 404;
    private static final int HTTP_STATUS_SERVER_ERROR = 500;
    /** 对账补发退款的展示原因(不参与资金计算) */
    private static final String RECONCILE_REFUND_REASON = "退款对账补发";

    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;
    private final WechatPayGateway wechatPayGateway;
    private final NotificationParser notificationParser;
    private final DingTalkAlertService dingTalkAlertService;
    private final SkuService skuService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlatformTransactionManager transactionManager;

    @Override
    public Map<String, String> prepay(Long userId, Long orderId) {
        // M7: 事务分段 —— 校验+建记录在事务内, 微信 HTTP 在事务外,
        // 避免秒级外呼占死 DB 连接(连接池全站共享)
        PendingPrepay prep = new TransactionTemplate(transactionManager)
                .execute(status -> preparePrepay(userId, orderId));

        try {
            return wechatPayGateway.prepay(prep.outTradeNo(), prep.amount(), prep.openid());
        } catch (Exception e) {
            log.error("微信统一下单失败, orderId={}, outTradeNo={}", orderId, prep.outTradeNo(), e);
            if (prep.created()) {
                // 本次新建的记录标 CLOSED(独立小事务保证落库)
                // 旧实现在 @Transactional 方法内 catch 后标 CLOSED 再 rethrow, 被整体回滚, 是死代码
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        paymentRecordMapper.update(null,
                                new LambdaUpdateWrapper<PaymentRecord>()
                                        .eq(PaymentRecord::getOutTradeNo, prep.outTradeNo())
                                        .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PENDING)
                                        .set(PaymentRecord::getStatus, PaymentRecord.STATUS_CLOSED)
                                        .set(PaymentRecord::getUpdateTime, LocalDateTime.now())));
            }
            throw new BusinessException("创建支付订单失败，请重试");
        }
    }

    /**
     * prepay 段1（事务内）: 校验订单归属/状态 + 查或建 PENDING 支付记录
     */
    private PendingPrepay preparePrepay(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该订单");
        }
        if (order.getStatus() != StatusConstants.ORDER_PENDING_PAYMENT) {
            throw new BusinessException("订单状态不允许支付");
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getOpenid() == null) {
            throw new BusinessException("用户信息异常，请重新登录");
        }

        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentRecord::getOrderId, orderId)
               .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PENDING);
        PaymentRecord existingRecord = paymentRecordMapper.selectOne(wrapper);

        if (existingRecord != null) {
            // 复用既有 PENDING 记录(上次 prepay HTTP 失败可重试), 失败时不标 CLOSED
            return new PendingPrepay(existingRecord.getOutTradeNo(), user.getOpenid(), order.getPayAmount(), false);
        }

        // 商户单号统一为订单号: 微信侧"商户单号" == 我们的订单号, 两端一致
        String outTradeNo = order.getOrderNo();
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(orderId);
        record.setOutTradeNo(outTradeNo);
        record.setAmount(order.getPayAmount());
        record.setStatus(PaymentRecord.STATUS_PENDING);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        paymentRecordMapper.insert(record);
        return new PendingPrepay(outTradeNo, user.getOpenid(), order.getPayAmount(), true);
    }

    private record PendingPrepay(String outTradeNo, String openid, BigDecimal amount, boolean created) {}

    @Override
    public Map<String, String> callback(String body, String signature, String nonce, String timestamp, String serial) {
        // M7: 事务只包 processPaymentSuccess 的 DB 原子单元, catch 移到事务外 ——
        // 否则内层异常给共享事务打 rollback-only 标记后, catch 也拦不住提交时的 UnexpectedRollbackException
        try {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serial)
                    .nonce(nonce)
                    .timestamp(timestamp)
                    .signature(signature)
                    .body(body)
                    .build();

            Transaction transaction = notificationParser.parse(requestParam, Transaction.class);

            String outTradeNo = transaction.getOutTradeNo();
            String transactionId = transaction.getTransactionId();
            Integer totalAmount = transaction.getAmount().getTotal();

            LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PaymentRecord::getOutTradeNo, outTradeNo);
            PaymentRecord record = paymentRecordMapper.selectOne(wrapper);

            if (record == null) {
                log.warn("支付回调未找到支付记录, outTradeNo={}", outTradeNo);
                return Map.of("code", "FAIL", "message", "未找到支付记录");
            }

            if (record.getStatus() == PaymentRecord.STATUS_PAID) {
                log.info("支付回调重复通知, outTradeNo={}", outTradeNo);
                return Map.of("code", "SUCCESS", "message", "成功");
            }

            BigDecimal callbackAmount = new BigDecimal(totalAmount).divide(new BigDecimal("100"));
            if (callbackAmount.compareTo(record.getAmount()) != 0) {
                log.error("支付金额不一致, outTradeNo={}, 期望={}, 实际={}", outTradeNo, record.getAmount(), callbackAmount);
                return Map.of("code", "FAIL", "message", "支付金额不一致");
            }

            Boolean processed = new TransactionTemplate(transactionManager)
                    .execute(status -> processPaymentSuccess(outTradeNo, transactionId));
            if (!Boolean.TRUE.equals(processed)) {
                // processPaymentSuccess 返回 false 涵盖三种情况:
                //   - 无支付记录(理论上前面已拦截, 兜底)
                //   - 已被并发处理(回调重试/lazy sync) → 幂等 Ack 微信
                //   - SQL 条件 update affect=0(竞争失败) → 幂等 Ack 微信
                log.info("支付回调处理完成, processPaymentSuccess=false, outTradeNo={}", outTradeNo);
            }
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception e) {
            log.error("支付回调处理失败", e);
            dingTalkAlertService.alert(PaymentAlertConstants.Alert.PAY_CALLBACK_FAIL, "支付回调异常: " + e.getMessage());
            return Map.of("code", "FAIL", "message", "处理异常");
        }
    }

    @Override
    public Map<String, String> refundCallback(String body, String signature, String nonce, String timestamp, String serial) {
        // M7: REFUNDED 翻转 + 订单 6→7 + 还库存是必须同生共死的原子单元, 包进同一事务;
        // catch 必须在事务外 —— 事务方法内部 catch 会把"半成品"当正常返回提交进库
        try {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serial)
                    .nonce(nonce)
                    .timestamp(timestamp)
                    .signature(signature)
                    .body(body)
                    .build();

            RefundNotification notification = notificationParser.parse(requestParam, RefundNotification.class);

            String outTradeNo = notification.getOutTradeNo();
            String outRefundNo = notification.getOutRefundNo();
            String refundStatus = String.valueOf(notification.getRefundStatus());

            // 按 outTradeNo 找支付记录
            LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PaymentRecord::getOutTradeNo, outTradeNo);
            PaymentRecord record = paymentRecordMapper.selectOne(wrapper);

            if (record == null) {
                log.warn("退款回调未找到支付记录, outTradeNo={}", outTradeNo);
                return Map.of("code", "FAIL", "message", "未找到支付记录");
            }

            // 已退款(3) 直接 Ack 微信(幂等)
            if (record.getStatus() == PaymentRecord.STATUS_REFUNDED) {
                log.info("退款回调重复通知, outRefundNo={}", outRefundNo);
                return Map.of("code", "SUCCESS", "message", "成功");
            }

            // 三态自愈: PAID(响应丢失滞留) 与 REFUNDING(正常受理) 均可继续处理。
            // 旧实现仅认 REFUNDING, 导致"微信已受理但本地停留 PAID"的真实成功被永久拒收。
            if (record.getStatus() != PaymentRecord.STATUS_PAID
                    && record.getStatus() != PaymentRecord.STATUS_REFUNDING) {
                log.warn("退款回调但支付记录状态不支持处理, outTradeNo={}, status={}", outTradeNo, record.getStatus());
                return Map.of("code", "FAIL", "message", "支付记录状态异常");
            }

            if (!WechatConstants.RefundStatus.SUCCESS.equals(refundStatus)) {
                // 微信明确退款失败/异常 → 回退: 订单置 9(退款失败) 待管理员处理, payment_record 回 PAID
                log.error("退款未成功, outTradeNo={}, outRefundNo={}, refundStatus={}",
                        outTradeNo, outRefundNo, refundStatus);
                rollbackRefund(record, "微信退款状态=" + refundStatus);
                dingTalkAlertService.alert(PaymentAlertConstants.Alert.REFUND_DEFINITE_FAILED, record.getOrderId(),
                        "订单=" + record.getOrderId() + " 退款未成功, outRefundNo=" + outRefundNo
                                + ", refundStatus=" + refundStatus + ", 已回退待处理");
                return Map.of("code", "SUCCESS", "message", "成功");
            }

            confirmRefundSuccess(record, outRefundNo);
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception e) {
            log.error("退款回调处理失败", e);
            dingTalkAlertService.alert(PaymentAlertConstants.Alert.REFUND_CALLBACK_FAIL,
                    "退款回调处理异常: " + e.getMessage());
            return Map.of("code", "FAIL", "message", "处理异常");
        }
    }

    @Override
    public PaymentRecord queryByOrderId(Long orderId) {
        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentRecord::getOrderId, orderId)
               .orderByDesc(PaymentRecord::getCreateTime)
               .last("LIMIT 1");
        return paymentRecordMapper.selectOne(wrapper);
    }

    @Override
    public PaymentRecord queryByOrderIdAndUserId(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            return null;
        }
        return queryByOrderId(orderId);
    }

    /**
     * 查询微信支付订单状态(⑥⑦ 共用): 返回 Transaction(含 tradeState / transactionId)
     * 异常直接上抛, 由调用方决定是否跳过/重试
     */
    @Override
    public Transaction queryWechatOrder(String outTradeNo) {
        return wechatPayGateway.queryOrder(outTradeNo);
    }

    /**
     * 支付成功核心处理: payment_record 翻转 PAID + 订单状态 1→2(待发货)
     * 三处复用: 回调 callback / ⑥ OrderCancelStaleJob 微信查单补单 / ⑦ getOrderById lazy sync
     *
     * 事务来源(M7): callback 经 TransactionTemplate 包裹调用(内部 this-call 不走代理, 由模板提供事务);
     * ⑥⑦ 为外部调用, @Transactional 经代理生效
     *
     * 幂等保证:
     *   - 已 PAID 直接返回 false(已被处理过)
     *   - SQL 条件 update WHERE id AND status=0, 并发下仅一个 affect=1, 其余 false
     *
     * 已取消订单收到支付(回调迟到/补单)的 ④ 兜底只发布自动退款事件, 微信外呼在事务提交后异步执行
     *
     * @return true 表示本次成功推进了支付状态(无论是否触发自动退款)
     *         false 表示无需处理(无记录 / 已 PAID / 并发竞争失败) —— 调用方直接 Ack 或跳过
     */
    @Override
    @Transactional
    public boolean processPaymentSuccess(String outTradeNo, String transactionId) {
        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentRecord::getOutTradeNo, outTradeNo);
        PaymentRecord record = paymentRecordMapper.selectOne(wrapper);

        if (record == null) {
            log.warn("processPaymentSuccess 未找到支付记录, outTradeNo={}", outTradeNo);
            return false;
        }

        if (record.getStatus() == PaymentRecord.STATUS_PAID) {
            log.info("processPaymentSuccess 支付记录已 PAID, 跳过, outTradeNo={}", outTradeNo);
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // SQL 条件 update, 利用 MySQL 行锁天然原子: 只有 status=0 时才能改为 PAID
        int rowsAffected = paymentRecordMapper.update(null,
                new LambdaUpdateWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getId, record.getId())
                        .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PENDING)
                        .set(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID)
                        .set(PaymentRecord::getTransactionId, transactionId)
                        .set(PaymentRecord::getPayTime, now)
                        .set(PaymentRecord::getUpdateTime, now));

        if (rowsAffected == 0) {
            log.info("支付记录已被并发处理, 跳过, outTradeNo={}", outTradeNo);
            return false;
        }

        // 订单状态条件更新: 仅 order.status=1(待付款) 时才改成 2(待发货)
        // 若订单已被自动取消(status=5)或其他状态, affect=0, 走下面 ④ 兜底退款
        int orderRows = orderMapper.update(null,
                new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, record.getOrderId())
                        .eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT)
                        .set(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT)
                        .set(Order::getPayTime, now)
                        .set(Order::getUpdateTime, now));

        log.info("支付成功, orderId={}, transactionId={}, 订单推进与否(orderRows={})",
                record.getOrderId(), transactionId, orderRows);

        // ④ 已取消订单(回调迟到/lazy sync 补单) → payment_record 已 PAID 但订单 5(已取消)
        // 事务内只发布事件, 微信退款在提交后由监听器异步发起, 支付回调立即 Ack
        if (orderRows == 0) {
            Order order = orderMapper.selectById(record.getOrderId());
            if (order != null && order.getStatus() == StatusConstants.ORDER_CANCELLED) {
                log.warn("订单已自动取消但收到支付, 提交后自动退款, orderId={}, transactionId={}",
                        record.getOrderId(), transactionId);
                applicationEventPublisher.publishEvent(
                        new LatePaymentRefundEvent(record.getOrderId(), outTradeNo));
            }
        }

        return true;
    }

    /**
     * 退款受理: 读记录 → CAS PAID→REFUNDING → 微信 HTTP, 返回三态结果。
     * 并发安全: outRefundNo 相同微信侧幂等; 双请求下第二个 CAS affect=0, 按结果未知处理。
     *
     * 判定保守: 只有请求未发出(本地校验/签名/配置错误)或微信明确 4xx 业务拒绝才算确定失败并立即回退;
     * 超时/网络/5xx/响应丢失一律按结果未知, 保持退款中交由回调或对账收敛。
     */
    @Override
    public RefundResult refund(Long orderId, String reason) {
        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentRecord::getOrderId, orderId)
               .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID);
        PaymentRecord record = paymentRecordMapper.selectOne(wrapper);

        if (record == null) {
            throw new BusinessException("未找到已支付的支付记录");
        }

        // claim-first: CAS PAID→REFUNDING 提前到外呼之前。
        // 外呼结果未知(超时/响应丢失)时本地已记录"已发起", 由退款回调或对账查询判定最终结果。
        if (!claimPaidRefund(record)) {
            log.info("退款记录已被并发处理, 按结果未知处理, orderId={}", orderId);
            dingTalkAlertService.alert(PaymentAlertConstants.Alert.REFUND_RESULT_UNKNOWN, orderId,
                    "订单=" + orderId + " 退款已被并发受理, 结果未知, 待回调或对账确认");
            return RefundResult.UNKNOWN;
        }

        String outRefundNo = "REFUND_" + record.getOutTradeNo();
        try {
            wechatPayGateway.createRefund(record.getOutTradeNo(), outRefundNo, record.getAmount(), reason);
            log.info("退款受理成功, orderId={}, outRefundNo={}, 待回调确认", orderId, outRefundNo);
            return RefundResult.ACCEPTED;
        } catch (Exception e) {
            if (isDefiniteFailure(e)) {
                log.error("退款确定失败, 回退支付记录, orderId={}, outRefundNo={}",
                        orderId, outRefundNo, e);
                rollbackRefund(record, "退款确定失败: " + e.getMessage());
                dingTalkAlertService.alert(PaymentAlertConstants.Alert.REFUND_DEFINITE_FAILED, orderId,
                        "订单=" + orderId + " 退款确定失败已回退, outRefundNo=" + outRefundNo
                                + ", error=" + e.getMessage());
                return RefundResult.DEFINITE_FAILED;
            }
            // 无法区分"确定失败/响应丢失": 保持 REFUNDING, 交由退款回调或对账查询判定; 不向上抛
            log.error("退款受理结果未知, orderId={}, outRefundNo={}, 保持 REFUNDING 待回调/对账",
                    orderId, outRefundNo, e);
            dingTalkAlertService.alert(PaymentAlertConstants.Alert.REFUND_RESULT_UNKNOWN, orderId,
                    "退款受理结果未知, outRefundNo=" + outRefundNo + ", error=" + e.getMessage());
            return RefundResult.UNKNOWN;
        }
    }

    /** 只有请求未发出或微信明确 4xx 业务错误才算确定失败; 其余(超时/网络/5xx/响应校验失败)一律未知 */
    private static boolean isDefiniteFailure(Exception e) {
        if (e instanceof IllegalArgumentException) {
            // 本地校验/签名/配置错误: 请求尚未发出
            return true;
        }
        if (e instanceof ServiceException serviceException) {
            int statusCode = serviceException.getHttpStatusCode();
            return statusCode >= HTTP_STATUS_BAD_REQUEST && statusCode < HTTP_STATUS_SERVER_ERROR;
        }
        return false;
    }

    /**
     * 退款成功确认(退款回调 / 对账查询共用):
     * payment_record PAID|REFUNDING → REFUNDED, 订单 6→7, 订单命中才恢复库存。
     * 容忍两种来源: 正常回调(REFUNDING) 与 响应丢失滞留(PAID)。
     */
    private void confirmRefundSuccess(PaymentRecord record, String outRefundNo) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            // 退款成功: payment_record 置 REFUNDED, 设 refundTime
            paymentRecordMapper.update(null,
                    new LambdaUpdateWrapper<PaymentRecord>()
                            .eq(PaymentRecord::getId, record.getId())
                            .in(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID, PaymentRecord.STATUS_REFUNDING)
                            .set(PaymentRecord::getStatus, PaymentRecord.STATUS_REFUNDED)
                            .set(PaymentRecord::getRefundAmount, record.getAmount())
                            .set(PaymentRecord::getRefundTime, now)
                            .set(PaymentRecord::getUpdateTime, now));

            // 订单状态推进 6(退款中) -> 7(已退款), WHERE status=6 防并发
            // affected>0 才还库存: 防重复回调双还; 已取消单(5)自动退款回调 6→7 不命中也不误还
            int orderRows = orderMapper.update(null,
                    new LambdaUpdateWrapper<Order>()
                            .eq(Order::getId, record.getOrderId())
                            .eq(Order::getStatus, StatusConstants.ORDER_REFUNDING)
                            .set(Order::getStatus, StatusConstants.ORDER_REFUNDED)
                            .set(Order::getUpdateTime, now));

            if (orderRows > 0) {
                List<OrderItem> items = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, record.getOrderId()));
                List<Long> skuIds = new ArrayList<>();
                for (OrderItem item : items) {
                    skuService.restoreStock(item.getSkuId(), item.getQuantity());
                    skuIds.add(item.getSkuId());
                }
                applicationEventPublisher.publishEvent(new StockCacheEvent(skuIds));
                log.info("退款成功并恢复库存, orderId={}, outRefundNo={}", record.getOrderId(), outRefundNo);
            } else {
                log.info("退款确认 6→7 未命中, 跳过库存恢复, orderId={}, outRefundNo={}", record.getOrderId(), outRefundNo);
            }
        });
    }

    /**
     * 退款失败回退: payment_record REFUNDING→PAID(回到可重试态), 订单 6→9(退款失败)。
     * 库存不回补(退款从未发生); cancel_reason 复用记录失败原因, 管理端可见。
     * 已取消订单(5)的 6→9 条件更新不命中, 订单保持已取消。
     * 告警由调用方按业务类型发出, 便于区分退款受理失败与对账回退。
     */
    private void rollbackRefund(PaymentRecord record, String reason) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            paymentRecordMapper.update(null,
                    new LambdaUpdateWrapper<PaymentRecord>()
                            .eq(PaymentRecord::getId, record.getId())
                            .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_REFUNDING)
                            .set(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID)
                            .set(PaymentRecord::getUpdateTime, now));

            int orderRows = orderMapper.update(null,
                    new LambdaUpdateWrapper<Order>()
                            .eq(Order::getId, record.getOrderId())
                            .eq(Order::getStatus, StatusConstants.ORDER_REFUNDING)
                            .set(Order::getStatus, StatusConstants.ORDER_REFUND_FAILED)
                            .set(Order::getCancelReason, reason)
                            .set(Order::getUpdateTime, now));
            log.warn("退款失败回退, orderId={}, reason={}, orderAffected={}", record.getOrderId(), reason, orderRows);
        });
    }

    /**
     * 退款对账: 按支付记录状态查询微信权威结果并收敛。
     * 退款中: 成功确认 / 处理中保留 / 无退款单重发 / 明确失败回退 / 查询异常保留告警。
     * 已支付且订单已取消: 先查退款单; 无退款单时补发; 查询异常保留。
     * 供 RefundReconcileJob 调用; 单条异常上抛由 Job 隔离。
     */
    @Override
    public void reconcileRefund(PaymentRecord record) {
        if (record == null) {
            return;
        }
        if (record.getStatus() == PaymentRecord.STATUS_PAID) {
            if (!isCancelledOrder(record)) {
                return;
            }
        } else if (record.getStatus() != PaymentRecord.STATUS_REFUNDING) {
            log.info("退款对账: 支付记录状态无需处理, recordId={}, status={}",
                    record.getId(), record.getStatus());
            return;
        }

        String outRefundNo = "REFUND_" + record.getOutTradeNo();
        RefundQuery query = queryRefundForReconcile(record, outRefundNo);
        switch (query.outcome()) {
            case FAILED -> {
                return;
            }
            case MISSING -> resubmitRefund(record, outRefundNo);
            case FOUND -> applyRefundStatus(record, outRefundNo, String.valueOf(query.refund().getStatus()));
        }
    }

    /** 已支付记录只有订单已取消才属于迟到支付退款 */
    private boolean isCancelledOrder(PaymentRecord record) {
        Order order = orderMapper.selectById(record.getOrderId());
        if (order == null || order.getStatus() != StatusConstants.ORDER_CANCELLED) {
            log.info("退款对账: 已支付记录订单未取消, 跳过, recordId={}, orderId={}",
                    record.getId(), record.getOrderId());
            return false;
        }
        return true;
    }

    /**
     * 查询微信退款单。仅 404 + RESOURCE_NOT_EXISTS 表示微信无此退款单, 交由调用方补发;
     * 网络异常/5xx/其他 404/响应异常一律告警并保持现状, 不得误判为无退款单。
     */
    private RefundQuery queryRefundForReconcile(PaymentRecord record, String outRefundNo) {
        try {
            return new RefundQuery(RefundQueryOutcome.FOUND, wechatPayGateway.queryRefund(outRefundNo));
        } catch (ServiceException e) {
            if (e.getHttpStatusCode() == HTTP_STATUS_NOT_FOUND
                    && WechatConstants.Pay.ERROR_RESOURCE_NOT_EXISTS.equals(e.getErrorCode())) {
                log.warn("退款对账: 微信无此退款单, 准备补发, orderId={}, outRefundNo={}, code={}, msg={}",
                        record.getOrderId(), outRefundNo, e.getErrorCode(), e.getErrorMessage());
                return new RefundQuery(RefundQueryOutcome.MISSING, null);
            }
            alertRefundQueryFailed(record, outRefundNo, e.getErrorMessage());
            return new RefundQuery(RefundQueryOutcome.FAILED, null);
        } catch (Exception e) {
            alertRefundQueryFailed(record, outRefundNo, e.getMessage());
            return new RefundQuery(RefundQueryOutcome.FAILED, null);
        }
    }

    private void alertRefundQueryFailed(PaymentRecord record, String outRefundNo, String error) {
        log.error("退款对账: 查询微信退款单失败, 保持现状, orderId={}, outRefundNo={}, error={}",
                record.getOrderId(), outRefundNo, error);
        dingTalkAlertService.alert(PaymentAlertConstants.Alert.REFUND_RESULT_UNKNOWN, record.getOrderId(),
                "退款对账查询微信退款单失败, 保持现状, outRefundNo=" + outRefundNo + ", error=" + error);
    }

    /** 用字符串比较规避 SDK 各版本 Status 类型差异(枚举常量名: SUCCESS/PROCESSING/CLOSED/ABNORMAL) */
    private void applyRefundStatus(PaymentRecord record, String outRefundNo, String refundStatus) {
        if (WechatConstants.RefundStatus.SUCCESS.equals(refundStatus)) {
            confirmRefundSuccess(record, outRefundNo);
            return;
        }
        if (WechatConstants.RefundStatus.PROCESSING.equals(refundStatus)) {
            if (record.getStatus() == PaymentRecord.STATUS_PAID) {
                claimPaidRefund(record);
            }
            log.info("退款对账: 退款处理中, 保留待下轮, orderId={}, outRefundNo={}",
                    record.getOrderId(), outRefundNo);
            return;
        }
        if (record.getStatus() == PaymentRecord.STATUS_PAID) {
            // 已支付+已取消: 本地没有退款中状态可回退, 保持已支付待人工处理
            log.error("退款对账: 微信退款明确失败, 支付记录保持已支付, orderId={}, outRefundNo={}, status={}",
                    record.getOrderId(), outRefundNo, refundStatus);
            dingTalkAlertService.alert(PaymentAlertConstants.Alert.REFUND_DEFINITE_FAILED, record.getOrderId(),
                    "微信退款状态=" + refundStatus + " 明确失败, 已取消订单支付记录保持已支付, 待人工处理, outRefundNo="
                            + outRefundNo);
            return;
        }
        rollbackRefund(record, "对账:微信退款状态=" + refundStatus);
        dingTalkAlertService.alert(PaymentAlertConstants.Alert.REFUND_ROLLBACK, record.getOrderId(),
                "退款对账发现微信退款状态=" + refundStatus + ", 已回退待人工处理, outRefundNo=" + outRefundNo);
    }

    /**
     * 微信无退款单: 用同一退款单号重新发起。已支付记录先 CAS 认领为退款中。
     * 受理成功保持退款中; 确定失败回退; 结果未知保持退款中并告警。
     */
    private void resubmitRefund(PaymentRecord record, String outRefundNo) {
        if (record.getStatus() == PaymentRecord.STATUS_PAID && !claimPaidRefund(record)) {
            log.info("退款对账: 支付记录已被并发处理, 跳过补发, orderId={}", record.getOrderId());
            return;
        }

        try {
            wechatPayGateway.createRefund(record.getOutTradeNo(), outRefundNo,
                    record.getAmount(), RECONCILE_REFUND_REASON);
            log.info("退款对账: 补发退款受理成功, orderId={}, outRefundNo={}",
                    record.getOrderId(), outRefundNo);
        } catch (Exception e) {
            if (isDefiniteFailure(e)) {
                log.error("退款对账: 补发退款确定失败, 回退支付记录, orderId={}, outRefundNo={}",
                        record.getOrderId(), outRefundNo, e);
                rollbackRefund(record, "对账补发退款确定失败: " + e.getMessage());
                dingTalkAlertService.alert(PaymentAlertConstants.Alert.REFUND_DEFINITE_FAILED,
                        record.getOrderId(), "订单=" + record.getOrderId() + " 对账补发退款确定失败已回退, outRefundNo="
                                + outRefundNo + ", error=" + e.getMessage());
                return;
            }
            log.error("退款对账: 补发退款结果未知, 保持退款中待回调/下轮对账, orderId={}, outRefundNo={}",
                    record.getOrderId(), outRefundNo, e);
            dingTalkAlertService.alert(PaymentAlertConstants.Alert.REFUND_RESULT_UNKNOWN,
                    record.getOrderId(), "退款对账补发结果未知, outRefundNo=" + outRefundNo
                            + ", error=" + e.getMessage());
        }
    }

    /** CAS 已支付 → 退款中, 返回是否认领成功 */
    private boolean claimPaidRefund(PaymentRecord record) {
        int claimed = paymentRecordMapper.update(null,
                new LambdaUpdateWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getId, record.getId())
                        .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID)
                        .set(PaymentRecord::getStatus, PaymentRecord.STATUS_REFUNDING)
                        .set(PaymentRecord::getRefundAmount, record.getAmount())
                        .set(PaymentRecord::getUpdateTime, LocalDateTime.now()));
        return claimed > 0;
    }

    private enum RefundQueryOutcome {
        FOUND, MISSING, FAILED
    }

    private record RefundQuery(RefundQueryOutcome outcome, Refund refund) {}
}
