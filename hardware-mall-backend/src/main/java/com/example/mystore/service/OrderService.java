package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.entity.vo.DashboardStatsVO;
import com.example.mystore.entity.vo.RecentOrderVO;
import com.example.mystore.entity.dto.CreateOrderRequest;

import java.util.List;
import java.util.Map;

public interface OrderService {
    OrderVO createOrder(Long userId, CreateOrderRequest request, String idempotencyKey);
    Page<OrderVO> getOrderPage(Long userId, Integer status, Integer page, Integer limit);
    Page<OrderVO> getAdminOrderPage(Long userId, Integer status, Integer page, Integer limit);
    OrderVO getOrderById(Long userId, Long orderId);
    OrderVO getOrderByOrderNo(String orderNo);
    void cancelOrder(Long userId, Long orderId, String reason);
    void confirmReceive(Long userId, Long orderId);
    void deleteOrder(Long userId, Long orderId);
    Map<String, Object> getOrderStats();
    DashboardStatsVO getDashboardStats();
    List<RecentOrderVO> getRecentOrders(Integer limit);
    
    void shipOrder(Long orderId, String logisticsNo);
    void refundOrder(Long orderId, String reason);
    
    /**
     * 自动取消超时未支付订单（幂等）
     * 供 MQ 消费者和定时任务兜底共用
     * @param orderId 订单ID
     * @param cancelReason 取消原因
     * @return true-成功取消；false-订单不存在或已非待付款状态
     */
    boolean autoCancelOrder(Long orderId, String cancelReason);

    OrderVO getAdminOrderById(Long orderId);
}
