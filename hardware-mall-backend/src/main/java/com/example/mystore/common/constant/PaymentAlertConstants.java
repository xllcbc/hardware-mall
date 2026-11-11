package com.example.mystore.common.constant;

/**
 * 支付/退款域钉钉告警类型。
 * 与微信协议常量物理隔离: 此处只表达本系统告警语义, 不是微信接口字段值。
 */
public final class PaymentAlertConstants {

    private PaymentAlertConstants() {
    }

    /** 钉钉告警类型 */
    public static final class Alert {

        private Alert() {
        }

        public static final String PAY_CALLBACK_FAIL = "PAY_CALLBACK_FAIL";
        public static final String REFUND_CALLBACK_FAIL = "REFUND_CALLBACK_FAIL";
        public static final String REFUND_RESULT_UNKNOWN = "REFUND_RESULT_UNKNOWN";
        public static final String REFUND_DEFINITE_FAILED = "REFUND_DEFINITE_FAILED";
        public static final String REFUND_ROLLBACK = "REFUND_ROLLBACK";
        public static final String AUTO_REFUND_LISTENER_FAIL = "AUTO_REFUND_LISTENER_FAIL";
    }
}
