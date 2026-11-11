package com.example.mystore.event;

import com.example.mystore.common.constant.PaymentAlertConstants;
import com.example.mystore.service.PayService;
import com.example.mystore.service.RefundResult;
import com.example.mystore.service.impl.DingTalkAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 迟到支付自动退款监听器：支付事务提交后异步发起退款，支付回调立即 Ack。
 * 执行失败时支付记录仍是已支付，可由退款对账的“已支付 + 已取消”分支补发。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LatePaymentRefundListener {

    static final String AUTO_REFUND_REASON = "订单已超时取消,支付迟到,自动退款";

    private final PayService payService;
    private final DingTalkAlertService dingTalkAlertService;

    @Async("refundExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLatePaymentRefund(LatePaymentRefundEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        try {
            RefundResult result = payService.refund(event.orderId(), AUTO_REFUND_REASON);
            log.info("迟到支付自动退款完成, orderId={}, outTradeNo={}, result={}",
                    event.orderId(), event.outTradeNo(), result);
        } catch (Exception e) {
            log.error("迟到支付自动退款监听器异常, orderId={}, outTradeNo={}, 退款未受理时保持已支付待对账补发",
                    event.orderId(), event.outTradeNo(), e);
            dingTalkAlertService.alert(PaymentAlertConstants.Alert.AUTO_REFUND_LISTENER_FAIL, event.orderId(),
                    "订单=" + event.orderId() + " 迟到支付自动退款监听器异常: " + e.getMessage()
                            + "; 退款未受理时支付记录保持已支付, 待退款对账补发");
        }
    }
}
