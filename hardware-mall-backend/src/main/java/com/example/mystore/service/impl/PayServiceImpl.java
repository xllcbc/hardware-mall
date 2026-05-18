package com.example.mystore.service.impl;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Order;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.PayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayServiceImpl implements PayService {

    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public void mockPay(Long userId, Long orderId) {
        log.info("模拟支付开始, userId={}, orderId={}", userId, orderId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该订单");
        }
        if (order.getStatus() != StatusConstants.ORDER_PENDING_PAYMENT) {
            throw new RuntimeException("订单状态不允许支付");
        }

        order.setStatus(StatusConstants.ORDER_PENDING_SHIPMENT);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        log.info("模拟支付成功, orderId={}, orderNo={}", orderId, order.getOrderNo());
    }
}