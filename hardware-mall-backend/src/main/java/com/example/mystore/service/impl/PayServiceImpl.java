package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.db.User;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.PayService;
import com.wechat.pay.java.core.Config;
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
import com.wechat.pay.java.service.refund.model.RefundNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wechat.pay.mch-id")
public class PayServiceImpl implements PayService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final Config wechatPayConfig;
    private final NotificationParser notificationParser;

    @Value("${wechat.appid}")
    private String appId;

    @Value("${wechat.pay.mch-id}")
    private String mchId;

    @Value("${wechat.pay.notify-url}")
    private String notifyUrl;

    @Override
    @Transactional
    public Map<String, String> prepay(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该订单");
        }
        if (order.getStatus() != StatusConstants.ORDER_PENDING_PAYMENT) {
            throw new RuntimeException("订单状态不允许支付");
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getOpenid() == null) {
            throw new RuntimeException("用户信息异常，请重新登录");
        }

        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentRecord::getOrderId, orderId)
               .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PENDING);
        PaymentRecord existingRecord = paymentRecordMapper.selectOne(wrapper);

        String outTradeNo;
        if (existingRecord != null) {
            outTradeNo = existingRecord.getOutTradeNo();
        } else {
            outTradeNo = generateOutTradeNo(orderId);
            PaymentRecord record = new PaymentRecord();
            record.setOrderId(orderId);
            record.setOutTradeNo(outTradeNo);
            record.setAmount(order.getPayAmount());
            record.setStatus(PaymentRecord.STATUS_PENDING);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            paymentRecordMapper.insert(record);
        }

        try {
            PrepayWithRequestPaymentResponse response = callWechatPrepay(outTradeNo, order.getPayAmount(), user.getOpenid());
            Map<String, String> params = new HashMap<>();
            params.put("appId", response.getAppId());
            params.put("timeStamp", response.getTimeStamp());
            params.put("nonceStr", response.getNonceStr());
            params.put("packageValue", response.getPackageVal());
            params.put("signType", response.getSignType());
            params.put("paySign", response.getPaySign());
            return params;
        } catch (Exception e) {
            log.error("微信统一下单失败, orderId={}, outTradeNo={}", orderId, outTradeNo, e);
            if (existingRecord == null) {
                PaymentRecord record = paymentRecordMapper.selectOne(
                        new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOutTradeNo, outTradeNo));
                if (record != null) {
                    record.setStatus(PaymentRecord.STATUS_CLOSED);
                    record.setUpdateTime(LocalDateTime.now());
                    paymentRecordMapper.updateById(record);
                }
            }
            throw new RuntimeException("创建支付订单失败，请重试");
        }
    }

    @Override
    @Transactional
    public Map<String, String> callback(String body, String signature, String nonce, String timestamp, String serial) {
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

            boolean processed = processPaymentSuccess(outTradeNo, transactionId);
            if (!processed) {
                // processPaymentSuccess 返回 false 涵盖三种情况:
                //   - 无支付记录(理论上前面已拦截, 兜底)
                //   - 已被并发处理(回调重试/lazy sync) → 幂等 Ack 微信
                //   - SQL 条件 update affect=0(竞争失败) → 幂等 Ack 微信
                log.info("支付回调处理完成, processPaymentSuccess=false, outTradeNo={}", outTradeNo);
            }
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception e) {
            log.error("支付回调处理失败", e);
            return Map.of("code", "FAIL", "message", "处理异常");
        }
    }

    @Override
    @Transactional
    public Map<String, String> refundCallback(String body, String signature, String nonce, String timestamp, String serial) {
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

            // 只处理退款中(4)状态, 已退款(3)直接 Ack 微信
            if (record.getStatus() == PaymentRecord.STATUS_REFUNDED) {
                log.info("退款回调重复通知, outRefundNo={}", outRefundNo);
                return Map.of("code", "SUCCESS", "message", "成功");
            }

            if (record.getStatus() != PaymentRecord.STATUS_REFUNDING) {
                log.warn("退款回调但支付记录非退款中状态, outTradeNo={}, status={}", outTradeNo, record.getStatus());
                return Map.of("code", "FAIL", "message", "支付记录状态异常");
            }

            if (!"SUCCESS".equals(refundStatus)) {
                // 退款失败/异常, 状态保持 REFUNDING 留待人工介入(阶段⑧将加钉钉告警)
                log.error("退款失败, outTradeNo={}, outRefundNo={}, refundStatus={}", outTradeNo, outRefundNo, refundStatus);
                return Map.of("code", "SUCCESS", "message", "成功");
            }

            // 退款成功: payment_record 置 REFUNDED, 设 refundTime
            LocalDateTime now = LocalDateTime.now();
            paymentRecordMapper.update(null,
                    new LambdaUpdateWrapper<PaymentRecord>()
                            .eq(PaymentRecord::getId, record.getId())
                            .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_REFUNDING)
                            .set(PaymentRecord::getStatus, PaymentRecord.STATUS_REFUNDED)
                            .set(PaymentRecord::getRefundTime, now)
                            .set(PaymentRecord::getUpdateTime, now));

            // 订单状态推进 6(退款中) -> 7(已退款), WHERE status=6 防并发
            orderMapper.update(null,
                    new LambdaUpdateWrapper<Order>()
                            .eq(Order::getId, record.getOrderId())
                            .eq(Order::getStatus, StatusConstants.ORDER_REFUNDING)
                            .set(Order::getStatus, StatusConstants.ORDER_REFUNDED)
                            .set(Order::getUpdateTime, now));

            log.info("退款成功, orderId={}, outRefundNo={}", record.getOrderId(), outRefundNo);
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception e) {
            log.error("退款回调处理失败", e);
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

    /**
     * 支付成功核心处理: payment_record 翻转 PAID + 订单状态 1→2(待发货)
     * 三处复用: 回调 callback / ⑥ OrderCancelStaleJob 微信查单补单 / ⑦ getOrderById lazy sync
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
                }
            }
        }

        return true;
    }

    @Override
    @Transactional
    public void refund(Long orderId, String reason) {
        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentRecord::getOrderId, orderId)
               .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID);
        PaymentRecord record = paymentRecordMapper.selectOne(wrapper);

        if (record == null) {
            throw new RuntimeException("未找到已支付的支付记录");
        }

        try {
            String outRefundNo = "REFUND_" + record.getOutTradeNo();
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

            // 微信退款是异步: create() 受理成功不代表钱已到用户账上
            // 先置 REFUNDING(4) 等退款回调确认成功后才置 REFUNDED(3), 期间允许退款失败回滚/重试
            paymentRecordMapper.update(null,
                    new LambdaUpdateWrapper<PaymentRecord>()
                            .eq(PaymentRecord::getId, record.getId())
                            .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID)
                            .set(PaymentRecord::getStatus, PaymentRecord.STATUS_REFUNDING)
                            .set(PaymentRecord::getRefundAmount, record.getAmount())
                            .set(PaymentRecord::getUpdateTime, LocalDateTime.now()));

            log.info("退款受理成功, orderId={}, outRefundNo={}, 待回调确认", orderId, outRefundNo);
        } catch (Exception e) {
            log.error("退款失败, orderId={}", orderId, e);
            throw new RuntimeException("退款失败: " + e.getMessage());
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

    private String generateOutTradeNo(Long orderId) {
        return "HM" + System.currentTimeMillis() + orderId;
    }
}