package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.dto.CheckoutPreviewRequest;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.CheckoutPreviewVO;
import com.example.mystore.entity.vo.DashboardStatsVO;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.entity.vo.RecentOrderVO;
import com.example.mystore.service.OrderIntakeService;
import com.example.mystore.service.OrderLifecycleService;
import com.example.mystore.service.OrderQueryService;
import com.example.mystore.service.PayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 订单门面: 只验证委托目标、详情支付同步触发顺序, 以及门面不注入任何 Mapper。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderIntakeService orderIntakeService;
    @Mock
    private OrderLifecycleService orderLifecycleService;
    @Mock
    private OrderQueryService orderQueryService;
    @Mock
    private PayService payService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        // payService 在实现类中是 @Autowired(required=false) 字段注入,
        // @InjectMocks 走构造器注入不会覆盖它, 需手动注入
        ReflectionTestUtils.setField(orderService, "payService", payService);
    }

    // ==================== 门面纯委托 ====================

    @Test
    void facade_hasNoMapperDependency() {
        assertThat(Arrays.stream(OrderServiceImpl.class.getDeclaredFields()))
                .noneMatch(field -> BaseMapper.class.isAssignableFrom(field.getType()))
                .noneMatch(field -> field.getType().getSimpleName().endsWith("Mapper"));
    }

    @Test
    void orderEntry_delegatesToIntakeModule() {
        CheckoutPreviewRequest previewRequest = new CheckoutPreviewRequest();
        CheckoutPreviewVO preview = new CheckoutPreviewVO();
        when(orderIntakeService.previewOrder(previewRequest)).thenReturn(preview);

        CreateOrderRequest createRequest = new CreateOrderRequest();
        OrderVO created = new OrderVO();
        when(orderIntakeService.createOrder(2L, createRequest, "k1")).thenReturn(created);

        assertThat(orderService.previewOrder(previewRequest)).isSameAs(preview);
        assertThat(orderService.createOrder(2L, createRequest, "k1")).isSameAs(created);

        verify(orderIntakeService).previewOrder(previewRequest);
        verify(orderIntakeService).createOrder(2L, createRequest, "k1");
        verifyNoInteractions(orderQueryService, orderLifecycleService);
    }

    // ==================== 读模型门面接线 ====================

    @Test
    void readOnlyQueries_delegateToQueryModule() {
        Page<OrderVO> page = new Page<>(1, 10);
        Map<String, Object> stats = Map.of("pendingPay", 1L);
        DashboardStatsVO dashboard = new DashboardStatsVO();
        List<RecentOrderVO> recent = List.of(new RecentOrderVO());
        when(orderQueryService.getOrderPage(2L, 1, 1, 10)).thenReturn(page);
        when(orderQueryService.getAdminOrderPage(3L, 2, "SO1", "2026-01-01", "2026-01-31", 1, 10)).thenReturn(page);
        when(orderQueryService.getOrderByOrderNo("SO1")).thenReturn(new OrderVO());
        when(orderQueryService.getAdminOrderById(1L)).thenReturn(new OrderVO());
        when(orderQueryService.getOrderStats()).thenReturn(stats);
        when(orderQueryService.getDashboardStats()).thenReturn(dashboard);
        when(orderQueryService.getRecentOrders(5)).thenReturn(recent);

        assertThat(orderService.getOrderPage(2L, 1, 1, 10)).isSameAs(page);
        assertThat(orderService.getAdminOrderPage(3L, 2, "SO1", "2026-01-01", "2026-01-31", 1, 10)).isSameAs(page);
        assertThat(orderService.getOrderByOrderNo("SO1")).isNotNull();
        assertThat(orderService.getAdminOrderById(1L)).isNotNull();
        assertThat(orderService.getOrderStats()).isSameAs(stats);
        assertThat(orderService.getDashboardStats()).isSameAs(dashboard);
        assertThat(orderService.getRecentOrders(5)).isSameAs(recent);
        verifyNoInteractions(orderIntakeService, orderLifecycleService);
    }

    @Test
    void orderDetail_pendingPayment_triggersPaymentSyncThenAssemblesView() {
        when(orderQueryService.getUserOrderStatus(2L, 1L))
                .thenReturn(StatusConstants.ORDER_PENDING_PAYMENT);
        OrderVO view = new OrderVO();
        when(orderQueryService.getOrderView(1L)).thenReturn(view);

        assertThat(orderService.getOrderById(2L, 1L)).isSameAs(view);

        InOrder inOrder = inOrder(payService, orderQueryService);
        inOrder.verify(orderQueryService).getUserOrderStatus(2L, 1L);
        inOrder.verify(payService).syncPaymentStatus(1L);
        inOrder.verify(orderQueryService).getOrderView(1L);
        // 内联修复与微信查单已迁入支付模块: 订单门面详情只读
        verify(payService, never()).queryWechatOrder(any());
    }

    @Test
    void orderDetail_syncFailure_stillReturnsView() {
        when(orderQueryService.getUserOrderStatus(2L, 1L))
                .thenReturn(StatusConstants.ORDER_PENDING_PAYMENT);
        doThrow(new RuntimeException("wechat down")).when(payService).syncPaymentStatus(1L);
        OrderVO view = new OrderVO();
        when(orderQueryService.getOrderView(1L)).thenReturn(view);

        assertThat(orderService.getOrderById(2L, 1L)).isSameAs(view);

        verify(orderQueryService).getOrderView(1L);
    }

    @Test
    void orderDetail_otherUsersOrder_throwsWithoutSyncOrView() {
        when(orderQueryService.getUserOrderStatus(2L, 1L))
                .thenThrow(new BusinessException("订单不存在"));

        assertThatThrownBy(() -> orderService.getOrderById(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");

        verifyNoInteractions(payService);
        verify(orderQueryService, never()).getOrderView(anyLong());
    }

    @Test
    void orderDetail_notPendingPayment_skipsSyncButAssemblesView() {
        when(orderQueryService.getUserOrderStatus(2L, 1L))
                .thenReturn(StatusConstants.ORDER_SHIPPED);
        OrderVO view = new OrderVO();
        when(orderQueryService.getOrderView(1L)).thenReturn(view);

        assertThat(orderService.getOrderById(2L, 1L)).isSameAs(view);

        verifyNoInteractions(payService);
        verify(orderQueryService).getOrderView(1L);
    }

    // ==================== 生命周期门面接线 ====================

    @Test
    void lifecycleCommands_delegateToLifecycleModule() {
        when(orderLifecycleService.autoCancelOrder(1L, "超时未支付")).thenReturn(true);
        when(orderLifecycleService.autoConfirmReceive(1L)).thenReturn(true);

        orderService.cancelOrder(2L, 1L, "不想要了");
        assertThat(orderService.autoCancelOrder(1L, "超时未支付")).isTrue();
        orderService.shipOrder(1L, StatusConstants.DELIVERY_TYPE_LOCAL, 2L);
        orderService.confirmReceive(2L, 1L);
        assertThat(orderService.autoConfirmReceive(1L)).isTrue();
        orderService.confirmReceiveByWechat(2L, 1L);
        orderService.refundOrder(1L, "质量问题");
        orderService.applyRefund(2L, 1L, "不想要了");
        orderService.rejectRefund(1L, "凭证不足");
        orderService.deleteOrder(2L, 1L);

        verify(orderLifecycleService).cancelOrder(2L, 1L, "不想要了");
        verify(orderLifecycleService).autoCancelOrder(1L, "超时未支付");
        verify(orderLifecycleService).shipOrder(1L, StatusConstants.DELIVERY_TYPE_LOCAL, 2L);
        verify(orderLifecycleService).confirmReceive(2L, 1L);
        verify(orderLifecycleService).autoConfirmReceive(1L);
        verify(orderLifecycleService).confirmReceiveByWechat(2L, 1L);
        verify(orderLifecycleService).refundOrder(1L, "质量问题");
        verify(orderLifecycleService).applyRefund(2L, 1L, "不想要了");
        verify(orderLifecycleService).rejectRefund(1L, "凭证不足");
        verify(orderLifecycleService).deleteOrder(2L, 1L);
        verifyNoInteractions(orderIntakeService, orderQueryService);
    }
}
