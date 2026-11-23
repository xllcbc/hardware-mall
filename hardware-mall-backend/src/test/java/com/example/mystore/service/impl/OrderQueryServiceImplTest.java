package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Address;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.vo.DashboardStatsVO;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.entity.vo.RecentOrderVO;
import com.example.mystore.mapper.AddressMapper;
import com.example.mystore.mapper.LogisticsMapper;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单查询模块（读模型）: 只接收订单与最新支付记录, 不写库、不依赖支付网关/SKU/等级定价/微信。
 */
@ExtendWith(MockitoExtension.class)
class OrderQueryServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private LogisticsMapper logisticsMapper;
    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @InjectMocks
    private OrderQueryServiceImpl queryService;

    private Address address;
    private Logistics logistics;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 生成 SQL 片段时需要 Order 列元数据（单测无 Spring 上下文）
        TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queryService, "mchId", "mch-test-001");

        address = new Address();
        address.setId(1L);
        address.setConsignee("张三");
        address.setPhone("13800001001");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园南路88号");

        logistics = new Logistics();
        logistics.setId(1L);
        logistics.setName("德邦物流");
        logistics.setStatus(1);
    }

    @Test
    void adminOrderDetail_assemblesDisplayFieldsAndTexts() {
        Order order = order(1L, StatusConstants.ORDER_SHIPPED);
        order.setDeliveryType(StatusConstants.DELIVERY_TYPE_LOCAL);
        order.setLogisticsNo("PS123");
        order.setBuyerRemark("放门口");
        order.setCancelReason(null);
        order.setAdminRemark(null);
        order.setPayTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        order.setShipTime(LocalDateTime.of(2026, 1, 2, 10, 0));
        order.setCreateTime(LocalDateTime.of(2026, 1, 1, 9, 0));
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        PaymentRecord paid = payment(1L, PaymentRecord.STATUS_PAID);
        paid.setOutTradeNo("OUT_1");
        paid.setTransactionId("TXN_1");
        when(paymentRecordMapper.selectOne(any())).thenReturn(paid);

        OrderVO vo = queryService.getAdminOrderById(1L);

        assertThat(vo.getStatusText()).isEqualTo("已发货");
        assertThat(vo.getDeliveryTypeText()).isEqualTo("同城配送");
        assertThat(vo.getLogisticsName()).isEqualTo("德邦物流");
        assertThat(vo.getReceiverName()).isEqualTo("张三");
        assertThat(vo.getReceiverPhone()).isEqualTo("13800001001");
        assertThat(vo.getReceiverAddress()).isEqualTo("广东省深圳市南山区科技园南路88号");
        assertThat(vo.getMerchantId()).isEqualTo("mch-test-001");
        assertThat(vo.getMerchantTradeNo()).isEqualTo("OUT_1");
        assertThat(vo.getTransactionId()).isEqualTo("TXN_1");
        assertThat(vo.getPaymentStatus()).isEqualTo(PaymentRecord.STATUS_PAID);
        assertThat(vo.getPaymentStatusText()).isEqualTo("已支付");
    }

    @Test
    void adminOrderPage_batchLoadsLatestPaymentStatusPerOrder() {
        Order cancelled = order(1L, StatusConstants.ORDER_CANCELLED);
        Order shipped = order(2L, StatusConstants.ORDER_SHIPPED);
        Order withoutPayment = order(3L, StatusConstants.ORDER_CANCELLED);
        Page<Order> orderPage = new Page<>(1, 20, 3);
        orderPage.setRecords(List.of(cancelled, shipped, withoutPayment));

        when(orderMapper.selectPage(any(), any())).thenReturn(orderPage);
        when(addressMapper.selectById(any())).thenReturn(address);
        when(logisticsMapper.selectById(any())).thenReturn(logistics);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        PaymentRecord latestPaid = payment(1L, PaymentRecord.STATUS_PAID);
        PaymentRecord olderRefunded = payment(1L, PaymentRecord.STATUS_REFUNDED);
        PaymentRecord refunded = payment(2L, PaymentRecord.STATUS_REFUNDED);
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(latestPaid, olderRefunded, refunded));

        Page<OrderVO> result = queryService.getAdminOrderPage(null, null, null, null, null, 1, 20);

        assertThat(result.getRecords()).extracting(OrderVO::getPaymentStatus)
                .containsExactly(PaymentRecord.STATUS_PAID, PaymentRecord.STATUS_REFUNDED, null);
        assertThat(result.getRecords()).extracting(OrderVO::getPaymentStatusText)
                .containsExactly("已支付", "已退款", "未支付");
        verify(paymentRecordMapper, times(1)).selectList(any());
        verify(paymentRecordMapper, never()).selectOne(any());
    }

    @Test
    void adminOrderPage_invalidDateFormat_rejectedBeforeQuery() {
        assertThatThrownBy(() -> queryService.getAdminOrderPage(null, null, null, "2026/01/01", null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("日期格式应为 yyyy-MM-dd");
        verify(orderMapper, never()).selectPage(any(), any());
    }

    @Test
    void userOrderPage_assemblesViewWithoutPricingSnapshot() {
        Order order = order(1L, StatusConstants.ORDER_PENDING_PAYMENT);
        Page<Order> orderPage = new Page<>(1, 20, 1);
        orderPage.setRecords(List.of(order));

        when(orderMapper.selectPage(any(), any())).thenReturn(orderPage);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        OrderItem item = new OrderItem();
        item.setId(9L);
        item.setOrderId(1L);
        item.setSkuId(5L);
        item.setSpuId(3L);
        item.setProductName("防盗门锁 C级");
        item.setPrice(new BigDecimal("20.00"));
        item.setQuantity(1);
        item.setSubtotal(new BigDecimal("20.00"));
        item.setLevelId(2L);
        item.setLevelName("批发A");
        item.setPricingRate(new BigDecimal("0.8000"));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);

        Page<OrderVO> result = queryService.getOrderPage(2L, null, 1, 20);

        assertThat(result.getRecords()).hasSize(1);
        OrderVO vo = result.getRecords().get(0);
        assertThat(vo.getOrderNo()).isEqualTo("SO1");
        assertThat(vo.getStatusText()).isEqualTo("待付款");
        assertThat(vo.getPaymentStatusText()).isEqualTo("未支付");
        assertThat(vo.getReceiverName()).isEqualTo("张三");
        assertThat(vo.getItems()).hasSize(1);
        assertThat(vo.getItems().get(0).getLevelId()).isNull();
        assertThat(vo.getItems().get(0).getLevelName()).isNull();
        assertThat(vo.getItems().get(0).getPricingRate()).isNull();
    }

    @Test
    void orderByOrderNo_userViewHidesPricingSnapshotWhileAdminDetailKeepsIt() {
        Order order = order(100L, StatusConstants.ORDER_PENDING_PAYMENT);
        order.setOrderNo("SO-SNAPSHOT");
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setOrderId(100L);
        item.setSkuId(5L);
        item.setSpuId(3L);
        item.setProductName("防盗门锁 C级");
        item.setPrice(new BigDecimal("20.00"));
        item.setLevelId(2L);
        item.setLevelName("批发A");
        item.setPricingRate(new BigDecimal("0.8000"));
        item.setQuantity(1);
        item.setSubtotal(new BigDecimal("20.00"));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));

        OrderVO userView = queryService.getOrderByOrderNo("SO-SNAPSHOT");
        assertThat(userView.getItems().get(0).getLevelId()).isNull();
        assertThat(userView.getItems().get(0).getLevelName()).isNull();
        assertThat(userView.getItems().get(0).getPricingRate()).isNull();

        OrderVO adminView = queryService.getAdminOrderById(100L);
        assertThat(adminView.getItems().get(0).getLevelId()).isEqualTo(2L);
        assertThat(adminView.getItems().get(0).getLevelName()).isEqualTo("批发A");
        assertThat(adminView.getItems().get(0).getPricingRate()).isEqualByComparingTo("0.8000");
    }

    @Test
    void orderByOrderNo_missing_throwsNotFound() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> queryService.getOrderByOrderNo("SO-MISSING"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void userOrderView_status8_exposesCancelReasonAndRefundRequestedText() {
        Order order = order(1L, StatusConstants.ORDER_REFUND_REQUESTED);
        order.setCancelReason("不想要了");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);

        OrderVO vo = queryService.getOrderView(1L);

        assertThat(vo.getStatusText()).isEqualTo("退款申请中");
        assertThat(vo.getCancelReason()).isEqualTo("不想要了");
    }

    @Test
    void adminOrderDetail_rejectedRefund_exposesAdminRemark() {
        // 拒绝退款后状态回退 2/3, 拒绝原因经 adminRemark 透出给用户
        Order order = order(1L, StatusConstants.ORDER_PENDING_SHIPMENT);
        order.setAdminRemark("凭证不足");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(addressMapper.selectById(1L)).thenReturn(address);
        when(logisticsMapper.selectById(1L)).thenReturn(logistics);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);

        OrderVO vo = queryService.getAdminOrderById(1L);

        assertThat(vo.getStatus()).isEqualTo(StatusConstants.ORDER_PENDING_SHIPMENT);
        assertThat(vo.getAdminRemark()).isEqualTo("凭证不足");
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminOrderPage_appliesOrderNoAndInclusiveDateFilters() {
        when(orderMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 20));

        queryService.getAdminOrderPage(null, null, " SO123 ", "2026-01-01", "2026-01-31", 1, 20);

        ArgumentCaptor<LambdaQueryWrapper<Order>> captor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(any(), captor.capture());
        LambdaQueryWrapper<Order> wrapper = captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("order_no").contains("create_time");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("SO123")
                .contains(LocalDateTime.of(2026, 1, 1, 0, 0))
                .contains(LocalDateTime.of(2026, 2, 1, 0, 0));
    }

    @Test
    void orderStats_countsEachDashboardStatus() {
        when(orderMapper.selectCount(any()))
                .thenReturn(11L)
                .thenReturn(12L)
                .thenReturn(13L)
                .thenReturn(14L)
                .thenReturn(15L);

        Map<String, Object> stats = queryService.getOrderStats();

        assertThat(stats).containsEntry("pendingPay", 11L)
                .containsEntry("pendingShip", 12L)
                .containsEntry("shipped", 13L)
                .containsEntry("refundRequested", 14L)
                .containsEntry("refundFailed", 15L);
    }

    @Test
    void dashboardStats_reportsTodayCountsSalesAndTrends() {
        when(orderMapper.countTodayPaidOrders(any())).thenReturn(3L);
        when(orderMapper.sumTodaySales(any())).thenReturn(new BigDecimal("100.00"));
        when(orderMapper.selectCount(any())).thenReturn(7L);
        when(orderMapper.countPaidOrdersBetween(any(), any())).thenReturn(2L);
        when(orderMapper.sumSalesBetween(any(), any())).thenReturn(new BigDecimal("50.00"));
        when(orderMapper.countShippedBetween(any(), any())).thenReturn(2L).thenReturn(4L);

        DashboardStatsVO stats = queryService.getDashboardStats();

        assertThat(stats.getTodayOrders()).isEqualTo(3L);
        assertThat(stats.getTodaySales()).isEqualByComparingTo("100.00");
        assertThat(stats.getPendingShip()).isEqualTo(7L);
        assertThat(stats.getTrend().getOrdersTrend()).isEqualTo(50L);
        assertThat(stats.getTrend().getSalesTrend()).isEqualTo(100L);
        assertThat(stats.getTrend().getShipTrend()).isEqualTo(-50L);
    }

    @Test
    void dashboardStats_treatsMissingAggregatesAsZero() {
        when(orderMapper.countTodayPaidOrders(any())).thenReturn(null);
        when(orderMapper.sumTodaySales(any())).thenReturn(null);
        when(orderMapper.selectCount(any())).thenReturn(null);
        when(orderMapper.countPaidOrdersBetween(any(), any())).thenReturn(null);
        when(orderMapper.sumSalesBetween(any(), any())).thenReturn(null);
        when(orderMapper.countShippedBetween(any(), any())).thenReturn(null);

        DashboardStatsVO stats = queryService.getDashboardStats();

        assertThat(stats.getTodayOrders()).isZero();
        assertThat(stats.getTodaySales()).isNull();
        assertThat(stats.getPendingShip()).isNull();
        assertThat(stats.getTrend().getOrdersTrend()).isZero();
        assertThat(stats.getTrend().getSalesTrend()).isZero();
        assertThat(stats.getTrend().getShipTrend()).isZero();
    }

    @Test
    void recentOrders_projectStatusTextAndAmount() {
        Order shipped = order(1L, StatusConstants.ORDER_SHIPPED);
        shipped.setCreateTime(LocalDateTime.of(2026, 2, 1, 10, 0));
        Order cancelled = order(2L, StatusConstants.ORDER_CANCELLED);
        cancelled.setCreateTime(LocalDateTime.of(2026, 2, 2, 10, 0));
        when(orderMapper.selectList(any())).thenReturn(List.of(cancelled, shipped));

        List<RecentOrderVO> result = queryService.getRecentOrders(5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatusText()).isEqualTo("已取消");
        assertThat(result.get(0).getOrderNo()).isEqualTo("SO2");
        assertThat(result.get(0).getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(result.get(1).getStatusText()).isEqualTo("已发货");
    }

    @Test
    void adminOrderDetail_statusAndPaymentTextsKeepExistingWording() {
        Order order = order(1L, StatusConstants.ORDER_PENDING_PAYMENT);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(addressMapper.selectById(1L)).thenReturn(null);
        when(logisticsMapper.selectById(1L)).thenReturn(null);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<Integer, String> statusTexts = Map.of(
                StatusConstants.ORDER_PENDING_PAYMENT, "待付款",
                StatusConstants.ORDER_PENDING_SHIPMENT, "待发货",
                StatusConstants.ORDER_SHIPPED, "已发货",
                StatusConstants.ORDER_COMPLETED, "已完成",
                StatusConstants.ORDER_CANCELLED, "已取消",
                StatusConstants.ORDER_REFUNDING, "退款中",
                StatusConstants.ORDER_REFUNDED, "已退款",
                StatusConstants.ORDER_REFUND_REQUESTED, "退款申请中",
                StatusConstants.ORDER_REFUND_FAILED, "退款失败");
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);
        for (Map.Entry<Integer, String> entry : statusTexts.entrySet()) {
            order.setStatus(entry.getKey());
            OrderVO vo = queryService.getAdminOrderById(1L);
            assertThat(vo.getStatusText()).isEqualTo(entry.getValue());
            assertThat(vo.getPaymentStatusText()).isEqualTo("未支付");
            assertThat(vo.getDeliveryTypeText()).isNull();
        }

        order.setStatus(StatusConstants.ORDER_PENDING_SHIPMENT);
        Map<Integer, String> paymentTexts = Map.of(
                PaymentRecord.STATUS_PENDING, "待支付",
                PaymentRecord.STATUS_PAID, "已支付",
                PaymentRecord.STATUS_CLOSED, "已关闭",
                PaymentRecord.STATUS_REFUNDED, "已退款",
                PaymentRecord.STATUS_REFUNDING, "退款中");
        for (Map.Entry<Integer, String> entry : paymentTexts.entrySet()) {
            when(paymentRecordMapper.selectOne(any())).thenReturn(payment(1L, entry.getKey()));
            assertThat(queryService.getAdminOrderById(1L).getPaymentStatusText()).isEqualTo(entry.getValue());
        }
    }

    private Order order(Long id, int status) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(2L);
        order.setOrderNo("SO" + id);
        order.setStatus(status);
        order.setAddressId(1L);
        order.setLogisticsId(1L);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(new BigDecimal("100.00"));
        return order;
    }

    private PaymentRecord payment(Long orderId, int status) {
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(orderId);
        record.setStatus(status);
        record.setAmount(new BigDecimal("100.00"));
        record.setOutTradeNo("OUT_" + orderId);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }
}
