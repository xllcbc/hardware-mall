package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.entity.vo.DashboardStatsVO;
import com.example.mystore.entity.vo.RecentOrderVO;

import java.util.List;
import java.util.Map;

/**
 * 订单查询（读模型）：用户/管理分页、详情、统计与最近订单。
 *
 * <p>只读：不写库、不依赖支付网关、SKU 与等级定价。详情展示按订单读取最新支付记录（管理分页批量读取）。
 */
public interface OrderQueryService {

    /**
     * 用户端订单分页：按用户与状态筛选，组装不含等级定价快照的订单视图。
     */
    Page<OrderVO> getOrderPage(Long userId, Integer status, Integer page, Integer limit);

    /**
     * 组装用户端订单详情视图。
     *
     * <p>归属校验由详情路径先经 {@link #getUserOrderStatus(Long, Long)} 完成；
     * 支付同步由门面在调用本方法前触发。本方法只读、不再重复校验归属。
     */
    OrderVO getOrderView(Long orderId);

    /**
     * 读取用户订单状态：供门面判断详情是否需要触发支付同步。
     *
     * <p>只读不写库；订单不存在或不属于该用户时抛「订单不存在」。
     */
    Integer getUserOrderStatus(Long userId, Long orderId);

    /**
     * 管理端订单分页：按用户、状态、订单号与创建日期筛选，批量加载最新支付记录。
     */
    Page<OrderVO> getAdminOrderPage(Long userId, Integer status, String orderNo, String startDate, String endDate,
                                    Integer page, Integer limit);

    /**
     * 按订单号查询并组装用户端订单视图。
     */
    OrderVO getOrderByOrderNo(String orderNo);

    /**
     * 订单状态计数（管理端红点/快捷筛选）。
     */
    Map<String, Object> getOrderStats();

    /**
     * 仪表盘统计（今日订单/销售额/待发货与较昨日趋势）。
     */
    DashboardStatsVO getDashboardStats();

    /**
     * 管理端最近订单。
     */
    List<RecentOrderVO> getRecentOrders(Integer limit);

    /**
     * 管理端订单详情。
     */
    OrderVO getAdminOrderById(Long orderId);
}
