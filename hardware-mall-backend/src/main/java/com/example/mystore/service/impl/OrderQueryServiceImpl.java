package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Address;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.PaymentRecord;
import com.example.mystore.entity.vo.OrderItemVO;
import com.example.mystore.entity.vo.OrderVO;
import com.example.mystore.entity.vo.DashboardStatsVO;
import com.example.mystore.entity.vo.RecentOrderVO;
import com.example.mystore.mapper.AddressMapper;
import com.example.mystore.mapper.LogisticsMapper;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.mapper.PaymentRecordMapper;
import com.example.mystore.service.OrderQueryService;
import com.example.mystore.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单查询（读模型）实现：只组装展示数据，不写库、不触发外部调用。
 */
@Service
@RequiredArgsConstructor
public class OrderQueryServiceImpl implements OrderQueryService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final AddressMapper addressMapper;
    private final LogisticsMapper logisticsMapper;
    private final PaymentRecordMapper paymentRecordMapper;

    @Value("${wechat.pay.mch-id:}")
    private String mchId;

    @Override
    public Page<OrderVO> getOrderPage(Long userId, Integer status, Integer page, Integer limit) {
        Page<Order> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNull(Order::getUserDeleteTime).or().eq(Order::getUserDeleteTime, 0));
        if (userId != null) {
            wrapper.eq(Order::getUserId, userId);
        }

        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> orderPage = orderMapper.selectPage(pageParam, wrapper);

        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        List<OrderVO> voList = new ArrayList<>();
        for (Order order : orderPage.getRecords()) {
            voList.add(getOrderView(order.getId(), userId));
        }
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public OrderVO getOrderView(Long orderId, Long userId) {
        return buildOrderVO(orderMapper.selectById(orderId), userId, null);
    }

    @Override
    public Page<OrderVO> getAdminOrderPage(Long userId, Integer status, String orderNo, String startDate, String endDate,
                                           Integer page, Integer limit) {
        Page<Order> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNull(Order::getAdminDeleteTime).or().eq(Order::getAdminDeleteTime, 0));
        if (userId != null) {
            wrapper.eq(Order::getUserId, userId);
        }

        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        // M9: 管理端搜索参数补齐 —— orderNo 精确匹配, 日期按 createTime 区间(含 endDate 当天)
        if (StringUtils.hasText(orderNo)) {
            wrapper.eq(Order::getOrderNo, orderNo.trim());
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(Order::getCreateTime, parseDate(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.lt(Order::getCreateTime, parseDate(endDate).plusDays(1));
        }
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> orderPage = orderMapper.selectPage(pageParam, wrapper);

        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        List<OrderVO> voList = new ArrayList<>();
        Map<Long, PaymentRecord> latestPayments = loadLatestPaymentRecords(orderPage.getRecords());
        for (Order order : orderPage.getRecords()) {
            voList.add(buildOrderVO(order, null, latestPayments));
        }
        voPage.setRecords(voList);
        return voPage;
    }

    private Map<Long, PaymentRecord> loadLatestPaymentRecords(List<Order> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        List<PaymentRecord> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .in(PaymentRecord::getOrderId, orderIds)
                        .orderByDesc(PaymentRecord::getCreateTime)
                        .orderByDesc(PaymentRecord::getId));
        Map<Long, PaymentRecord> latestByOrderId = new HashMap<>();
        for (PaymentRecord record : records) {
            latestByOrderId.putIfAbsent(record.getOrderId(), record);
        }
        return latestByOrderId;
    }

    @Override
    public OrderVO getOrderByOrderNo(String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return getOrderView(order.getId(), order.getUserId());
    }

    @Override
    public Map<String, Object> getOrderStats() {
        Map<String, Object> stats = new HashMap<>();

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, StatusConstants.ORDER_PENDING_PAYMENT);
        long pendingPay = orderMapper.selectCount(wrapper);

        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT);
        long pendingShip = orderMapper.selectCount(wrapper);

        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, StatusConstants.ORDER_SHIPPED);
        long shipped = orderMapper.selectCount(wrapper);

        // 待管理员审核的退款申请数, 管理后台据此显示红点/快捷筛选
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, StatusConstants.ORDER_REFUND_REQUESTED);
        long refundRequested = orderMapper.selectCount(wrapper);

        // 退款失败待处理数, 管理后台据此显示提示条/快捷筛选/重试入口
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, StatusConstants.ORDER_REFUND_FAILED);
        long refundFailed = orderMapper.selectCount(wrapper);

        stats.put("pendingPay", pendingPay);
        stats.put("pendingShip", pendingShip);
        stats.put("shipped", shipped);
        stats.put("refundRequested", refundRequested);
        stats.put("refundFailed", refundFailed);

        return stats;
    }

    @Override
    public DashboardStatsVO getDashboardStats() {
        DashboardStatsVO stats = new DashboardStatsVO();

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        long todayOrders = nvl(orderMapper.countTodayPaidOrders(todayStart));
        stats.setTodayOrders(todayOrders);
        stats.setTodaySales(orderMapper.sumTodaySales(todayStart));

        LambdaQueryWrapper<Order> shipWrapper = new LambdaQueryWrapper<>();
        shipWrapper.eq(Order::getStatus, StatusConstants.ORDER_PENDING_SHIPMENT);
        stats.setPendingShip(orderMapper.selectCount(shipWrapper));

        // M11: 涨幅 = 今日 vs 昨日, 口径与今日统计一致
        DashboardStatsVO.Trend trend = new DashboardStatsVO.Trend();
        trend.setOrdersTrend(DashboardStatsVO.percentTrend(todayOrders, nvl(orderMapper.countPaidOrdersBetween(yesterdayStart, todayStart))));
        trend.setSalesTrend(DashboardStatsVO.percentTrend(
                toCents(stats.getTodaySales()), toCents(orderMapper.sumSalesBetween(yesterdayStart, todayStart))));
        trend.setShipTrend(DashboardStatsVO.percentTrend(
                nvl(orderMapper.countShippedBetween(todayStart, todayStart.plusDays(1))),
                nvl(orderMapper.countShippedBetween(yesterdayStart, todayStart))));
        stats.setTrend(trend);

        return stats;
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private static long toCents(BigDecimal amount) {
        return amount == null ? 0L : MoneyUtil.yuanToCents(amount);
    }

    @Override
    public List<RecentOrderVO> getRecentOrders(Integer limit) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Order::getCreateTime);
        wrapper.last("LIMIT " + limit);
        List<Order> orders = orderMapper.selectList(wrapper);

        List<RecentOrderVO> result = new ArrayList<>();
        for (Order order : orders) {
            RecentOrderVO vo = new RecentOrderVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setUserId(order.getUserId());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            vo.setStatusText(getStatusText(order.getStatus()));
            vo.setCreateTime(order.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    @Override
    public OrderVO getAdminOrderById(Long orderId) {
        return buildOrderVO(orderMapper.selectById(orderId), null, null);
    }

    private OrderVO buildOrderVO(Order order, Long userId, Map<Long, PaymentRecord> latestPayments) {
        Address address = addressMapper.selectById(order.getAddressId());
        Logistics logistics = logisticsMapper.selectById(order.getLogisticsId());

        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, order.getId());
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);

        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(getStatusText(order.getStatus()));
        vo.setTotalAmount(order.getTotalAmount());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setLogisticsName(logistics != null ? logistics.getName() : null);
        vo.setLogisticsNo(order.getLogisticsNo());
        vo.setDeliveryType(order.getDeliveryType());
        vo.setDeliveryTypeText(getDeliveryTypeText(order.getDeliveryType()));
        vo.setReceiverName(address != null ? address.getConsignee() : null);
        vo.setReceiverPhone(address != null ? address.getPhone() : null);
        vo.setReceiverAddress(address != null ? address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail() : null);
        vo.setBuyerRemark(order.getBuyerRemark());
        vo.setCancelReason(order.getCancelReason());
        vo.setAdminRemark(order.getAdminRemark());
        vo.setPayTime(order.getPayTime());
        vo.setShipTime(order.getShipTime());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setItems(items.stream().map(item -> toOrderItemVO(item, userId == null)).toList());

        // 微信确认收货组件所需参数(商户单号=订单号; 交易单号支付成功后回填)
        PaymentRecord payRecord = latestPayments != null
                ? latestPayments.get(order.getId())
                : paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getOrderId, order.getId())
                        .orderByDesc(PaymentRecord::getCreateTime)
                        .orderByDesc(PaymentRecord::getId)
                        .last("LIMIT 1"));
        vo.setMerchantId(mchId);
        vo.setMerchantTradeNo(payRecord != null ? payRecord.getOutTradeNo() : null);
        vo.setTransactionId(payRecord != null ? payRecord.getTransactionId() : null);
        vo.setPaymentStatus(payRecord != null ? payRecord.getStatus() : null);
        vo.setPaymentStatusText(getPaymentStatusText(payRecord));

        return vo;
    }

    private String getPaymentStatusText(PaymentRecord record) {
        if (record == null) {
            return "未支付";
        }
        return switch (record.getStatus()) {
            case PaymentRecord.STATUS_PENDING -> "待支付";
            case PaymentRecord.STATUS_PAID -> "已支付";
            case PaymentRecord.STATUS_CLOSED -> "已关闭";
            case PaymentRecord.STATUS_REFUNDED -> "已退款";
            case PaymentRecord.STATUS_REFUNDING -> "退款中";
            default -> "未知";
        };
    }

    private OrderItemVO toOrderItemVO(OrderItem item, boolean includePricingSnapshot) {
        OrderItemVO vo = new OrderItemVO();
        vo.setId(item.getId());
        vo.setOrderId(item.getOrderId());
        vo.setSkuId(item.getSkuId());
        vo.setSpuId(item.getSpuId());
        vo.setProductName(item.getProductName());
        vo.setProductSpec(item.getProductSpec());
        vo.setProductImage(item.getProductImage());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        vo.setCreateTime(item.getCreateTime());
        if (includePricingSnapshot) {
            vo.setLevelId(item.getLevelId());
            vo.setLevelName(item.getLevelName());
            vo.setPricingRate(item.getPricingRate());
        }
        return vo;
    }

    private String getStatusText(Integer status) {
        return switch (status) {
            case StatusConstants.ORDER_PENDING_PAYMENT -> "待付款";
            case StatusConstants.ORDER_PENDING_SHIPMENT -> "待发货";
            case StatusConstants.ORDER_SHIPPED -> "已发货";
            case StatusConstants.ORDER_COMPLETED -> "已完成";
            case StatusConstants.ORDER_CANCELLED -> "已取消";
            case StatusConstants.ORDER_REFUNDING -> "退款中";
            case StatusConstants.ORDER_REFUNDED -> "已退款";
            case StatusConstants.ORDER_REFUND_FAILED -> "退款失败";
            case StatusConstants.ORDER_REFUND_REQUESTED -> "退款申请中";
            default -> "未知";
        };
    }

    private String getDeliveryTypeText(Integer deliveryType) {
        if (deliveryType == null) {
            return null;
        }
        return switch (deliveryType) {
            case StatusConstants.DELIVERY_TYPE_LOCAL -> "同城配送";
            case StatusConstants.DELIVERY_TYPE_PICKUP -> "用户自提";
            default -> "未知";
        };
    }

    /**
     * M9: 管理端订单搜索的日期参数解析, 统一取当天 00:00
     */
    private LocalDateTime parseDate(String date) {
        try {
            return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期格式应为 yyyy-MM-dd");
        }
    }
}
