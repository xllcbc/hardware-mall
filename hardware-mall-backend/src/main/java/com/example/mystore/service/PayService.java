package com.example.mystore.service;

import com.example.mystore.entity.db.PaymentRecord;
import com.wechat.pay.java.service.payments.model.Transaction;
import java.util.Map;

public interface PayService {
    Map<String, String> prepay(Long userId, Long orderId);
    Map<String, String> callback(String body, String signature, String nonce, String timestamp, String serial);
    Map<String, String> refundCallback(String body, String signature, String nonce, String timestamp, String serial);
    PaymentRecord queryByOrderId(Long orderId);
    PaymentRecord queryByOrderIdAndUserId(Long orderId, Long userId);
    RefundResult refund(Long orderId, String reason);
    boolean processPaymentSuccess(String outTradeNo, String transactionId);
    Transaction queryWechatOrder(String outTradeNo);
    void reconcileRefund(PaymentRecord record);

    /**
     * 订单详情的支付状态同步入口（同一订单 30 秒内最多触发一次）：
     * 支付记录已支付但订单仍待付款时修复为待发货；支付记录待支付时查询微信，成功则走
     * {@link #processPaymentSuccess} 的既有支付成功处理。
     *
     * <p>异常向上抛出，由详情读路径吞掉，不影响详情返回。
     */
    void syncPaymentStatus(Long orderId);
}