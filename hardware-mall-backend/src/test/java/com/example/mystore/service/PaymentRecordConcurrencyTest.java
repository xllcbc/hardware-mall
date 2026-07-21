package com.example.mystore.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Transactional
class PaymentRecordConcurrencyTest {

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Test
    void sql_atomicUpdate_ensuresIdempotency() {
        Order order = new Order();
        order.setOrderNo("TEST_PAYMENT_001");
        order.setUserId(0L);
        order.setAddressId(1L);
        order.setLogisticsId(1L);
        order.setStatus(StatusConstants.ORDER_PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(new BigDecimal("100.00"));
        orderMapper.insert(order);

        PaymentRecord pr = new PaymentRecord();
        pr.setOrderId(order.getId());
        pr.setOutTradeNo("OUT_TRADE_NO_001");
        pr.setAmount(new BigDecimal("100.00"));
        pr.setStatus(PaymentRecord.STATUS_PENDING);
        paymentRecordMapper.insert(pr);

        LambdaUpdateWrapper<PaymentRecord> update1 = new LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getId, pr.getId())
                .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PENDING)
                .set(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID);
        LambdaUpdateWrapper<PaymentRecord> update2 = new LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getId, pr.getId())
                .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PENDING)
                .set(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID);

        int rows1 = paymentRecordMapper.update(null, update1);
        int rows2 = paymentRecordMapper.update(null, update2);

        assertThat(rows1 + rows2).isEqualTo(1);
        assertThat(rows1 == 1 || rows2 == 1).isTrue();
    }

    @Test
    void orderStatus_conditionalUpdate_ensuresIdempotency() {
        Order order = new Order();
        order.setOrderNo("TEST_PAYMENT_002");
        order.setUserId(0L);
        order.setAddressId(1L);
        order.setLogisticsId(1L);
        order.setStatus(StatusConstants.ORDER_PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(new BigDecimal("100.00"));
        orderMapper.insert(order);

        LambdaUpdateWrapper<Order> u1 = new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT)
                .set(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT);
        LambdaUpdateWrapper<Order> u2 = new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT)
                .set(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT);

        int r1 = orderMapper.update(null, u1);
        int r2 = orderMapper.update(null, u2);

        assertThat(r1 + r2).isEqualTo(1);
    }
}
