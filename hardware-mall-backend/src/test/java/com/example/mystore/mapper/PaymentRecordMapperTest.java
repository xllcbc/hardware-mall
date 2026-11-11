package com.example.mystore.mapper;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.PaymentRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.junit.jupiter.api.Tag("h2")
@ActiveProfiles("test")
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Transactional
class PaymentRecordMapperTest {

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Test
    void shouldSelectRefundingRecordsRegardlessOfOrderStatus() {
        Long cancelled = insertOrder(StatusConstants.ORDER_CANCELLED);
        Long pendingShipment = insertOrder(StatusConstants.ORDER_PENDING_SHIPMENT);
        Long refundedOrder = insertOrder(StatusConstants.ORDER_REFUNDED);
        LocalDateTime stale = LocalDateTime.now().minusHours(1);

        Long r1 = insertRecord(cancelled, PaymentRecord.STATUS_REFUNDING, stale);
        Long r2 = insertRecord(pendingShipment, PaymentRecord.STATUS_REFUNDING, stale.plusMinutes(1));
        Long r3 = insertRecord(refundedOrder, PaymentRecord.STATUS_REFUNDING, stale.plusMinutes(2));

        List<PaymentRecord> rows = paymentRecordMapper.selectStaleRefundRecords(
                LocalDateTime.now().minusMinutes(10), 100);

        assertThat(rows).extracting(PaymentRecord::getId).containsExactly(r1, r2, r3);
    }

    @Test
    void shouldSelectPaidRecordsOnlyWhenOrderCancelled() {
        Long cancelled = insertOrder(StatusConstants.ORDER_CANCELLED);
        Long pendingShipment = insertOrder(StatusConstants.ORDER_PENDING_SHIPMENT);
        LocalDateTime stale = LocalDateTime.now().minusHours(1);

        Long paidCancelled = insertRecord(cancelled, PaymentRecord.STATUS_PAID, stale);
        insertRecord(pendingShipment, PaymentRecord.STATUS_PAID, stale);

        List<PaymentRecord> rows = paymentRecordMapper.selectStaleRefundRecords(
                LocalDateTime.now().minusMinutes(10), 100);

        assertThat(rows).extracting(PaymentRecord::getId).containsExactly(paidCancelled);
    }

    @Test
    void shouldExcludeFreshAndConvergedRecords() {
        Long cancelled = insertOrder(StatusConstants.ORDER_CANCELLED);
        Long pendingShipment = insertOrder(StatusConstants.ORDER_PENDING_SHIPMENT);
        LocalDateTime stale = LocalDateTime.now().minusHours(1);
        LocalDateTime fresh = LocalDateTime.now();

        insertRecord(pendingShipment, PaymentRecord.STATUS_REFUNDING, fresh);
        insertRecord(cancelled, PaymentRecord.STATUS_PAID, fresh);
        insertRecord(cancelled, PaymentRecord.STATUS_REFUNDED, stale);
        insertRecord(cancelled, PaymentRecord.STATUS_CLOSED, stale);
        insertRecord(pendingShipment, PaymentRecord.STATUS_PENDING, stale);

        List<PaymentRecord> rows = paymentRecordMapper.selectStaleRefundRecords(
                LocalDateTime.now().minusMinutes(10), 100);

        assertThat(rows).isEmpty();
    }

    @Test
    void shouldOrderByUpdateTimeAndApplyLimit() {
        Long orderId = insertOrder(StatusConstants.ORDER_REFUNDING);
        LocalDateTime now = LocalDateTime.now();
        Long newest = insertRecord(orderId, PaymentRecord.STATUS_REFUNDING, now.minusMinutes(30));
        Long oldest = insertRecord(orderId, PaymentRecord.STATUS_REFUNDING, now.minusMinutes(90));
        Long middle = insertRecord(orderId, PaymentRecord.STATUS_REFUNDING, now.minusMinutes(60));

        List<PaymentRecord> rows = paymentRecordMapper.selectStaleRefundRecords(
                now.minusMinutes(10), 2);

        assertThat(rows).extracting(PaymentRecord::getId).containsExactly(oldest, middle);
        assertThat(rows).extracting(PaymentRecord::getId).doesNotContain(newest);
    }

    private Long insertOrder(int status) {
        Order order = new Order();
        order.setOrderNo("ORDER_" + System.nanoTime());
        order.setUserId(1L);
        order.setAddressId(1L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(new BigDecimal("100.00"));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(order);
        return order.getId();
    }

    private Long insertRecord(Long orderId, int status, LocalDateTime updateTime) {
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(orderId);
        record.setOutTradeNo("OUT_" + System.nanoTime());
        record.setAmount(new BigDecimal("100.00"));
        record.setStatus(status);
        record.setCreateTime(updateTime);
        record.setUpdateTime(updateTime);
        paymentRecordMapper.insert(record);
        return record.getId();
    }
}
