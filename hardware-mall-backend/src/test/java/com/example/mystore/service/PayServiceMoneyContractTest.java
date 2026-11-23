package com.example.mystore.service;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.db.User;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.service.impl.PayServiceImpl;
import com.wechat.pay.java.core.notification.NotificationParser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 支付/退款与微信网关之间的金额契约：网关只接收整数分，且与订单应付金额、支付记录金额一致。
 */
@ExtendWith(MockitoExtension.class)
class PayServiceMoneyContractTest {

    @BeforeAll
    static void initMybatisMetadata() {
        // LambdaUpdateWrapper.set() 会立即解析列名；单测没有 Spring/MyBatis 上下文，需要手动注册
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PaymentRecord.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @Mock PaymentRecordMapper paymentRecordMapper;
    @Mock OrderMapper orderMapper;
    @Mock OrderItemMapper orderItemMapper;
    @Mock UserMapper userMapper;
    @Mock WechatPayGateway wechatPayGateway;
    @Mock NotificationParser notificationParser;
    @Mock DingTalkAlertService dingTalkAlertService;
    @Mock SkuService skuService;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock PlatformTransactionManager transactionManager;
    @Mock TransactionStatus transactionStatus;

    @InjectMocks PayServiceImpl payService;

    @Test
    void prepaySendsOrderPayAmountInCentsToGateway() {
        Order order = newOrder("88.88");
        when(orderMapper.selectById(order.getId())).thenReturn(order);
        when(userMapper.selectById(order.getUserId())).thenReturn(newUser(order.getUserId()));
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(wechatPayGateway.prepay(order.getOrderNo(), 8_888L, "openid-7"))
                .thenReturn(Map.of("paySign", "sign"));

        Map<String, String> result = payService.prepay(order.getUserId(), order.getId());

        assertThat(result).containsEntry("paySign", "sign");
        verify(wechatPayGateway).prepay(order.getOrderNo(), 8_888L, "openid-7");
    }

    @Test
    void prepayRejectsSubCentOrderAmountWithoutCallingGateway() {
        Order order = newOrder("88.881");
        when(orderMapper.selectById(order.getId())).thenReturn(order);
        when(userMapper.selectById(order.getUserId())).thenReturn(newUser(order.getUserId()));
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        assertThatThrownBy(() -> payService.prepay(order.getUserId(), order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("两位小数");

        verifyNoInteractions(wechatPayGateway);
    }

    @Test
    void refundSendsPaymentAmountInCentsToGateway() {
        PaymentRecord record = newPaidRecord("88.88");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);
        when(paymentRecordMapper.update(any(), any())).thenReturn(1);

        RefundResult result = payService.refund(record.getOrderId(), "测试退款");

        assertThat(result).isEqualTo(RefundResult.ACCEPTED);
        verify(wechatPayGateway).createRefund(
                record.getOutTradeNo(), "REFUND_" + record.getOutTradeNo(), 8_888L, "测试退款");
    }

    @Test
    void refundRejectsSubCentAmountWithoutCallingGatewayOrClaimingRecord() {
        PaymentRecord record = newPaidRecord("88.881");
        when(paymentRecordMapper.selectOne(any())).thenReturn(record);

        assertThatThrownBy(() -> payService.refund(record.getOrderId(), "测试退款"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("两位小数");

        verifyNoInteractions(wechatPayGateway);
        verify(paymentRecordMapper, never()).update(any(), any());
    }

    private static Order newOrder(String payAmount) {
        Order order = new Order();
        order.setId(10L);
        order.setUserId(7L);
        order.setOrderNo("ORDER-10");
        order.setStatus(StatusConstants.ORDER_PENDING_PAYMENT);
        order.setPayAmount(new BigDecimal(payAmount));
        return order;
    }

    private static User newUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setOpenid("openid-7");
        return user;
    }

    private static PaymentRecord newPaidRecord(String amount) {
        PaymentRecord record = new PaymentRecord();
        record.setId(3L);
        record.setOrderId(10L);
        record.setOutTradeNo("ORDER-10");
        record.setAmount(new BigDecimal(amount));
        record.setStatus(PaymentRecord.STATUS_PAID);
        return record;
    }
}
