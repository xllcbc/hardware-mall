package com.example.mystore.service;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.service.impl.PayServiceImpl;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PayServiceImplTest {

    @MockBean Config wechatPayConfig;
    @MockBean NotificationParser notificationParser;
    @MockBean DingTalkAlertService dingTalkAlertService;

    @Autowired PaymentRecordMapper paymentRecordMapper;
    @Autowired OrderMapper orderMapper;
    @Autowired SkuMapper skuMapper;
    @Autowired OrderItemMapper orderItemMapper;

    @SpyBean
    PayServiceImpl payService;

    private Long setupOrder(int orderStatus) {
        Order order = new Order();
        order.setOrderNo("TEST_" + System.nanoTime());
        order.setUserId(1L);
        order.setAddressId(1L);
        order.setLogisticsId(1L);
        order.setStatus(orderStatus);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(new BigDecimal("100.00"));
        orderMapper.insert(order);
        return order.getId();
    }

    private PaymentRecord setupPaymentRecord(Long orderId, int status, String outTradeNo) {
        PaymentRecord pr = new PaymentRecord();
        pr.setOrderId(orderId);
        pr.setOutTradeNo(outTradeNo);
        pr.setAmount(new BigDecimal("100.00"));
        pr.setStatus(status);
        pr.setCreateTime(LocalDateTime.now());
        pr.setUpdateTime(LocalDateTime.now());
        paymentRecordMapper.insert(pr);
        return pr;
    }

    // ==================== processPaymentSuccess ====================

    @Test
    void shouldReturnFalse_whenRecordNotFound() {
        boolean result = payService.processPaymentSuccess("NOT_EXIST", "TXN_ID");
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalse_whenAlreadyPaid() {
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_SHIPMENT);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, "OUT_001");

        boolean result = payService.processPaymentSuccess("OUT_001", "TXN_ID");

        assertThat(result).isFalse();
    }

    @Test
    void shouldAdvanceOrder_whenNormalPaymentSuccess() {
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_PAYMENT);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PENDING, "OUT_002");

        boolean result = payService.processPaymentSuccess("OUT_002", "TXN_ID");

        assertThat(result).isTrue();

        PaymentRecord pr = paymentRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getOutTradeNo, "OUT_002"));
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_PAID);

        Order order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(StatusConstants.ORDER_PENDING_SHIPMENT);
    }

    @Test
    void shouldTriggerAutoRefund_whenOrderCancelled() {
        doNothing().when(payService).refund(anyLong(), anyString());

        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PENDING, "OUT_003");

        boolean result = payService.processPaymentSuccess("OUT_003", "TXN_ID");

        assertThat(result).isTrue();
        verify(payService).refund(eq(orderId), anyString());
    }

    @Test
    void shouldAlert_whenAutoRefundFails() {
        doThrow(new RuntimeException("退款失败")).when(payService).refund(anyLong(), anyString());

        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PENDING, "OUT_004");

        boolean result = payService.processPaymentSuccess("OUT_004", "TXN_ID");

        assertThat(result).isTrue();
        verify(dingTalkAlertService).alert(eq("AUTO_REFUND_FAIL"), anyString());
    }

    // ==================== refundCallback ====================

    @Test
    void refundCallback_shouldReturnFail_whenRecordNotFound() {
        String outTradeNo = uniqueOutTradeNo();
        mockNotificationParser(outTradeNo, "SUCCESS");

        Map<String, String> result = payService.refundCallback("body", "sig", "nonce", "ts", "serial");

        assertThat(result).containsEntry("code", "FAIL");
    }

    @Test
    void refundCallback_shouldReturnSuccess_whenAlreadyRefunded() {
        String outTradeNo = uniqueOutTradeNo();
        mockNotificationParser(outTradeNo, "SUCCESS");
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDED);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDED, outTradeNo);

        Map<String, String> result = payService.refundCallback("body", "sig", "nonce", "ts", "serial");

        assertThat(result).containsEntry("code", "SUCCESS");
    }

    @Test
    void refundCallback_shouldReturnFail_whenStatusNotRefunding() {
        String outTradeNo = uniqueOutTradeNo();
        mockNotificationParser(outTradeNo, "SUCCESS");
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_PAYMENT);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_CLOSED, outTradeNo);

        Map<String, String> result = payService.refundCallback("body", "sig", "nonce", "ts", "serial");

        assertThat(result).containsEntry("code", "FAIL");
    }

    @Test
    void refundCallback_shouldAdvanceToRefunded_whenRefundSuccess() {
        String outTradeNo = uniqueOutTradeNo();
        mockNotificationParser(outTradeNo, "SUCCESS");
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, outTradeNo);

        Map<String, String> result = payService.refundCallback("body", "sig", "nonce", "ts", "serial");

        assertThat(result).containsEntry("code", "SUCCESS");

        PaymentRecord pr = paymentRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getOutTradeNo, outTradeNo));
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_REFUNDED);

        Order order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(StatusConstants.ORDER_REFUNDED);
    }

    @Test
    void refundCallback_shouldAlert_whenRefundFailed() {
        String outTradeNo = uniqueOutTradeNo();
        mockNotificationParser(outTradeNo, "FAIL");
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, outTradeNo);

        Map<String, String> result = payService.refundCallback("body", "sig", "nonce", "ts", "serial");

        assertThat(result).containsEntry("code", "SUCCESS");
        verify(dingTalkAlertService).alert(eq("REFUND_CONFIRM_FAIL"), anyString());

        PaymentRecord pr = paymentRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getOutTradeNo, outTradeNo));
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_REFUNDING);
    }

    @Test
    void refundCallback_shouldRestoreStock_whenRefundSuccessWins() {
        String outTradeNo = uniqueOutTradeNo();
        mockNotificationParser(outTradeNo, "SUCCESS");
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, outTradeNo);
        Long skuId = setupSku(10);
        setupOrderItem(orderId, skuId, 3);

        Map<String, String> result = payService.refundCallback("body", "sig", "nonce", "ts", "serial");

        assertThat(result).containsEntry("code", "SUCCESS");
        // 6→7 赢得跳变 → 还库存: 10 + 3 = 13
        assertThat(skuMapper.selectById(skuId).getStock()).isEqualTo(13);
    }

    @Test
    void refundCallback_shouldNotRestoreStock_whenOrderNotInRefunding() {
        String outTradeNo = uniqueOutTradeNo();
        mockNotificationParser(outTradeNo, "SUCCESS");
        // 已取消单(5)自动退款场景: 订单非 6, 6→7 CAS 0 行 → 不还库存(取消时已还)
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, outTradeNo);
        Long skuId = setupSku(10);
        setupOrderItem(orderId, skuId, 3);

        Map<String, String> result = payService.refundCallback("body", "sig", "nonce", "ts", "serial");

        assertThat(result).containsEntry("code", "SUCCESS");
        assertThat(skuMapper.selectById(skuId).getStock()).isEqualTo(10);
    }

    // ==================== helper ====================

    private Long setupSku(int stock) {
        Sku sku = new Sku();
        sku.setSpuId(1L);
        sku.setSpecs(new java.util.ArrayList<>());
        sku.setSpecHash("HASH_" + System.nanoTime());
        sku.setPrice(new BigDecimal("100.00"));
        sku.setStock(stock);
        sku.setStatus(1);
        skuMapper.insert(sku);
        return sku.getId();
    }

    private void setupOrderItem(Long orderId, Long skuId, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setSkuId(skuId);
        item.setSpuId(1L);
        item.setProductName("测试商品");
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal("100.00"));
        item.setSubtotal(new BigDecimal("300.00"));
        orderItemMapper.insert(item);
    }

    private static String uniqueOutTradeNo() {
        return "OUT_UNIQUE_" + System.nanoTime();
    }

    @SuppressWarnings("unchecked")
    private void mockNotificationParser(String outTradeNo, String refundStatus) {
        RefundNotification notification = org.mockito.Mockito.mock(RefundNotification.class);
        when(notification.getOutTradeNo()).thenReturn(outTradeNo);
        when(notification.getOutRefundNo()).thenReturn("REF-001");

        if ("SUCCESS".equals(refundStatus)) {
            try {
                Object val = getRefundStatusSuccess();
                org.mockito.Mockito.doReturn(val).when(notification).getRefundStatus();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        org.mockito.Mockito.doReturn(notification)
                .when(notificationParser).parse(any(RequestParam.class), any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object getRefundStatusSuccess() throws Exception {
        java.lang.reflect.Method m = RefundNotification.class.getMethod("getRefundStatus");
        Class<?> returnType = m.getReturnType();
        if (returnType.isEnum()) {
            return Enum.valueOf((Class<Enum>) returnType, "SUCCESS");
        }
        throw new RuntimeException("getRefundStatus 返回类型不是枚举: " + returnType.getName());
    }
}
