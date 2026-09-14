package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.db.User;
import com.example.mystore.event.StockSyncEvent;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.PayService;
import com.example.mystore.service.SkuService;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;
    private final Config wechatPayConfig;
    private final NotificationParser notificationParser;
    private final DingTalkAlertService dingTalkAlertService;
    private final SkuService skuService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlatformTransactionManager transactionManager;

    @Value("${wechat.appid}")
    private String appId;

    @Value("${wechat.pay.mch-id}")
    private String mchId;

    @Value("${wechat.pay.notify-url}")
    private String notifyUrl;

    @Override
    public Map<String, String> prepay(Long userId, Long orderId) {
        // M7: 事务分段 —— 校验+建记录在事务内, 微信 HTTP 在事务外,
        // 避免秒级外呼占死 DB 连接(连接池全站共享)
        PendingPrepay prep = new TransactionTemplate(transactionManager)
                .execute(status -> preparePrepay(userId, orderId));

        try {
            PrepayWithRequestPaymentResponse response =
                    callWechatPrepay(prep.outTradeNo(), prep.amount(), prep.openid());
            Map<String, String> params = new HashMap<>();
            params.put("appId", response.getAppId());
            params.put("timeStamp", response.getTimeStamp());
            params.put("nonceStr", response.getNonceStr());
            params.put("packageValue", response.getPackageVal());
            params.put("signType", response.getSignType());
            params.put("paySign", response.getPaySign());
            return params;
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
            dingTalkAlertService.alert("PAY_CALLBACK_FAIL", "支付回调异常: " + e.getMessage());
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

            if (!"SUCCESS".equals(refundStatus)) {
                // 微信明确退款失败/异常 → 回退: 订单置 9(退款失败) 待管理员处理, payment_record 回 PAID
                log.error("退款未成功, outTradeNo={}, outRefundNo={}, refundStatus={}",
                        outTradeNo, outRefundNo, refundStatus);
                rollbackRefund(record, "微信退款状态=" + refundStatus);
                return Map.of("code", "SUCCESS", "message", "成功");
            }

            confirmRefundSuccess(record, outRefundNo);
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception e) {
            log.error("退款回调处理失败", e);
            dingTalkAlertService.alert("REFUND_CALLBACK_FAIL",
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
     * 查询微信支付订单状态(⑥⑦ 共用)
     * 调用 JsapiServiceExtension.queryOrderByOutTradeNo, 返回 Transaction(含 tradeState / transactionId)
     * 异常直接上抛, 由调用方决定是否跳过/重试
     */
    @Override
    public Transaction queryWechatOrder(String outTradeNo) {
        JsapiServiceExtension jsapiService = new JsapiServiceExtension.Builder()
                .config(wechatPayConfig)
                .build();
        com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest request =
                new com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest();
        request.setOutTradeNo(outTradeNo);
        request.setMchid(mchId);
        return jsapiService.queryOrderByOutTradeNo(request);
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
     * 已取消订单收到支付(回调迟到/补单)的 ④ 兜底自动退款也在此处理, 退款失败只 log
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
        // 直接 refund(), 不调 orderService.refundOrder() 避免重复恢复库存(自动取消时已恢复)
        if (orderRows == 0) {
            Order order = orderMapper.selectById(record.getOrderId());
            if (order != null && order.getStatus() == StatusConstants.ORDER_CANCELLED) {
                log.warn("订单已自动取消但收到支付, 自动退款, orderId={}, transactionId={}",
                        record.getOrderId(), transactionId);
                try {
                    refund(record.getOrderId(), "订单已超时取消,支付迟到,自动退款");
                } catch (Exception refundErr) {
                    log.error("自动退款失败, orderId={}, payment_record=PAID 待人工介入",
                            record.getOrderId(), refundErr);
                    dingTalkAlertService.alert("AUTO_REFUND_FAIL",
                            "订单=" + record.getOrderId() + " 已取消但支付回调迟到, 自动退款发起失败: " + refundErr.getMessage());
                }
            }
        }

        return true;
    }

    /**
     * 退款受理: 读记录 → 微信 HTTP → CAS PAID→REFUNDING, 三步各自自动提交（M7 去 @Transactional）
     * 并发安全: outRefundNo 相同微信侧幂等; 双请求下第二个 CAS affect=0 无害
     *
     * 注意: processPaymentSuccess 的 ④ 自动退款路径在事务内 this-调用本方法, HTTP 仍短暂占用外层事务连接 ——
     * 不套 REQUIRES_NEW 是有意的: 挂起外层事务后新连接读不到未提交的 PAID, 会直接抛"未找到已支付的支付记录";
     * 该路径罕见(回调迟到兜底)且有钉钉告警兜底, 保持现状
     */
    @Override
    public void refund(Long orderId, String reason) {
        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentRecord::getOrderId, orderId)
               .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID);
        PaymentRecord record = paymentRecordMapper.selectOne(wrapper);

        if (record == null) {
            throw new BusinessException("未找到已支付的支付记录");
        }

        // claim-first: CAS PAID→REFUNDING 提前到外呼之前。
        // 外呼结果未知(超时/响应丢失)时本地已记录"已发起", 由退款回调或对账查询判定最终结果,
        // 不再停留在 PAID 导致"确定失败"与"响应丢失"无法区分。
        int claimed = paymentRecordMapper.update(null,
                new LambdaUpdateWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getId, record.getId())
                        .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID)
                        .set(PaymentRecord::getStatus, PaymentRecord.STATUS_REFUNDING)
                        .set(PaymentRecord::getRefundAmount, record.getAmount())
                        .set(PaymentRecord::getUpdateTime, LocalDateTime.now()));
        if (claimed == 0) {
            log.info("退款记录已被并发处理, 跳过本次受理, orderId={}", orderId);
            return;
        }

        String outRefundNo = "REFUND_" + record.getOutTradeNo();
        try {
            CreateRequest refundRequest = new CreateRequest();
            refundRequest.setOutTradeNo(record.getOutTradeNo());
            refundRequest.setOutRefundNo(outRefundNo);
            refundRequest.setReason(reason);
            refundRequest.setNotifyUrl(notifyUrl + "/refund");
            AmountReq amountReq = new AmountReq();
            amountReq.setRefund(record.getAmount().multiply(new BigDecimal("100")).longValue());
            amountReq.setTotal(record.getAmount().multiply(new BigDecimal("100")).longValue());
            amountReq.setCurrency("CNY");
            refundRequest.setAmount(amountReq);

            RefundService refundService = new RefundService.Builder()
                    .config(wechatPayConfig)
                    .build();
            refundService.create(refundRequest);

            log.info("退款受理成功, orderId={}, outRefundNo={}, 待回调确认", orderId, outRefundNo);
        } catch (Exception e) {
            // 无法区分"确定失败/响应丢失": 保持 REFUNDING, 交由退款回调或对账查询判定; 不向上抛
            log.error("退款受理结果未知, orderId={}, outRefundNo={}, 保持 REFUNDING 待回调/对账",
                    orderId, outRefundNo, e);
            dingTalkAlertService.alert("REFUND_FAIL",
                    "退款受理结果未知, orderId=" + orderId + ", outRefundNo=" + outRefundNo
                            + ", error=" + e.getMessage());
        }
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
                applicationEventPublisher.publishEvent(new StockSyncEvent(skuIds));
                log.info("退款成功并恢复库存, orderId={}, outRefundNo={}", record.getOrderId(), outRefundNo);
            } else {
                log.info("退款确认 6→7 未命中, 跳过库存恢复, orderId={}, outRefundNo={}", record.getOrderId(), outRefundNo);
            }
        });
    }

    /**
     * 退款失败回退: payment_record REFUNDING→PAID(回到可重试态), 订单 6→9(退款失败)。
     * 库存不回补(退款从未发生); cancel_reason 复用记录失败原因, 管理端可见。
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
        dingTalkAlertService.alert("REFUND_ROLLBACK",
                "退款确认失败, 订单已置退款失败(9)待人工处理, orderId=" + record.getOrderId() + ", reason=" + reason);
    }

    /**
     * 查询微信退款单真实状态(由商户退款单号)
     */
    private Refund queryRefund(String outRefundNo) {
        RefundService refundService = new RefundService.Builder()
                .config(wechatPayConfig)
                .build();
        QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
        request.setOutRefundNo(outRefundNo);
        return refundService.queryByOutRefundNo(request);
    }

    /**
     * 退款对账: 对滞留"退款中"订单查询微信退款权威状态并收敛。
     * 供 RefundReconcileJob 调用; 单条异常上抛由 Job 隔离。
     */
    @Override
    public void reconcileRefund(Long orderId) {
        PaymentRecord record = queryByOrderId(orderId);
        if (record == null) {
            log.warn("退款对账: 未找到支付记录, orderId={}", orderId);
            return;
        }
        String outRefundNo = "REFUND_" + record.getOutTradeNo();
        Refund refund;
        try {
            refund = queryRefund(outRefundNo);
        } catch (ServiceException e) {
            // 微信无此退款单(404 RESOURCE_NOT_EXISTS 等) → 从未受理 → 回退
            log.warn("退款对账: 微信无此退款单, orderId={}, outRefundNo={}, code={}, msg={}",
                    orderId, outRefundNo, e.getErrorCode(), e.getErrorMessage());
            rollbackRefund(record, "对账:微信无此退款单");
            return;
        }
        // 用字符串比较规避 SDK 各版本 Status 类型差异(枚举常量名: SUCCESS/PROCESSING/CLOSED/ABNORMAL)
        String refundStatus = String.valueOf(refund.getStatus());
        if ("SUCCESS".equals(refundStatus)) {
            confirmRefundSuccess(record, outRefundNo);
        } else if ("PROCESSING".equals(refundStatus)) {
            log.info("退款对账: 退款处理中, 保留待下轮, orderId={}, outRefundNo={}", orderId, outRefundNo);
        } else {
            rollbackRefund(record, "对账:微信退款状态=" + refundStatus);
        }
    }

    private PrepayWithRequestPaymentResponse callWechatPrepay(String outTradeNo, BigDecimal amount, String openid) {
        JsapiServiceExtension jsapiService = new JsapiServiceExtension.Builder()
                .config(wechatPayConfig)
                .build();

        PrepayRequest prepayRequest = new PrepayRequest();
        prepayRequest.setAppid(appId);
        prepayRequest.setMchid(mchId);
        prepayRequest.setDescription("五金商城-订单支付");
        prepayRequest.setOutTradeNo(outTradeNo);
        prepayRequest.setNotifyUrl(notifyUrl);

        Amount reqAmount = new Amount();
        reqAmount.setTotal(amount.multiply(new BigDecimal("100")).intValueExact());
        reqAmount.setCurrency("CNY");
        prepayRequest.setAmount(reqAmount);

        Payer payer = new Payer();
        payer.setOpenid(openid);
        prepayRequest.setPayer(payer);

        return jsapiService.prepayWithRequestPayment(prepayRequest);
    }
}