package com.example.mystore.mapper;

import com.example.mystore.entity.db.Order;
import org.junit.jupiter.api.BeforeEach;
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
@ActiveProfiles("test")
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Transactional
class OrderMapperTest {

    @Autowired
    private OrderMapper orderMapper;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0);

        // 插入今日已支付订单（状态 2-待发货）
        insertOrder("20260506000001", 2, todayStart.plusHours(2), 2);
        // 插入今日已支付订单（状态 4-已完成）
        insertOrder("20260506000002", 2, todayStart.plusHours(5), 4);
        // 插入今日已支付订单（状态 7-已退款）
        insertOrder("20260506000003", 3, todayStart.plusHours(8), 7);
        // 插入今日未支付订单（状态 1-待付款）
        insertOrder("20260506000004", 2, null, 1);
        // 插入昨日已支付订单
        insertOrder("20260505000001", 2, todayStart.minusHours(2), 2);
    }

    private void insertOrder(String orderNo, int status, LocalDateTime payTime, int orderStatus) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(1L);
        order.setAddressId(1L);
        order.setLogisticsId(1L);
        order.setStatus(orderStatus);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(new BigDecimal("100.00"));
        order.setPayTime(payTime);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(order);
    }

    @Test
    void testCountTodayPaidOrders() {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        Long count = orderMapper.countTodayPaidOrders(todayStart);
        // 今日已支付且状态不在 (1,5,7) 的有 2 条 (status=2, status=4)
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testSumTodaySales() {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        BigDecimal sum = orderMapper.sumTodaySales(todayStart);
        // 今日已支付且状态不在 (1,5,7) 的总金额 = 100 + 100 = 200
        assertThat(sum).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void testSelectStalePendingOrders() {
        // 查询当前时间之前的超时订单（应该包含 setUp 中 status=1 的订单）
        List<Order> staleOrders = orderMapper.selectStalePendingOrders(1, LocalDateTime.now().plusMinutes(1), 10);
        assertThat(staleOrders).hasSize(1);
        assertThat(staleOrders.get(0).getOrderNo()).isEqualTo("20260506000004");

        // 插入一条更旧的超时订单
        Order staleOrder = new Order();
        staleOrder.setOrderNo("20260501000001");
        staleOrder.setUserId(1L);
        staleOrder.setAddressId(1L);
        staleOrder.setLogisticsId(1L);
        staleOrder.setStatus(1);
        staleOrder.setTotalAmount(new BigDecimal("50.00"));
        staleOrder.setPayAmount(new BigDecimal("50.00"));
        staleOrder.setCreateTime(LocalDateTime.now().minusHours(2));
        staleOrder.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(staleOrder);

        List<Order> result = orderMapper.selectStalePendingOrders(1, LocalDateTime.now(), 10);
        assertThat(result).hasSize(2);
    }
}
