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
    Page<OrderVO> getAdminOrderPage(Long userId, Integer status, String orderNo, String startDate, String endDate, Integer page, Integer limit);
    OrderVO getOrderById(Long userId, Long orderId);
    OrderVO getOrderByOrderNo(String orderNo);
    void cancelOrder(Long userId, Long orderId, String reason);
    void confirmReceive(Long userId, Long orderId);
    void deleteOrder(Long userId, Long orderId);
    Map<String, Object> getOrderStats();
    DashboardStatsVO getDashboardStats();
    List<RecentOrderVO> getRecentOrders(Integer limit);
    
    void shipOrder(Long orderId, Long logisticsId, String logisticsNo);
    void refundOrder(Long orderId, String reason);

    /**
     * 用户申请退款（写入 8=退款申请中, 通知管理员审核）
     * 仅允许待发货/已发货状态; 审核通过走 refundOrder, 拒绝走 rejectRefund
     */
    void applyRefund(Long userId, Long orderId, String reason);

    /**
     * 管理员拒绝退款申请: 8 → 回原状态(按 shipTime 判断回 2 或 3), 拒绝原因记入 adminRemark
     */
    void rejectRefund(Long orderId, String rejectReason);
    
    /**
     * 自动取消超时未支付订单（幂等）
     * 供 MQ 消费者和定时任务兜底共用
     * @param orderId 订单ID
     * @param cancelReason 取消原因
     * @return true-成功取消；false-订单不存在或已非待付款状态
     */
    boolean autoCancelOrder(Long orderId, String cancelReason);

    /**
     * 发货超期自动确认收货（幂等、CAS 安全）
     * 供定时任务调用，将已发货超期订单置为已完成
     * @param orderId 订单ID
     * @return true-成功收货；false-订单不存在/已非已发货状态/CAS 未命中
     */
    boolean autoConfirmReceive(Long orderId);

    OrderVO getAdminOrderById(Long orderId);
}
