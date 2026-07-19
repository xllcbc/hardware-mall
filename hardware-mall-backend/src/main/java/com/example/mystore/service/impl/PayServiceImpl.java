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

            LocalDateTime now = LocalDateTime.now();

            // 用 SQL 条件 update 代替 select-then-update, 利用 MySQL 行锁天然原子:
            // 只有线程在 status=0 时才能改成 PAID, 并发下只有一个 affect=1, 其他 affect=0
            int rowsAffected = paymentRecordMapper.update(null,
                    new LambdaUpdateWrapper<PaymentRecord>()
                            .eq(PaymentRecord::getId, record.getId())
                            .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PENDING)
                            .set(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID)
                            .set(PaymentRecord::getTransactionId, transactionId)
                            .set(PaymentRecord::getPayTime, now)
                            .set(PaymentRecord::getUpdateTime, now));

            if (rowsAffected == 0) {
                // 已被其他线程处理过(回调重试/lazy sync), 直接 Ack 微信不再重复处理
                log.info("支付记录已被并发处理, 跳过, outTradeNo={}", outTradeNo);
                return Map.of("code", "SUCCESS", "message", "成功");
            }

            // 订单状态条件更新: 仅 order.status=1 时才改成 2
            // 若订单已被自动取消(status=5)或其他状态, 此处 affect=0, 不影响订单, 留给 ④ 处理退款
            orderMapper.update(null,
                    new LambdaUpdateWrapper<Order>()
                            .eq(Order::getId, record.getOrderId())
                            .eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT)
                            .set(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT)
                            .set(Order::getPayTime, now)
                            .set(Order::getUpdateTime, now));

            log.info("支付成功, orderId={}, transactionId={}", record.getOrderId(), transactionId);

            // ④ 已取消订单收到支付回调 → 自动退款
            // 场景: natapp 断/服务重启/证书过期等导致回调晚到, 但订单已在 30 分钟时被 OrderCancelStaleJob 取消
            // 此时 payment_record 已被上面置为 PAID, 钱在商户账户但订单显示已取消, 用户没退款
            // 直接调 refund() 而非 orderService.refundOrder() —— 避免重复恢复库存(自动取消时已恢复过)
            Order order = orderMapper.selectById(record.getOrderId());
            if (order != null && order.getStatus() == StatusConstants.ORDER_CANCELLED) {
                log.warn("订单已自动取消但收到支付回调, 自动退款, orderId={}, transactionId={}",
                        record.getOrderId(), transactionId);
                try {
                    refund(record.getOrderId(), "订单已超时取消,支付回调迟到,自动退款");
                } catch (Exception refundErr) {
                    // 退款发起失败保持 payment_record=PAID 状态, 留待阶段 ⑦ lazy sync 兜底 + ⑧ 钉钉告警人工介入
                    log.error("自动退款失败, orderId={}, payment_record=PAID 待人工或 lazy sync 补单",
                            record.getOrderId(), refundErr);
                }
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