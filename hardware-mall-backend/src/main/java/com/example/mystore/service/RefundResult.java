package com.example.mystore.service;

/**
 * 退款受理结果三态。
 *
 * <p>{@link #ACCEPTED} 微信已受理，最终结果待退款回调或退款对账确认；
 * {@link #UNKNOWN} 超时、网络异常、5xx、响应缺失等无法判定，支付记录保持退款中；
 * {@link #DEFINITE_FAILED} 微信明确 4xx 业务拒绝（或请求未发出），支付记录回到已支付。
 */
public enum RefundResult {
    ACCEPTED,
    UNKNOWN,
    DEFINITE_FAILED
}
