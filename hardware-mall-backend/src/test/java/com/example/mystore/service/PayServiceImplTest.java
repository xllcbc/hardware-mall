package com.example.mystore.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.User;
import com.example.mystore.event.LatePaymentRefundEvent;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.service.impl.PayServiceImpl;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import com.wechat.pay.java.service.refund.model.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@org.junit.jupiter.api.Tag("h2")
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PayServiceImplTest {

    @MockBean WechatPayGateway wechatPayGateway;
    @MockBean NotificationParser notificationParser;
    @MockBean DingTalkAlertService dingTalkAlertService;

    @Autowired PaymentRecordMapper paymentRecordMapper;
    @Autowired OrderMapper orderMapper;
    @Autowired SkuMapper skuMapper;
    @Autowired OrderItemMapper orderItemMapper;
    @Autowired UserMapper userMapper;

    @SpyBean
    PayServiceImpl payService;

    private Long setupOrder(int orderStatus) {
        return setupOrder(orderStatus, 1L);
    }

    private Long setupOrder(int orderStatus, Long userId) {
        Order order = new Order();
        order.setOrderNo("TEST_" + System.nanoTime());
        order.setUserId(userId);
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
    void shouldPublishLatePaymentRefundEvent_whenOrderCancelled(ApplicationEvents events) {
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PENDING, "OUT_003");

        boolean result = payService.processPaymentSuccess("OUT_003", "TXN_ID");

        assertThat(result).isTrue();
        assertThat(events.stream(LatePaymentRefundEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.orderId()).isEqualTo(orderId);
                    assertThat(event.outTradeNo()).isEqualTo("OUT_003");
                });
        // 支付事务内不得外呼微信退款
        verify(wechatPayGateway, never()).createRefund(anyString(), anyString(), any(), anyString());
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
    void refundCallback_shouldRollback_whenRefundFailed() {
        String outTradeNo = uniqueOutTradeNo();
        mockNotificationParser(outTradeNo, "FAIL");
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, outTradeNo);
        Long skuId = setupSku(10);
        setupOrderItem(orderId, skuId, 3);

        Map<String, String> result = payService.refundCallback("body", "sig", "nonce", "ts", "serial");

        assertThat(result).containsEntry("code", "SUCCESS");
        verify(dingTalkAlertService).alert(eq("REFUND_DEFINITE_FAILED"), eq(orderId), anyString());

        PaymentRecord pr = paymentRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getOutTradeNo, outTradeNo));
        // 回退到可重试态 PAID
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_PAID);

        Order order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(StatusConstants.ORDER_REFUND_FAILED);
        // 退款未成功, 不回补库存
        assertThat(skuMapper.selectById(skuId).getStock()).isEqualTo(10);
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
        // 退款成功只更新支付记录, 订单保持已取消
        assertThat(paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getOutTradeNo, outTradeNo)).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDED);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_CANCELLED);
    }

    @Test
    void refundCallback_shouldHeal_whenRecordStillPaid() {
        // 响应丢失滞留态: 退款已发起但本地停留 PAID, 微信成功后回调必须能自愈
        String outTradeNo = uniqueOutTradeNo();
        mockNotificationParser(outTradeNo, "SUCCESS");
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, outTradeNo);
        Long skuId = setupSku(10);
        setupOrderItem(orderId, skuId, 3);

        Map<String, String> result = payService.refundCallback("body", "sig", "nonce", "ts", "serial");

        assertThat(result).containsEntry("code", "SUCCESS");

        PaymentRecord pr = paymentRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getOutTradeNo, outTradeNo));
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_REFUNDED);

        Order order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(StatusConstants.ORDER_REFUNDED);
        assertThat(skuMapper.selectById(skuId).getStock()).isEqualTo(13);
    }

    // ==================== refund 三态 ====================

    @Test
    void refund_shouldReturnUnknownAndStayRefunding_whenWechatCallTimesOut() {
        // claim-first: CAS PAID→REFUNDING 在外呼之前; 超时/响应丢失不能判定结果, 保持退款中
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());

        // 假网关模拟超时/响应丢失
        doThrow(new RuntimeException("timeout")).when(wechatPayGateway)
                .createRefund(anyString(), anyString(), any(), anyString());

        RefundResult result = payService.refund(orderId, "测试退款");

        assertThat(result).isEqualTo(RefundResult.UNKNOWN);
        PaymentRecord pr = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId));
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_REFUNDING);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_REFUNDING);
        verify(dingTalkAlertService).alert(eq("REFUND_RESULT_UNKNOWN"), eq(orderId), anyString());
    }

    @Test
    void refund_shouldReturnUnknown_whenWechatReturns5xx() {
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());
        ServiceException serverError = serviceException(500, "SYSTEM_ERROR", "系统错误");
        doThrow(serverError).when(wechatPayGateway)
                .createRefund(anyString(), anyString(), any(), anyString());

        RefundResult result = payService.refund(orderId, "测试退款");

        assertThat(result).isEqualTo(RefundResult.UNKNOWN);
        assertThat(paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId)).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDING);
        verify(dingTalkAlertService).alert(eq("REFUND_RESULT_UNKNOWN"), eq(orderId), anyString());
    }

    // ==================== 微信支付网关接缝 ====================

    @Test
    void prepay_shouldReturnWechatParams_whenGatewayAccepts() {
        User user = setupUser("o-test-prepay-ok");
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_PAYMENT, user.getId());
        Map<String, String> params = Map.of(
                "appId", "wx-test-appid-for-ci",
                "timeStamp", "1700000000",
                "nonceStr", "nonce",
                "packageValue", "prepay_id=wx001",
                "signType", "RSA",
                "paySign", "sign");
        when(wechatPayGateway.prepay(anyString(), any(), eq("o-test-prepay-ok"))).thenReturn(params);

        Map<String, String> result = payService.prepay(user.getId(), orderId);

        assertThat(result).isEqualTo(params);
        verify(wechatPayGateway).prepay(anyString(), eq(new BigDecimal("100.00")), eq("o-test-prepay-ok"));
    }

    @Test
    void refund_shouldReturnAcceptedWithoutAlert_whenGatewayAccepts() {
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());

        RefundResult result = payService.refund(orderId, "测试退款");

        assertThat(result).isEqualTo(RefundResult.ACCEPTED);
        PaymentRecord pr = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId));
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_REFUNDING);
        verify(wechatPayGateway).createRefund(
                eq(record.getOutTradeNo()), eq("REFUND_" + record.getOutTradeNo()),
                eq(new BigDecimal("100.00")), eq("测试退款"));
        verifyNoInteractions(dingTalkAlertService);
    }

    @Test
    void refund_shouldRollbackToFailedOrder_whenGatewayRejectsWith4xx() {
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());
        ServiceException rejected = serviceException(400, "PARAM_ERROR", "参数错误");
        doThrow(rejected).when(wechatPayGateway)
                .createRefund(anyString(), anyString(), any(), anyString());

        RefundResult result = payService.refund(orderId, "测试退款");

        assertThat(result).isEqualTo(RefundResult.DEFINITE_FAILED);
        PaymentRecord pr = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId));
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_PAID);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_REFUND_FAILED);
        verify(dingTalkAlertService).alert(eq("REFUND_DEFINITE_FAILED"), eq(orderId), anyString());
    }

    @Test
    void refund_shouldRollback_whenRequestWasNotSent() {
        // 本地校验/签名/配置错误: 请求未发出, 属于确定失败
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());
        doThrow(new IllegalArgumentException("private key invalid")).when(wechatPayGateway)
                .createRefund(anyString(), anyString(), any(), anyString());

        RefundResult result = payService.refund(orderId, "测试退款");

        assertThat(result).isEqualTo(RefundResult.DEFINITE_FAILED);
        assertThat(paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId)).getStatus())
                .isEqualTo(PaymentRecord.STATUS_PAID);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_REFUND_FAILED);
        verify(dingTalkAlertService).alert(eq("REFUND_DEFINITE_FAILED"), eq(orderId), anyString());
    }

    @Test
    void refund_shouldKeepOrderCancelled_whenLatePaymentRefundResultUnknown() {
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());
        doThrow(new RuntimeException("timeout")).when(wechatPayGateway)
                .createRefund(anyString(), anyString(), any(), anyString());

        RefundResult result = payService.refund(orderId, "迟到支付自动退款");

        assertThat(result).isEqualTo(RefundResult.UNKNOWN);
        assertThat(paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId)).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDING);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_CANCELLED);
        verify(dingTalkAlertService).alert(eq("REFUND_RESULT_UNKNOWN"), eq(orderId), anyString());
    }

    @Test
    void refund_shouldKeepOrderCancelled_whenLatePaymentRefundRejectedWith4xx() {
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());
        ServiceException rejected = serviceException(400, "PARAM_ERROR", "参数错误");
        doThrow(rejected).when(wechatPayGateway)
                .createRefund(anyString(), anyString(), any(), anyString());

        RefundResult result = payService.refund(orderId, "迟到支付自动退款");

        assertThat(result).isEqualTo(RefundResult.DEFINITE_FAILED);
        assertThat(paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId)).getStatus())
                .isEqualTo(PaymentRecord.STATUS_PAID);
        // 已取消订单保持 5, 不因退款失败改成 9
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_CANCELLED);
        verify(dingTalkAlertService).alert(eq("REFUND_DEFINITE_FAILED"), eq(orderId), anyString());
    }

    // ==================== 退款对账 ====================

    @Test
    void reconcileRefund_shouldResubmitWithSameRefundNo_whenRefundingRecordHasNoRefundOrder() {
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, uniqueOutTradeNo());
        doThrow(serviceException(404, "RESOURCE_NOT_EXISTS", "退款单不存在"))
                .when(wechatPayGateway).queryRefund("REFUND_" + record.getOutTradeNo());

        payService.reconcileRefund(record);

        verify(wechatPayGateway).createRefund(
                eq(record.getOutTradeNo()), eq("REFUND_" + record.getOutTradeNo()),
                eq(new BigDecimal("100.00")), anyString());
        PaymentRecord pr = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId));
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_REFUNDING);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_REFUNDING);
        verify(dingTalkAlertService, never()).alert(eq("REFUND_ROLLBACK"), eq(orderId), anyString());
    }

    @Test
    void reconcileRefund_shouldKeepRefundingAndNotResubmit_whenWechatQueryReturns5xx() {
        // 5xx 无法判定, 不得误判为“无此退款单”而回退或重发
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, uniqueOutTradeNo());
        doThrow(serviceException(500, "SYSTEM_ERROR", "系统错误"))
                .when(wechatPayGateway).queryRefund("REFUND_" + record.getOutTradeNo());

        payService.reconcileRefund(record);

        verify(wechatPayGateway, never()).createRefund(anyString(), anyString(), any(), anyString());
        PaymentRecord pr = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId));
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_REFUNDING);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_REFUNDING);
        verify(dingTalkAlertService).alert(eq("REFUND_RESULT_UNKNOWN"), eq(orderId), anyString());
    }

    @Test
    void reconcileRefund_shouldKeepRefunding_when404HasUnexpectedErrorCode() {
        // 只有 404 + RESOURCE_NOT_EXISTS 才代表“无退款单”; 其他 404 属查询异常, 不得触发补发
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, uniqueOutTradeNo());
        doThrow(serviceException(404, "NOT_FOUND", "接口不存在"))
                .when(wechatPayGateway).queryRefund("REFUND_" + record.getOutTradeNo());

        payService.reconcileRefund(record);

        verify(wechatPayGateway, never()).createRefund(anyString(), anyString(), any(), anyString());
        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDING);
        verify(dingTalkAlertService).alert(eq("REFUND_RESULT_UNKNOWN"), eq(orderId), anyString());
    }

    @Test
    void reconcileRefund_shouldKeepRefunding_whenRefundQueryThrowsNetworkError() {
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, uniqueOutTradeNo());
        doThrow(new RuntimeException("timeout"))
                .when(wechatPayGateway).queryRefund("REFUND_" + record.getOutTradeNo());

        payService.reconcileRefund(record);

        verify(wechatPayGateway, never()).createRefund(anyString(), anyString(), any(), anyString());
        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDING);
        verify(dingTalkAlertService).alert(eq("REFUND_RESULT_UNKNOWN"), eq(orderId), anyString());
    }

    @Test
    void reconcileRefund_shouldConfirmAndRestoreStock_whenRefundingOrderRefundSucceeds() {
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, uniqueOutTradeNo());
        Long skuId = setupSku(10);
        setupOrderItem(orderId, skuId, 3);
        when(wechatPayGateway.queryRefund("REFUND_" + record.getOutTradeNo()))
                .thenReturn(refundWithStatus(Status.SUCCESS));

        payService.reconcileRefund(record);

        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDED);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_REFUNDED);
        assertThat(skuMapper.selectById(skuId).getStock()).isEqualTo(13);
    }

    @Test
    void reconcileRefund_shouldRollbackOrderToFailed_whenRefundExplicitlyClosed() {
        Long orderId = setupOrder(StatusConstants.ORDER_REFUNDING);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, uniqueOutTradeNo());
        when(wechatPayGateway.queryRefund("REFUND_" + record.getOutTradeNo()))
                .thenReturn(refundWithStatus(Status.CLOSED));

        payService.reconcileRefund(record);

        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_PAID);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_REFUND_FAILED);
        verify(dingTalkAlertService).alert(eq("REFUND_ROLLBACK"), eq(orderId), anyString());
    }

    @Test
    void reconcileRefund_shouldMarkRefundedWithoutRestoringStock_whenCancelledOrderRefundSucceeds() {
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, uniqueOutTradeNo());
        Long skuId = setupSku(10);
        setupOrderItem(orderId, skuId, 3);
        when(wechatPayGateway.queryRefund("REFUND_" + record.getOutTradeNo()))
                .thenReturn(refundWithStatus(Status.SUCCESS));

        payService.reconcileRefund(record);

        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDED);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_CANCELLED);
        assertThat(skuMapper.selectById(skuId).getStock()).isEqualTo(10);
    }

    @Test
    void reconcileRefund_shouldResubmitAndKeepOrderCancelled_whenCancelledOrderHasNoRefundOrder() {
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, uniqueOutTradeNo());
        doThrow(serviceException(404, "RESOURCE_NOT_EXISTS", "退款单不存在"))
                .when(wechatPayGateway).queryRefund("REFUND_" + record.getOutTradeNo());

        payService.reconcileRefund(record);

        verify(wechatPayGateway).createRefund(
                eq(record.getOutTradeNo()), eq("REFUND_" + record.getOutTradeNo()),
                eq(new BigDecimal("100.00")), anyString());
        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDING);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_CANCELLED);
    }

    @Test
    void reconcileRefund_shouldKeepRefundingAndAlert_whenResubmitResultUnknown() {
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_REFUNDING, uniqueOutTradeNo());
        doThrow(serviceException(404, "RESOURCE_NOT_EXISTS", "退款单不存在"))
                .when(wechatPayGateway).queryRefund("REFUND_" + record.getOutTradeNo());
        doThrow(new RuntimeException("timeout"))
                .when(wechatPayGateway).createRefund(anyString(), anyString(), any(), anyString());

        payService.reconcileRefund(record);

        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDING);
        verify(dingTalkAlertService).alert(eq("REFUND_RESULT_UNKNOWN"), eq(orderId), anyString());
    }

    @Test
    void reconcileRefund_shouldQueryThenResubmit_whenPaidCancelledOrderHasNoRefundOrder() {
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());
        doThrow(serviceException(404, "RESOURCE_NOT_EXISTS", "退款单不存在"))
                .when(wechatPayGateway).queryRefund("REFUND_" + record.getOutTradeNo());

        payService.reconcileRefund(record);

        verify(wechatPayGateway).createRefund(
                eq(record.getOutTradeNo()), eq("REFUND_" + record.getOutTradeNo()),
                eq(new BigDecimal("100.00")), anyString());
        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDING);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_CANCELLED);
    }

    @Test
    void reconcileRefund_shouldKeepPaidAndAlert_whenPaidCancelledOrderQueryReturns5xx() {
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());
        doThrow(serviceException(500, "SYSTEM_ERROR", "系统错误"))
                .when(wechatPayGateway).queryRefund("REFUND_" + record.getOutTradeNo());

        payService.reconcileRefund(record);

        verify(wechatPayGateway, never()).createRefund(anyString(), anyString(), any(), anyString());
        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_PAID);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_CANCELLED);
        verify(dingTalkAlertService).alert(eq("REFUND_RESULT_UNKNOWN"), eq(orderId), anyString());
    }

    @Test
    void reconcileRefund_shouldMarkRefunded_whenPaidCancelledOrderRefundAlreadySucceeded() {
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());
        Long skuId = setupSku(10);
        setupOrderItem(orderId, skuId, 3);
        when(wechatPayGateway.queryRefund("REFUND_" + record.getOutTradeNo()))
                .thenReturn(refundWithStatus(Status.SUCCESS));

        payService.reconcileRefund(record);

        PaymentRecord updated = paymentRecordMapper.selectById(record.getId());
        assertThat(updated.getStatus()).isEqualTo(PaymentRecord.STATUS_REFUNDED);
        assertThat(updated.getRefundAmount()).isEqualByComparingTo("100.00");
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_CANCELLED);
        assertThat(skuMapper.selectById(skuId).getStock()).isEqualTo(10);
    }

    @Test
    void reconcileRefund_shouldKeepPaidAndAlertDefiniteFailure_whenPaidCancelledOrderRefundClosed() {
        // 已支付+已取消没有退款中状态可回退, 不得宣称“已回退”
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());
        when(wechatPayGateway.queryRefund("REFUND_" + record.getOutTradeNo()))
                .thenReturn(refundWithStatus(Status.CLOSED));

        payService.reconcileRefund(record);

        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_PAID);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_CANCELLED);
        verify(dingTalkAlertService).alert(eq("REFUND_DEFINITE_FAILED"), eq(orderId), anyString());
        verify(dingTalkAlertService, never()).alert(eq("REFUND_ROLLBACK"), eq(orderId), anyString());
    }

    @Test
    void reconcileRefund_shouldClaimRefunding_whenPaidCancelledOrderRefundIsProcessing() {
        Long orderId = setupOrder(StatusConstants.ORDER_CANCELLED);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());
        when(wechatPayGateway.queryRefund("REFUND_" + record.getOutTradeNo()))
                .thenReturn(refundWithStatus(Status.PROCESSING));

        payService.reconcileRefund(record);

        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_REFUNDING);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo(StatusConstants.ORDER_CANCELLED);
    }

    @Test
    void reconcileRefund_shouldIgnorePaidRecord_whenOrderNotCancelled() {
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_SHIPMENT);
        PaymentRecord record = setupPaymentRecord(orderId, PaymentRecord.STATUS_PAID, uniqueOutTradeNo());

        payService.reconcileRefund(record);

        verifyNoInteractions(wechatPayGateway);
        assertThat(paymentRecordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(PaymentRecord.STATUS_PAID);
    }

    @Test
    void queryWechatOrder_shouldDelegateToGateway() {
        Transaction txn = new Transaction();
        when(wechatPayGateway.queryOrder("OUT_QUERY")).thenReturn(txn);

        assertThat(payService.queryWechatOrder("OUT_QUERY")).isSameAs(txn);
    }

    // ==================== M7 事务分段 ====================

    @Test
    void callback_shouldReturnFail_whenNotificationInvalid() {
        // M7: catch 在事务外 —— 解析/处理抛异常必须得到干净的 FAIL, 而非 UnexpectedRollbackException 500
        when(notificationParser.parse(any(RequestParam.class), any()))
                .thenThrow(new RuntimeException("bad signature"));

        Map<String, String> result = payService.callback("body", "sig", "nonce", "ts", "serial");

        assertThat(result).containsEntry("code", "FAIL");
        verify(dingTalkAlertService).alert(eq("PAY_CALLBACK_FAIL"), anyString());
    }

    @Test
    void prepay_shouldMarkClosed_whenWechatPrepayFails() {
        // M7: HTTP 失败后, 本次新建的 PENDING 记录必须真的落库为 CLOSED
        // （旧实现写在外层 @Transactional 事务内, 随 rethrow 被整体回滚, 是死代码）
        User user = setupUser("o-test-prepay");
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_PAYMENT, user.getId());

        // 假网关模拟微信下单失败
        when(wechatPayGateway.prepay(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("prepay failed"));

        assertThatThrownBy(() -> payService.prepay(user.getId(), orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("创建支付订单失败");

        PaymentRecord pr = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId));
        assertThat(pr).isNotNull();
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_CLOSED);
    }

    // ==================== prepay 失败后复用支付记录 ====================

    @Test
    void prepay_shouldReuseClosedRecord_whenRetryAfterPrepayFailure() {
        // 首次 prepay 失败留下一条 CLOSED; 再次支付必须复用同一行, 不能新插撞 uk_out_trade_no
        User user = setupUser("o-test-prepay-retry");
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_PAYMENT, user.getId());
        PaymentRecord closed = setupPaymentRecord(
                orderId, PaymentRecord.STATUS_CLOSED, orderNoOf(orderId));

        when(wechatPayGateway.prepay(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("prepay failed"));

        assertThatThrownBy(() -> payService.prepay(user.getId(), orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("创建支付订单失败");

        java.util.List<PaymentRecord> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getId()).isEqualTo(closed.getId());
        assertThat(records.get(0).getStatus()).isEqualTo(PaymentRecord.STATUS_CLOSED);
    }

    @Test
    void prepay_shouldReusePendingRecord_whenRetry() {
        User user = setupUser("o-test-prepay-pending");
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_PAYMENT, user.getId());
        PaymentRecord pending = setupPaymentRecord(
                orderId, PaymentRecord.STATUS_PENDING, orderNoOf(orderId));

        when(wechatPayGateway.prepay(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("prepay failed"));

        assertThatThrownBy(() -> payService.prepay(user.getId(), orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("创建支付订单失败");

        java.util.List<PaymentRecord> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getId()).isEqualTo(pending.getId());
        assertThat(records.get(0).getStatus()).isEqualTo(PaymentRecord.STATUS_PENDING);
    }

    @Test
    void prepay_shouldReject_whenRecordIsPaid() {
        User user = setupUser("o-test-prepay-paid");
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_PAYMENT, user.getId());
        PaymentRecord paid = setupPaymentRecord(
                orderId, PaymentRecord.STATUS_PAID, orderNoOf(orderId));

        assertThatThrownBy(() -> payService.prepay(user.getId(), orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已支付");

        PaymentRecord pr = paymentRecordMapper.selectById(paid.getId());
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_PAID);
    }

    @Test
    void prepay_shouldReject_whenRecordIsRefunding() {
        User user = setupUser("o-test-prepay-refunding");
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_PAYMENT, user.getId());
        PaymentRecord refunding = setupPaymentRecord(
                orderId, PaymentRecord.STATUS_REFUNDING, orderNoOf(orderId));

        assertThatThrownBy(() -> payService.prepay(user.getId(), orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退款中");

        PaymentRecord pr = paymentRecordMapper.selectById(refunding.getId());
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_REFUNDING);
    }

    @Test
    void prepay_shouldReject_whenRecordIsRefunded() {
        User user = setupUser("o-test-prepay-refunded");
        Long orderId = setupOrder(StatusConstants.ORDER_PENDING_PAYMENT, user.getId());
        PaymentRecord refunded = setupPaymentRecord(
                orderId, PaymentRecord.STATUS_REFUNDED, orderNoOf(orderId));

        assertThatThrownBy(() -> payService.prepay(user.getId(), orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已退款");

        PaymentRecord pr = paymentRecordMapper.selectById(refunded.getId());
        assertThat(pr.getStatus()).isEqualTo(PaymentRecord.STATUS_REFUNDED);
    }

    // ==================== helper ====================

    private static Refund refundWithStatus(Status status) {
        Refund refund = new Refund();
        refund.setStatus(status);
        return refund;
    }

    private static ServiceException serviceException(int httpStatusCode, String errorCode, String errorMessage) {
        ServiceException e = org.mockito.Mockito.mock(ServiceException.class);
        when(e.getHttpStatusCode()).thenReturn(httpStatusCode);
        when(e.getErrorCode()).thenReturn(errorCode);
        when(e.getErrorMessage()).thenReturn(errorMessage);
        return e;
    }

    private User setupUser(String openid) {
        User user = new User();
        user.setOpenid(openid);
        user.setRole(1);
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    private String orderNoOf(Long orderId) {
        return orderMapper.selectById(orderId).getOrderNo();
    }

    private Long setupSku(int stock) {        Sku sku = new Sku();
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
