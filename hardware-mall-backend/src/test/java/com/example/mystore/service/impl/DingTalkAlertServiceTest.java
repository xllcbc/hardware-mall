package com.example.mystore.service.impl;

import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DingTalkAlertServiceTest {

    @Mock
    private RedisUtil redisUtil;

    private DingTalkAlertService service;

    @BeforeEach
    void setUp() {
        service = new DingTalkAlertService(redisUtil);
        ReflectionTestUtils.setField(service, "webhook", "http://127.0.0.1:1/dingtalk");
        ReflectionTestUtils.setField(service, "secret", "test-secret");
        when(redisUtil.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    }

    @Test
    void orderScopedAlerts_shouldUseDistinctDebounceKeys() {
        service.alert("REFUND_RESULT_UNKNOWN", 101L, "订单 101 结果未知");
        service.alert("REFUND_RESULT_UNKNOWN", 102L, "订单 102 结果未知");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(redisUtil, times(2)).setIfAbsent(keys.capture(), any(), eq(5L), eq(TimeUnit.MINUTES));
        assertThat(keys.getAllValues()).containsExactly(
                "alert:dingtalk:REFUND_RESULT_UNKNOWN:101",
                "alert:dingtalk:REFUND_RESULT_UNKNOWN:102");
    }

    @Test
    void aggregateAlert_shouldKeepTypeScopedKey() {
        service.alert("PAY_CALLBACK_FAIL", "支付回调异常");

        verify(redisUtil).setIfAbsent(eq("alert:dingtalk:PAY_CALLBACK_FAIL"), any(), eq(5L), eq(TimeUnit.MINUTES));
    }
}
