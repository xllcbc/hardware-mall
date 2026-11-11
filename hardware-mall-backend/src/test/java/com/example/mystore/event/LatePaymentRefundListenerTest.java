package com.example.mystore.event;

import com.example.mystore.service.PayService;
import com.example.mystore.service.RefundResult;
import com.example.mystore.service.impl.DingTalkAlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LatePaymentRefundListenerTest {

    @Mock
    private PayService payService;
    @Mock
    private DingTalkAlertService dingTalkAlertService;

    @InjectMocks
    private LatePaymentRefundListener listener;

    @Test
    void shouldCallRefundWithAutoReason() {
        when(payService.refund(1L, LatePaymentRefundListener.AUTO_REFUND_REASON))
                .thenReturn(RefundResult.ACCEPTED);

        listener.onLatePaymentRefund(new LatePaymentRefundEvent(1L, "OUT_001"));

        verify(payService).refund(1L, LatePaymentRefundListener.AUTO_REFUND_REASON);
        verify(dingTalkAlertService, never()).alert(anyString(), eq(1L), anyString());
    }

    @Test
    void shouldAlertByOrder_whenListenerThrows() {
        when(payService.refund(1L, LatePaymentRefundListener.AUTO_REFUND_REASON))
                .thenThrow(new RuntimeException("db down"));

        listener.onLatePaymentRefund(new LatePaymentRefundEvent(1L, "OUT_001"));

        verify(dingTalkAlertService).alert(eq("AUTO_REFUND_LISTENER_FAIL"), eq(1L),
                org.mockito.ArgumentMatchers.contains("db down"));
    }

    @Test
    void shouldNotAlertAgain_whenRefundReturnsDefiniteFailure() {
        // 确定失败已由 PayService 按订单告警并回退, 监听器不重复告警
        when(payService.refund(2L, LatePaymentRefundListener.AUTO_REFUND_REASON))
                .thenReturn(RefundResult.DEFINITE_FAILED);

        listener.onLatePaymentRefund(new LatePaymentRefundEvent(2L, "OUT_002"));

        verify(dingTalkAlertService, never()).alert(anyString(), eq(2L), anyString());
    }
}
