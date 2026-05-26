package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.service.PayService;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.cipher.Signer;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.AmountReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderMapper orderMapper;
    private final RSAAutoCertificateConfig rsaAutoCertificateConfig;
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

        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentRecord::getOrderId, orderId)
               .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PENDING);
        PaymentRecord existingRecord = paymentRecordMapper.selectOne(wrapper);
        if (existingRecord != null) {
            return buildJsapiPayParams(existingRecord.getOutTradeNo());
        }

        String outTradeNo = generateOutTradeNo(orderId);
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(orderId);
        record.setOutTradeNo(outTradeNo);
        record.setAmount(order.getPayAmount());
        record.setStatus(PaymentRecord.STATUS_PENDING);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        paymentRecordMapper.insert(record);

        try {
            PrepayResponse prepayResponse = callWechatPrepay(outTradeNo, order.getPayAmount(), order.getUserId().toString());
            return buildJsapiPayParams(prepayResponse.getPrepayId());
        } catch (Exception e) {
            log.error("微信统一下单失败, orderId={}, outTradeNo={}", orderId, outTradeNo, e);
            record.setStatus(PaymentRecord.STATUS_CLOSED);
            record.setUpdateTime(LocalDateTime.now());
            paymentRecordMapper.updateById(record);
            throw new RuntimeException("创建支付订单失败，请重试");
        }
    }

    @Override
    @Transactional
    public String callback(String body, String signature, String nonce, String timestamp, String serial) {
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
                return "FAIL";
            }

            if (record.getStatus() == PaymentRecord.STATUS_PAID) {
                log.info("支付回调重复通知, outTradeNo={}", outTradeNo);
                return "SUCCESS";
            }

            BigDecimal callbackAmount = new BigDecimal(totalAmount).divide(new BigDecimal("100"));
            if (callbackAmount.compareTo(record.getAmount()) != 0) {
                log.error("支付金额不一致, outTradeNo={}, 期望={}, 实际={}", outTradeNo, record.getAmount(), callbackAmount);
                return "FAIL";
            }

            record.setStatus(PaymentRecord.STATUS_PAID);
            record.setTransactionId(transactionId);
            record.setPayTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            paymentRecordMapper.updateById(record);

            Order order = orderMapper.selectById(record.getOrderId());
            if (order != null && order.getStatus() == StatusConstants.ORDER_PENDING_PAYMENT) {
                order.setStatus(StatusConstants.ORDER_PENDING_SHIPMENT);
                order.setPayTime(LocalDateTime.now());
                order.setUpdateTime(LocalDateTime.now());
                orderMapper.updateById(order);
            }

            log.info("支付成功, orderId={}, transactionId={}", record.getOrderId(), transactionId);
            return "SUCCESS";
        } catch (Exception e) {
            log.error("支付回调处理失败", e);
            return "FAIL";
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
            AmountReq amountReq = new AmountReq();
            amountReq.setRefund(record.getAmount().multiply(new BigDecimal("100")).longValue());
            amountReq.setTotal(record.getAmount().multiply(new BigDecimal("100")).longValue());
            amountReq.setCurrency("CNY");
            refundRequest.setAmount(amountReq);

            RefundService refundService = new RefundService.Builder()
                    .config(rsaAutoCertificateConfig)
                    .build();
            refundService.create(refundRequest);

            record.setStatus(PaymentRecord.STATUS_REFUNDED);
            record.setRefundAmount(record.getAmount());
            record.setRefundTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            paymentRecordMapper.updateById(record);

            log.info("退款成功, orderId={}, refundAmount={}", orderId, record.getAmount());
        } catch (Exception e) {
            log.error("退款失败, orderId={}", orderId, e);
            throw new RuntimeException("退款失败: " + e.getMessage());
        }
    }

    private PrepayResponse callWechatPrepay(String outTradeNo, BigDecimal amount, String openid) {
        JsapiService jsapiService = new JsapiService.Builder()
                .config(rsaAutoCertificateConfig)
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

        return jsapiService.prepay(prepayRequest);
    }

    private Map<String, String> buildJsapiPayParams(String prepayId) {
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);

        Map<String, String> params = new HashMap<>();
        params.put("appId", appId);
        params.put("timeStamp", timeStamp);
        params.put("nonceStr", nonceStr);
        params.put("packageValue", "prepay_id=" + prepayId);
        params.put("signType", "RSA");

        try {
            String message = appId + "\n" + timeStamp + "\n" + nonceStr + "\n" + "prepay_id=" + prepayId + "\n";
            Signer signer = rsaAutoCertificateConfig.createSigner();
            String paySign = signer.sign(message).getSign();
            params.put("paySign", paySign);
        } catch (Exception e) {
            log.error("签名失败", e);
            throw new RuntimeException("支付签名失败");
        }

        return params;
    }

    private String generateOutTradeNo(Long orderId) {
        return "HM" + System.currentTimeMillis() + orderId;
    }
}