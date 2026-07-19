package com.example.mystore.service;

import com.example.mystore.entity.db.PaymentRecord;
import java.util.Map;

public interface PayService {
    Map<String, String> prepay(Long userId, Long orderId);
    Map<String, String> callback(String body, String signature, String nonce, String timestamp, String serial);
    Map<String, String> refundCallback(String body, String signature, String nonce, String timestamp, String serial);
    PaymentRecord queryByOrderId(Long orderId);
    void refund(Long orderId, String reason);
}