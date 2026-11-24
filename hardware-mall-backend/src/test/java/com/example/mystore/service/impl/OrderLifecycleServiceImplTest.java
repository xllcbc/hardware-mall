package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.WechatConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.event.StockCacheEvent;
import com.example.mystore.mapper.LogisticsMapper;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.PayService;
import com.example.mystore.service.RefundResult;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.WechatOrderShippingService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 订单生命周期模块: 通过命令接口断言状态迁移、前驱集合与失败语义。
 *
 * <p>迁移助手不单独开缝合点, 只经命令验证: 条件更新的 WHERE 与前驱集合必须同源。
 */
@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceImplTest {

    /** 未定义的发货方式: 发货方式规则只接受同城/自提 */
    private static final int UNSUPPORTED_DELIVERY_TYPE = 1;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private LogisticsMapper logisticsMapper;
    @Mock
    private SkuService skuService;
    @Mock
    private WechatOrderShippingService wechatOrderShippingService;
    @Mock
    private DingTalkAlertService dingTalkAlertService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private PayService payService;

    @InjectMocks
    private OrderLifecycleServiceImpl lifecycleService;

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper.set() 立即解析列名, 单测无 Spring 上下文需手动注册 Order 元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderItem.class);
    }

    @BeforeEach
    void setUp() {
        // payService 在实现类中是 @Autowired(required=false) 字段注入,
        // @InjectMocks 走构造器注入不会覆盖它, 需手动注入
        ReflectionTestUtils.setField(lifecycleService, "payService", payService);
    }

    // ==================== 确认收货 ====================

    @Test
    void confirmReceive_shippedOrder_movesToCompleted() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.confirmReceive(2L, 1L);

        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_SHIPPED, StatusConstants.ORDER_COMPLETED);
        assertThat(capturedSqlSet()).contains("receive_time=");
    }

    @Test
    void confirmReceive_notShipped_rejectsBeforeWrite() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));

        assertThatThrownBy(() -> lifecycleService.confirmReceive(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能确认收货已发货的订单");

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void confirmReceive_otherUsersOrder_throwsOrderNotFound() {
        Order order = orderWithStatus(1L, StatusConstants.ORDER_SHIPPED);
        order.setUserId(99L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> lifecycleService.confirmReceive(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void confirmReceive_missingOrder_throwsOrderNotFound() {
        when(orderMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> lifecycleService.confirmReceive(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void confirmReceive_casMiss_throwsStatusChanged() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> lifecycleService.confirmReceive(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变更，请刷新后重试");
    }

    // ==================== 自动确认收货 ====================

    @Test
    void autoConfirmReceive_shippedOrder_returnsTrueAndMovesToCompleted() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        assertThat(lifecycleService.autoConfirmReceive(1L)).isTrue();
        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_SHIPPED, StatusConstants.ORDER_COMPLETED);
        assertThat(capturedSqlSet()).contains("receive_time=");
    }

    @Test
    void autoConfirmReceive_missingOrder_returnsFalseWithoutWrite() {
        when(orderMapper.selectById(1L)).thenReturn(null);

        assertThat(lifecycleService.autoConfirmReceive(1L)).isFalse();
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void autoConfirmReceive_notShipped_returnsFalseWithoutWrite() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_COMPLETED));

        assertThat(lifecycleService.autoConfirmReceive(1L)).isFalse();
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void autoConfirmReceive_casMiss_returnsFalse() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        assertThat(lifecycleService.autoConfirmReceive(1L)).isFalse();
    }

    // ==================== 用户申请退款 ====================

    @Test
    void applyRefund_success_claimsStatus8AndNotifiesAdmin() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.applyRefund(2L, 1L, "不想要了");

        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_PENDING_SHIPMENT,
                        StatusConstants.ORDER_REFUND_REQUESTED,
                        "不想要了");
        assertThat(capturedSqlSet())
                .contains("cancel_reason=")
                .contains("cancel_time=");
        verify(dingTalkAlertService).notify(eq("REFUND_REQUEST:1"), contains("SO1"));
        verify(dingTalkAlertService).notify(eq("REFUND_REQUEST:1"), contains("不想要了"));
    }

    @Test
    void applyRefund_transitionWhereListsEveryLegalPredecessor() {
        // 预检放行的两个前驱都必须出现在同一条条件更新里（集合与 WHERE 同源）
        for (int status : new int[]{StatusConstants.ORDER_PENDING_SHIPMENT, StatusConstants.ORDER_SHIPPED}) {
            when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, status));
            when(orderMapper.update(isNull(), any())).thenReturn(1);

            lifecycleService.applyRefund(2L, 1L, "质量问题");
        }

        verify(orderMapper, times(2)).update(isNull(), any());
        assertThat(capturedUpdateValues(2))
                .contains(StatusConstants.ORDER_PENDING_SHIPMENT,
                        StatusConstants.ORDER_SHIPPED,
                        StatusConstants.ORDER_REFUND_REQUESTED);
    }

    @Test
    void applyRefund_casMiss_throwsAndNoNotify() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> lifecycleService.applyRefund(2L, 1L, "不想要了"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变更，请刷新后重试");

        verify(dingTalkAlertService, never()).notify(any(), any());
    }

    @Test
    void applyRefund_invalidStatus_rejectsBeforeCasClaim() {
        int[] invalidStatuses = {
                StatusConstants.ORDER_PENDING_PAYMENT,
                StatusConstants.ORDER_COMPLETED,
                StatusConstants.ORDER_CANCELLED,
                StatusConstants.ORDER_REFUNDING,
                StatusConstants.ORDER_REFUNDED,
                StatusConstants.ORDER_REFUND_REQUESTED
        };
        for (int status : invalidStatuses) {
            Order order = orderWithStatus(1L, status);
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> lifecycleService.applyRefund(2L, 1L, "不想要了"))
                    .as("status=%d 应被拒绝", status)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不支持申请退款");
        }
        verify(orderMapper, never()).update(isNull(), any());
        verify(dingTalkAlertService, never()).notify(any(), any());
    }

    @Test
    void applyRefund_notOwner_rejectsAndNoWrite() {
        Order order = orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT);
        order.setUserId(99L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> lifecycleService.applyRefund(2L, 1L, "不想要了"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");

        verify(orderMapper, never()).update(isNull(), any());
        verify(dingTalkAlertService, never()).notify(any(), any());
    }

    // ==================== 管理员拒绝退款申请 ====================

    @Test
    void rejectRefund_notShipped_backToPendingShipment() {
        Order order = orderWithStatus(1L, StatusConstants.ORDER_REFUND_REQUESTED);
        order.setShipTime(null);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.rejectRefund(1L, "凭证不足");

        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_REFUND_REQUESTED,
                        StatusConstants.ORDER_PENDING_SHIPMENT,
                        "凭证不足");
        assertThat(capturedSqlSet())
                .contains("admin_remark=")
                .contains("cancel_time=");
    }

    @Test
    void rejectRefund_shipped_backToShipped() {
        Order order = orderWithStatus(1L, StatusConstants.ORDER_REFUND_REQUESTED);
        order.setShipTime(LocalDateTime.now());
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.rejectRefund(1L, "影响二次销售");

        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_REFUND_REQUESTED,
                        StatusConstants.ORDER_SHIPPED,
                        "影响二次销售");
    }

    @Test
    void rejectRefund_nonRequestedStatus_rejectsAndNoWrite() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));

        assertThatThrownBy(() -> lifecycleService.rejectRefund(1L, "拒绝"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是待审核的退款申请");

        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void rejectRefund_casMiss_throwsStatusChanged() {
        Order order = orderWithStatus(1L, StatusConstants.ORDER_REFUND_REQUESTED);
        order.setShipTime(LocalDateTime.now());
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> lifecycleService.rejectRefund(1L, "拒绝"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变更，请刷新后重试");
    }

    // ==================== 用户删除 ====================

    @Test
    void deleteOrder_completedOrder_setsUserDeleteTime() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_COMPLETED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.deleteOrder(2L, 1L);

        // 只写删除时间, 不改订单状态; 不能仅凭 "有 Long 参数" 通过(orderId 本身也是 Long)
        assertThat(capturedSqlSet())
                .contains("user_delete_time=")
                .doesNotContain("status=");
        assertThat(capturedUpdateValues()).contains(StatusConstants.ORDER_COMPLETED);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void deleteOrder_cancelledOrder_allowed() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_CANCELLED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.deleteOrder(2L, 1L);

        assertThat(capturedUpdateValues()).contains(StatusConstants.ORDER_CANCELLED);
    }

    @Test
    void deleteOrder_pendingPayment_rejectedBeforeWrite() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_PAYMENT));

        assertThatThrownBy(() -> lifecycleService.deleteOrder(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该订单不可删除");

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void deleteOrder_notOwner_rejectsAndNoWrite() {
        Order order = orderWithStatus(1L, StatusConstants.ORDER_COMPLETED);
        order.setUserId(99L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> lifecycleService.deleteOrder(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void deleteOrder_casMiss_throwsStatusChanged() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_COMPLETED));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> lifecycleService.deleteOrder(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变更，请刷新后重试");
    }

    // ==================== 发货 ====================

    @Test
    void shipOrder_localDelivery_generatesDeliveryNoMovesToShippedAndUploads() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));
        when(logisticsMapper.selectById(2L)).thenReturn(enabledLogistics(2L));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.shipOrder(1L, StatusConstants.DELIVERY_TYPE_LOCAL, 2L);

        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_SHIPPED,
                        StatusConstants.DELIVERY_TYPE_LOCAL,
                        2L)
                .anyMatch(v -> v instanceof String s && s.startsWith("PS"));
        verify(wechatOrderShippingService).uploadShippingInfo(1L);
    }

    @Test
    void shipOrder_pickup_hasNoLogisticsAndNoDeliveryNo() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.shipOrder(1L, StatusConstants.DELIVERY_TYPE_PICKUP, null);

        verifyNoInteractions(logisticsMapper);
        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_SHIPPED, StatusConstants.DELIVERY_TYPE_PICKUP);
        assertThat(capturedSqlSet())
                .contains("logistics_id=")
                .contains("logistics_no=");
        verify(wechatOrderShippingService).uploadShippingInfo(1L);
    }

    @Test
    void shipOrder_invalidDeliveryType_rejectsBeforeWrite() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));

        assertThatThrownBy(() -> lifecycleService.shipOrder(1L, UNSUPPORTED_DELIVERY_TYPE, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不支持的发货方式");

        verify(orderMapper, never()).update(isNull(), any());
        verifyNoInteractions(wechatOrderShippingService);
    }

    @Test
    void shipOrder_nonPendingShipmentStatus_rejectsBeforeWrite() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));

        assertThatThrownBy(() -> lifecycleService.shipOrder(1L, StatusConstants.DELIVERY_TYPE_PICKUP, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能发货待发货的订单");

        verify(orderMapper, never()).update(isNull(), any());
        verifyNoInteractions(logisticsMapper, wechatOrderShippingService);
    }

    @Test
    void shipOrder_missingOrder_throwsOrderNotFound() {
        when(orderMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> lifecycleService.shipOrder(1L, StatusConstants.DELIVERY_TYPE_PICKUP, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");

        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void shipOrder_logisticsNotFound_rejectsBeforeWrite() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));
        when(logisticsMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> lifecycleService.shipOrder(1L, StatusConstants.DELIVERY_TYPE_LOCAL, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("物流公司不存在或已停用");

        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void shipOrder_logisticsDisabled_rejectsBeforeWrite() {
        Logistics disabled = new Logistics();
        disabled.setId(3L);
        disabled.setStatus(StatusConstants.LOGISTICS_STATUS_DISABLED);
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));
        when(logisticsMapper.selectById(3L)).thenReturn(disabled);

        assertThatThrownBy(() -> lifecycleService.shipOrder(1L, StatusConstants.DELIVERY_TYPE_LOCAL, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("物流公司不存在或已停用");

        verify(orderMapper, never()).update(isNull(), any());
    }

    @Test
    void shipOrder_casMiss_throwsStatusChangedAndDoesNotUpload() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));
        when(logisticsMapper.selectById(2L)).thenReturn(enabledLogistics(2L));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> lifecycleService.shipOrder(1L, StatusConstants.DELIVERY_TYPE_LOCAL, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变更，请刷新后重试");

        verifyNoInteractions(wechatOrderShippingService);
    }

    @Test
    void shipOrder_wechatUploadFailure_isSwallowedAfterSuccessfulShipment() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        doThrow(new RuntimeException("wechat down"))
                .when(wechatOrderShippingService).uploadShippingInfo(1L);

        assertThatCode(() -> lifecycleService.shipOrder(1L, StatusConstants.DELIVERY_TYPE_PICKUP, null))
                .doesNotThrowAnyException();

        verify(wechatOrderShippingService).uploadShippingInfo(1L);
    }

    // ==================== 微信确认收货 ====================

    @Test
    void confirmReceiveByWechat_confirmed_recordsStateThenCompletesOrder() {
        assertWechatConfirmAdvancesLocalOrder(WechatConstants.OrderState.CONFIRMED);
    }

    @Test
    void confirmReceiveByWechat_completed_recordsStateThenCompletesOrder() {
        assertWechatConfirmAdvancesLocalOrder(WechatConstants.OrderState.COMPLETED);
    }

    @Test
    void confirmReceiveByWechat_shippedNotConfirmed_rejectsWithoutLocalTransition() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(wechatOrderShippingService.queryOrderState(1L)).thenReturn(WechatConstants.OrderState.SHIPPED);

        assertThatThrownBy(() -> lifecycleService.confirmReceiveByWechat(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("微信未确认收货，请稍后重试");

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void confirmReceiveByWechat_wechatQueryFailed_rejectsWithoutLocalTransition() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(wechatOrderShippingService.queryOrderState(1L)).thenReturn(null);

        assertThatThrownBy(() -> lifecycleService.confirmReceiveByWechat(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("微信未确认收货，请稍后重试");

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void confirmReceiveByWechat_alreadyCompleted_isIdempotentWithoutWechatQuery() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_COMPLETED));

        lifecycleService.confirmReceiveByWechat(2L, 1L);

        verifyNoInteractions(wechatOrderShippingService);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void confirmReceiveByWechat_wrongLocalStatus_rejectsWithoutWechatQuery() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_SHIPMENT));

        assertThatThrownBy(() -> lifecycleService.confirmReceiveByWechat(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前订单状态不支持确认收货");

        verifyNoInteractions(wechatOrderShippingService);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void confirmReceiveByWechat_notOwner_rejectsWithoutWechatQuery() {
        Order order = orderWithStatus(1L, StatusConstants.ORDER_SHIPPED);
        order.setUserId(99L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> lifecycleService.confirmReceiveByWechat(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");

        verifyNoInteractions(wechatOrderShippingService);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void confirmReceiveByWechat_localTransitionCasMiss_throwsStatusChanged() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(wechatOrderShippingService.queryOrderState(1L)).thenReturn(WechatConstants.OrderState.CONFIRMED);
        // 第一次写 wechat_order_state 命中, 第二次本地 3→4 未命中
        when(orderMapper.update(isNull(), any())).thenReturn(1, 0);

        assertThatThrownBy(() -> lifecycleService.confirmReceiveByWechat(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变更，请刷新后重试");
    }

    private void assertWechatConfirmAdvancesLocalOrder(int wechatState) {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(wechatOrderShippingService.queryOrderState(1L)).thenReturn(wechatState);
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.confirmReceiveByWechat(2L, 1L);

        assertThat(capturedUpdateValues(2))
                .contains(wechatState,
                        StatusConstants.ORDER_SHIPPED,
                        StatusConstants.ORDER_COMPLETED);
        // 先记录微信状态, 再执行本地 3→4
        java.util.List<String> sqlSets = capturedSqlSets(2);
        assertThat(sqlSets.get(0)).contains("wechat_order_state=");
        assertThat(sqlSets.get(1)).contains("receive_time=");
    }

    // ==================== 退款入口 ====================

    @Test
    void refundOrder_claimFirst_casBeforeRefund() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        when(payService.refund(1L, "质量问题")).thenReturn(RefundResult.ACCEPTED);

        lifecycleService.refundOrder(1L, "质量问题");

        InOrder inOrder = inOrder(orderMapper, payService);
        inOrder.verify(orderMapper).update(isNull(), any());   // 先 CAS 占位
        inOrder.verify(payService).refund(1L, "质量问题");      // 后调微信退款
        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_PENDING_SHIPMENT,
                        StatusConstants.ORDER_SHIPPED,
                        StatusConstants.ORDER_REFUNDING,
                        "质量问题");
        assertThat(capturedSqlSet())
                .contains("cancel_reason=")
                .contains("cancel_time=");
    }

    @Test
    void refundOrder_casMiss_throwsAndNoRefund() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(orderMapper.update(isNull(), any())).thenReturn(0); // 竞态输了

        assertThatThrownBy(() -> lifecycleService.refundOrder(1L, "质量问题"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变更，请刷新后重试");

        verify(payService, never()).refund(any(), any()); // 未占位成功绝不退款
    }

    @Test
    void refundOrder_doesNotRestoreStockOrPublishEvent() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        when(payService.refund(1L, "质量问题")).thenReturn(RefundResult.ACCEPTED);

        lifecycleService.refundOrder(1L, "质量问题");

        // 库存恢复已迁到退款回调(6→7 确认成功后), 退款入口不再还库存不发事件
        verifyNoInteractions(skuService, applicationEventPublisher);
    }

    @Test
    void refundOrder_refundThrows_propagatesButClaimed() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        doThrow(new RuntimeException("wechat error")).when(payService).refund(any(), any());

        assertThatThrownBy(() -> lifecycleService.refundOrder(1L, "质量问题"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("wechat error");

        verify(orderMapper).update(isNull(), any()); // claim 已提交, status 保持 6
    }

    @Test
    void refundOrder_requestedStatus8_approved_casClaimIncludes8() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_REFUND_REQUESTED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        when(payService.refund(1L, "同意退款")).thenReturn(RefundResult.ACCEPTED);

        lifecycleService.refundOrder(1L, "同意退款");

        // 预检放行状态 8; 占位为退款中后走既有微信退款链路
        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_REFUND_REQUESTED, StatusConstants.ORDER_REFUNDING);
        verify(payService).refund(1L, "同意退款");
    }

    @Test
    void refundOrder_cancelledAndPaid_retriesRefundWithoutChangingOrderStatus() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_CANCELLED));
        when(payService.queryByOrderId(1L)).thenReturn(paymentRecord(1L, PaymentRecord.STATUS_PAID));
        when(payService.refund(1L, "管理员重试退款")).thenReturn(RefundResult.ACCEPTED);

        lifecycleService.refundOrder(1L, "管理员重试退款");

        // 已取消订单只校验最新支付记录, 不推进订单状态
        verify(orderMapper, never()).update(any(), any());
        verify(payService).refund(1L, "管理员重试退款");
    }

    @Test
    void refundOrder_cancelledButUnpaid_rejectsWithoutRefund() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_CANCELLED));
        when(payService.queryByOrderId(1L)).thenReturn(paymentRecord(1L, PaymentRecord.STATUS_PENDING));

        assertThatThrownBy(() -> lifecycleService.refundOrder(1L, "管理员重试退款"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是已支付");

        verify(payService, never()).refund(any(), any());
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void refundOrder_cancelledWithoutPaymentRecord_rejectsWithoutRefund() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_CANCELLED));
        when(payService.queryByOrderId(1L)).thenReturn(null);

        assertThatThrownBy(() -> lifecycleService.refundOrder(1L, "管理员重试退款"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是已支付");

        verify(payService, never()).refund(any(), any());
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void refundOrder_cancelledPaymentDefiniteFailure_returnsClearErrorAndKeepsOrderStatus() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_CANCELLED));
        when(payService.queryByOrderId(1L)).thenReturn(paymentRecord(1L, PaymentRecord.STATUS_PAID));
        when(payService.refund(1L, "管理员重试退款")).thenReturn(RefundResult.DEFINITE_FAILED);

        assertThatThrownBy(() -> lifecycleService.refundOrder(1L, "管理员重试退款"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退款确定失败");

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void refundOrder_cancelledPaymentResultUnknown_returnsNormally() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_CANCELLED));
        when(payService.queryByOrderId(1L)).thenReturn(paymentRecord(1L, PaymentRecord.STATUS_PAID));
        when(payService.refund(1L, "管理员重试退款")).thenReturn(RefundResult.UNKNOWN);

        lifecycleService.refundOrder(1L, "管理员重试退款");

        verify(payService).refund(1L, "管理员重试退款");
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void refundOrder_activePaymentDefiniteFailure_returnsClearError() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        when(payService.refund(1L, "质量问题")).thenReturn(RefundResult.DEFINITE_FAILED);

        assertThatThrownBy(() -> lifecycleService.refundOrder(1L, "质量问题"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("退款确定失败：微信明确拒绝，支付记录已回退，请稍后重试");
    }

    @Test
    void refundOrder_activePaymentResultUnknown_returnsNormally() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        when(payService.refund(1L, "质量问题")).thenReturn(RefundResult.UNKNOWN);

        lifecycleService.refundOrder(1L, "质量问题");

        verify(payService).refund(1L, "质量问题");
    }

    @Test
    void refundOrder_legacyStatusesIncludingFailure9_keepCasRefundChain() {
        // 状态 9(退款失败) 是历史状态: 管理员重试沿用同一 CAS 占位链路
        for (int status : new int[]{
                StatusConstants.ORDER_PENDING_SHIPMENT,
                StatusConstants.ORDER_SHIPPED,
                StatusConstants.ORDER_REFUND_REQUESTED,
                StatusConstants.ORDER_REFUND_FAILED}) {
            when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, status));
            when(orderMapper.update(isNull(), any())).thenReturn(1);
            when(payService.refund(1L, "质量问题")).thenReturn(RefundResult.ACCEPTED);

            lifecycleService.refundOrder(1L, "质量问题");
        }

        verify(orderMapper, times(4)).update(isNull(), any());
        verify(payService, times(4)).refund(1L, "质量问题");
        assertThat(capturedUpdateValues(4))
                .contains(StatusConstants.ORDER_PENDING_SHIPMENT,
                        StatusConstants.ORDER_SHIPPED,
                        StatusConstants.ORDER_REFUND_REQUESTED,
                        StatusConstants.ORDER_REFUND_FAILED,
                        StatusConstants.ORDER_REFUNDING);
    }

    @Test
    void refundOrder_invalidStatus_rejectsBeforeCasClaim() {
        int[] invalidStatuses = {
                StatusConstants.ORDER_PENDING_PAYMENT,
                StatusConstants.ORDER_COMPLETED,
                StatusConstants.ORDER_REFUNDED
        };
        for (int status : invalidStatuses) {
            when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, status));

            assertThatThrownBy(() -> lifecycleService.refundOrder(1L, "质量问题"))
                    .as("status=%d 应被拒绝", status)
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("该订单状态不支持退款");
        }

        verify(orderMapper, never()).update(any(), any());
        verifyNoInteractions(payService);
    }

    @Test
    void refundOrder_missingOrder_throwsOrderNotFound() {
        when(orderMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> lifecycleService.refundOrder(1L, "质量问题"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");

        verify(orderMapper, never()).update(any(), any());
        verifyNoInteractions(payService);
    }

    @Test
    void refundOrder_payServiceMissing_throwsBeforeCasClaim() {
        // payService 缺失时必须明确报错, 不得静默跳过; 且守卫在 CAS claim 之前, 订单不会卡"退款中"
        ReflectionTestUtils.setField(lifecycleService, "payService", null);

        assertThatThrownBy(() -> lifecycleService.refundOrder(1L, "质量问题"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("支付服务未启用，无法退款");

        verifyNoInteractions(orderMapper); // 连 selectById 都未发生, 任何 DB 写都不允许
    }

    // ==================== 用户取消 ====================

    @Test
    void cancelOrder_pendingPayment_restoresStockThenClaimsCancelledAndPublishesEvent() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_PAYMENT));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem(5L, 2), orderItem(7L, 1)));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        lifecycleService.cancelOrder(2L, 1L, "不想要了");

        // 先还库存, 再做条件更新
        InOrder inOrder = inOrder(skuService, orderMapper);
        inOrder.verify(skuService).restoreStock(5L, 2);
        inOrder.verify(skuService).restoreStock(7L, 1);
        inOrder.verify(orderMapper).update(isNull(), any());
        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_PENDING_PAYMENT,
                        StatusConstants.ORDER_CANCELLED,
                        "不想要了");
        assertThat(capturedSqlSet())
                .contains("cancel_reason=")
                .contains("cancel_time=");
        // 事件在事务内发布, 监听器 AFTER_COMMIT 执行; 载荷是已还库存的 SKU
        assertThat(publishedStockSkuIds()).containsExactly(5L, 7L);
    }

    @Test
    void cancelOrder_notPendingPayment_rejectsWithoutStockWriteOrEvent() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_SHIPPED));

        assertThatThrownBy(() -> lifecycleService.cancelOrder(2L, 1L, "不想要了"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能取消待付款的订单");

        verifyNoInteractions(skuService, applicationEventPublisher);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void cancelOrder_notOwner_rejectsWithoutSideEffects() {
        Order order = orderWithStatus(1L, StatusConstants.ORDER_PENDING_PAYMENT);
        order.setUserId(99L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> lifecycleService.cancelOrder(2L, 1L, "不想要了"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");

        verifyNoInteractions(skuService, applicationEventPublisher);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void cancelOrder_missingOrder_throwsOrderNotFound() {
        when(orderMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> lifecycleService.cancelOrder(2L, 1L, "不想要了"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单不存在");

        verifyNoInteractions(skuService, applicationEventPublisher);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void cancelOrder_casMiss_throwsToRollbackRestoredStock() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_PAYMENT));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem(5L, 2)));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        // 抛异常是回滚已还库存的唯一手段, 不能静默吞掉
        assertThatThrownBy(() -> lifecycleService.cancelOrder(2L, 1L, "不想要了"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变更，请刷新后重试");

        verify(skuService).restoreStock(5L, 2);
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    // ==================== 定时任务自动取消 ====================

    @Test
    void autoCancelOrder_pendingPayment_restoresStockThenClaimsCancelledAndPublishesEvent() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_PAYMENT));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem(5L, 2)));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        assertThat(lifecycleService.autoCancelOrder(1L, "超时未支付，系统自动取消")).isTrue();

        InOrder inOrder = inOrder(skuService, orderMapper);
        inOrder.verify(skuService).restoreStock(5L, 2);
        inOrder.verify(orderMapper).update(isNull(), any());
        assertThat(capturedUpdateValues())
                .contains(StatusConstants.ORDER_PENDING_PAYMENT,
                        StatusConstants.ORDER_CANCELLED,
                        "超时未支付，系统自动取消");
        assertThat(publishedStockSkuIds()).containsExactly(5L);
    }

    @Test
    void autoCancelOrder_missingOrder_returnsFalseWithoutSideEffects() {
        when(orderMapper.selectById(1L)).thenReturn(null);

        assertThat(lifecycleService.autoCancelOrder(1L, "超时未支付，系统自动取消")).isFalse();

        verifyNoInteractions(skuService, applicationEventPublisher);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void autoCancelOrder_notPendingPayment_returnsFalseWithoutSideEffects() {
        int[] alreadyHandledStatuses = {
                StatusConstants.ORDER_CANCELLED,
                StatusConstants.ORDER_COMPLETED,
                StatusConstants.ORDER_PENDING_SHIPMENT
        };
        for (int status : alreadyHandledStatuses) {
            when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, status));

            assertThat(lifecycleService.autoCancelOrder(1L, "超时未支付，系统自动取消"))
                    .as("status=%d 应返回 false 且无副作用", status)
                    .isFalse();
        }

        verifyNoInteractions(skuService, applicationEventPublisher);
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void autoCancelOrder_casMiss_throwsToRollbackRestoredStock() {
        when(orderMapper.selectById(1L)).thenReturn(orderWithStatus(1L, StatusConstants.ORDER_PENDING_PAYMENT));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem(5L, 2)));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        // 定时任务的 CAS 未命中仍需回滚已还库存, 因此抛异常而非返回 false
        assertThatThrownBy(() -> lifecycleService.autoCancelOrder(1L, "超时未支付，系统自动取消"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变更，跳过取消");

        verify(skuService).restoreStock(5L, 2);
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    /**
     * 取消与自动取消必须自己持有事务: 先还库存再条件更新, CAS 未命中抛异常才能回滚已还库存。
     * 事务注解无法从命令返回值观察, 且监听器的 AFTER_COMMIT 依赖它, 故在此钉住这条契约。
     */
    @Test
    void cancelCommands_areTransactionalSoCasMissRollsBackRestoredStock() throws Exception {
        assertThat(isTransactional("cancelOrder", Long.class, Long.class, String.class)).isTrue();
        assertThat(isTransactional("autoCancelOrder", Long.class, String.class)).isTrue();
    }

    private Logistics enabledLogistics(Long id) {
        Logistics logistics = new Logistics();
        logistics.setId(id);
        logistics.setStatus(StatusConstants.LOGISTICS_STATUS_ENABLED);
        return logistics;
    }

    private OrderItem orderItem(Long skuId, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(1L);
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    /** 取消类命令必须自己持有事务: 还库存先提交, CAS 未命中时抛异常才能回滚已还库存 */
    private static boolean isTransactional(String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = OrderLifecycleServiceImpl.class.getMethod(name, parameterTypes);
        return method.isAnnotationPresent(Transactional.class);
    }

    private java.util.List<Long> publishedStockSkuIds() {
        ArgumentCaptor<StockCacheEvent> captor = ArgumentCaptor.forClass(StockCacheEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        return captor.getValue().skuIds();
    }

    private Order orderWithStatus(Long id, int status) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(2L);
        order.setOrderNo("SO" + id);
        order.setStatus(status);
        order.setAddressId(1L);
        order.setLogisticsId(1L);
        return order;
    }

    private PaymentRecord paymentRecord(Long orderId, int status) {
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(orderId);
        record.setStatus(status);
        record.setAmount(new java.math.BigDecimal("100.00"));
        record.setOutTradeNo("OUT_" + orderId);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    private java.util.List<Object> capturedUpdateValues() {
        return capturedUpdateValues(1);
    }

    /**
     * @param expectedInvocations 期望的条件更新次数: 命令必须是单语句, 多出一次也应失败
     */
    private java.util.List<Object> capturedUpdateValues(int expectedInvocations) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Order>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(orderMapper, times(expectedInvocations)).update(isNull(), captor.capture());
        // WHERE 的 IN 与 SET 参数在生成 SQL 片段时才写入参数表, 单测无 MyBatis 执行需手动触发
        captor.getAllValues().forEach(LambdaUpdateWrapper::getSqlSegment);
        return captor.getAllValues().stream()
                .flatMap(wrapper -> wrapper.getParamNameValuePairs().values().stream())
                .toList();
    }

    /** 条件更新的 SET 列: 断言附加列确实被写入, 而不只是 WHERE 命中 */
    private String capturedSqlSet() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Order>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(orderMapper, times(1)).update(isNull(), captor.capture());
        return captor.getValue().getSqlSet();
    }

    private java.util.List<String> capturedSqlSets(int expectedInvocations) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Order>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(orderMapper, times(expectedInvocations)).update(isNull(), captor.capture());
        return captor.getAllValues().stream().map(LambdaUpdateWrapper::getSqlSet).toList();
    }
}
