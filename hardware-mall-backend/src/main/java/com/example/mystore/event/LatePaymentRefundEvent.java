package com.example.mystore.event;

/**
 * 已取消订单收到迟到支付后，需要在支付事务提交后异步发起的自动退款。
 */
public record LatePaymentRefundEvent(Long orderId, String outTradeNo) {
}
