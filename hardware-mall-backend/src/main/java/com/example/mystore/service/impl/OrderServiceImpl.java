package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.dto.CheckoutPreviewRequest;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.CheckoutPreviewVO;
import com.example.mystore.entity.vo.DashboardStatsVO;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.entity.vo.RecentOrderVO;
import com.example.mystore.service.OrderIntakeService;
import com.example.mystore.service.OrderLifecycleService;
import com.example.mystore.service.OrderQueryService;
import com.example.mystore.service.OrderService;
import com.example.mystore.service.PayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 订单门面：HTTP 适配器，委托订单接入、生命周期与查询三个协作者，不注入任何 Mapper。
 *
 * <p>唯一编排是打开用户订单详情时，待付款订单先触发支付模块的支付状态同步，再由查询模块组装视图。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderIntakeService orderIntakeService;
    private final OrderLifecycleService orderLifecycleService;
    private final OrderQueryService orderQueryService;

    @Autowired(required = false)
    private PayService payService;

    @Override
    public CheckoutPreviewVO previewOrder(CheckoutPreviewRequest request) {
        return orderIntakeService.previewOrder(request);
    }

    @Override
    public OrderVO createOrder(Long userId, CreateOrderRequest request, String idempotencyKey) {
        return orderIntakeService.createOrder(userId, request, idempotencyKey);
    }

    @Override
    public Page<OrderVO> getOrderPage(Long userId, Integer status, Integer page, Integer limit) {
        return orderQueryService.getOrderPage(userId, status, page, limit);
    }

    @Override
    public Page<OrderVO> getAdminOrderPage(Long userId, Integer status, String orderNo, String startDate, String endDate, Integer page, Integer limit) {
        return orderQueryService.getAdminOrderPage(userId, status, orderNo, startDate, endDate, page, limit);
    }

    @Override
    public OrderVO getOrderById(Long userId, Long orderId) {
        // 读请求先做归属校验与状态判断, 不写库
        Integer status = orderQueryService.getUserOrderStatus(userId, orderId);

        // 打开待付款订单详情时触发支付状态同步（去抖、修复与查单由支付模块负责）
        // 同步异常不影响详情返回
        if (status == StatusConstants.ORDER_PENDING_PAYMENT && payService != null) {
            try {
                payService.syncPaymentStatus(orderId);
            } catch (Exception e) {
                log.warn("⑦ lazy sync 异常, 不影响详情返回, orderId={}: {}", orderId, e.getMessage());
            }
        }

        return orderQueryService.getOrderView(orderId);
    }

    @Override
    public OrderVO getOrderByOrderNo(String orderNo) {
        return orderQueryService.getOrderByOrderNo(orderNo);
    }

    @Override
    public void cancelOrder(Long userId, Long orderId, String reason) {
        orderLifecycleService.cancelOrder(userId, orderId, reason);
    }

    @Override
    public void confirmReceive(Long userId, Long orderId) {
        orderLifecycleService.confirmReceive(userId, orderId);
    }

    @Override
    public void confirmReceiveByWechat(Long userId, Long orderId) {
        orderLifecycleService.confirmReceiveByWechat(userId, orderId);
    }

    @Override
    public void deleteOrder(Long userId, Long orderId) {
        orderLifecycleService.deleteOrder(userId, orderId);
    }

    @Override
    public Map<String, Object> getOrderStats() {
        return orderQueryService.getOrderStats();
    }

    @Override
    public DashboardStatsVO getDashboardStats() {
        return orderQueryService.getDashboardStats();
    }

    @Override
    public List<RecentOrderVO> getRecentOrders(Integer limit) {
        return orderQueryService.getRecentOrders(limit);
    }

    @Override
    public void shipOrder(Long orderId, Integer deliveryType, Long logisticsId) {
        orderLifecycleService.shipOrder(orderId, deliveryType, logisticsId);
    }

    @Override
    public void refundOrder(Long orderId, String reason) {
        orderLifecycleService.refundOrder(orderId, reason);
    }

    @Override
    public void applyRefund(Long userId, Long orderId, String reason) {
        orderLifecycleService.applyRefund(userId, orderId, reason);
    }

    @Override
    public void rejectRefund(Long orderId, String rejectReason) {
        orderLifecycleService.rejectRefund(orderId, rejectReason);
    }

    @Override
    public OrderVO getAdminOrderById(Long orderId) {
        return orderQueryService.getAdminOrderById(orderId);
    }

    @Override
    public boolean autoCancelOrder(Long orderId, String cancelReason) {
        return orderLifecycleService.autoCancelOrder(orderId, cancelReason);
    }

    @Override
    public boolean autoConfirmReceive(Long orderId) {
        return orderLifecycleService.autoConfirmReceive(orderId);
    }
}
