package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.entity.db.Order;
import com.example.mystore.mapper.OrderMapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * 订单状态迁移助手（模块内部类型, 不暴露为公开接口）。
 *
 * <p>条件更新的 WHERE 只由调用方传入的合法前驱集合生成, 保证预检集合与 SQL 条件同源。
 * 目标状态之外的附加列通过 {@code additionalColumns} 追加。
 */
final class OrderStatusTransition {

    private OrderStatusTransition() {
    }

    /**
     * 按合法前驱集合执行一次单语句条件更新。
     *
     * @param orderMapper      订单 Mapper
     * @param orderId          订单 ID
     * @param allowedFrom      合法前驱状态集合, 同时用于生成 WHERE status IN (...)
     * @param targetStatus     目标状态; {@code null} 表示不改变状态（如用户删除只写删除时间）
     * @param additionalColumns 目标状态之外的附加列
     * @return 影响行数
     */
    static int apply(OrderMapper orderMapper,
                     Long orderId,
                     Collection<Integer> allowedFrom,
                     Integer targetStatus,
                     Consumer<LambdaUpdateWrapper<Order>> additionalColumns) {
        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, orderId)
                .in(Order::getStatus, allowedFrom)
                .set(Order::getUpdateTime, LocalDateTime.now());
        if (targetStatus != null) {
            wrapper.set(Order::getStatus, targetStatus);
        }
        if (additionalColumns != null) {
            additionalColumns.accept(wrapper);
        }
        return orderMapper.update(null, wrapper);
    }
}
