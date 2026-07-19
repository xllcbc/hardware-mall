package com.example.mystore.service;

import com.example.mystore.entity.db.PaymentRecord;
import com.wechat.pay.java.service.payments.model.Transaction;
import java.util.Map;

public interface PayService {
    Map<String, String> prepay(Long userId, Long orderId);
    Map<String, String> callback(String body, String signature, String nonce, String timestamp, String serial);
    Map<String, String> refundCallback(String body, String signature, String nonce, String timestamp, String serial);
    PaymentRecord queryByOrderId(Long orderId);
    void refund(Long orderId, String reason);
    boolean processPaymentSuccess(String outTradeNo, String transactionId);
    Transaction queryWechatOrder(String outTradeNo);
}