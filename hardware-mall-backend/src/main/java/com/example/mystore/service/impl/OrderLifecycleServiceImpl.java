package com.example.mystore.service.impl;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Order;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.OrderLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 订单生命周期实现: 所有状态迁移经 {@link OrderStatusTransition} 生成条件更新。
 *
 * <p>合法前驱集合只在此处声明, 预检与 WHERE 引用同一常量, 避免两处漂移。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderLifecycleServiceImpl implements OrderLifecycleService {

    /** 确认收货: 仅已发货可推进（自动确认收货复用同一前驱集合） */
    static final Set<Integer> CONFIRM_RECEIVE_FROM = Set.of(StatusConstants.ORDER_SHIPPED);

    /** 用户申请退款: 待发货或已发货可申请 */
    static final Set<Integer> APPLY_REFUND_FROM = Set.of(
            StatusConstants.ORDER_PENDING_SHIPMENT, StatusConstants.ORDER_SHIPPED);

    /** 管理员拒绝退款: 仅退款申请中可回退 */
    static final Set<Integer> REJECT_REFUND_FROM = Set.of(StatusConstants.ORDER_REFUND_REQUESTED);

    /** 用户删除: 仅已完成或已取消可删除 */
    static final Set<Integer> DELETE_FROM = Set.of(
            StatusConstants.ORDER_COMPLETED, StatusConstants.ORDER_CANCELLED);

    static final String STATUS_CHANGED_MESSAGE = "订单状态已变更，请刷新后重试";

    private final OrderMapper orderMapper;
    private final DingTalkAlertService dingTalkAlertService;

    @Override
    public void confirmReceive(Long userId, Long orderId) {
        Order order = requireOwnedOrder(userId, orderId);
        if (!CONFIRM_RECEIVE_FROM.contains(order.getStatus())) {
            throw new BusinessException("只能确认收货已发货的订单");
        }
        int affected = completeReceive(orderId);
        requireAffected(affected);
    }

    @Override
    public boolean autoConfirmReceive(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在, orderId={}", orderId);
            return false;
        }
        // 幂等性检查：只有已发货状态的订单才需要自动收货
        if (!CONFIRM_RECEIVE_FROM.contains(order.getStatus())) {
            log.info("订单已处理，无需自动收货, orderId={}, status={}", orderId, order.getStatus());
            return false;
        }
        int affected = completeReceive(orderId);
        if (affected > 0) {
            log.info("订单自动收货成功, orderId={}", orderId);
            return true;
        }
        log.info("自动收货跳过（CAS 未命中，订单状态已变更）, orderId={}", orderId);
        return false;
    }

    @Override
    public void applyRefund(Long userId, Long orderId, String reason) {
        Order order = requireOwnedOrder(userId, orderId);
        // 预检: 明显错状态直接报错 (CAS 才是权威并发守卫, 预检仅为友好提示)
        if (!APPLY_REFUND_FROM.contains(order.getStatus())) {
            throw new BusinessException("当前订单状态不支持申请退款");
        }

        // claim-first: 先 CAS 占位 8(退款申请中), 命中 0 行说明状态被并发改走, 中止
        // 单条 UPDATE 自动提交, 不加 @Transactional; 管理员审核通过后走退款入口调微信退款
        int affected = OrderStatusTransition.apply(
                orderMapper, orderId, APPLY_REFUND_FROM, StatusConstants.ORDER_REFUND_REQUESTED,
                wrapper -> wrapper
                        .set(Order::getCancelReason, reason)
                        .set(Order::getCancelTime, LocalDateTime.now()));
        requireAffected(affected);
        log.info("用户申请退款, orderId={}, orderNo={}, 等待管理员审核", orderId, order.getOrderNo());

        // 通知管理员审核(防抖 key 含订单 ID, 每笔申请都独立送达)
        dingTalkAlertService.notify("REFUND_REQUEST:" + orderId,
                "用户申请退款\n订单号: " + order.getOrderNo() + "\n申请原因: " + reason);
    }

    @Override
    public void rejectRefund(Long orderId, String rejectReason) {
        Order order = requireOrder(orderId);
        if (!REJECT_REFUND_FROM.contains(order.getStatus())) {
            throw new BusinessException("该订单不是待审核的退款申请");
        }

        // 回退目标: 申请前是什么状态就回什么状态 — 按是否已有发货时间判断, 无需额外字段
        int targetStatus = order.getShipTime() == null
                ? StatusConstants.ORDER_PENDING_SHIPMENT
                : StatusConstants.ORDER_SHIPPED;

        // CAS: 仅当仍为退款申请中时回退, 防与并发审核互相覆盖
        int affected = OrderStatusTransition.apply(
                orderMapper, orderId, REJECT_REFUND_FROM, targetStatus,
                wrapper -> wrapper
                        .set(Order::getAdminRemark, rejectReason)
                        .set(Order::getCancelTime, null));
        requireAffected(affected);
        log.info("管理员拒绝退款申请, orderId={}, 回退至状态{}, 原因: {}", orderId, targetStatus, rejectReason);
    }

    @Override
    public void deleteOrder(Long userId, Long orderId) {
        Order order = requireOwnedOrder(userId, orderId);
        if (!DELETE_FROM.contains(order.getStatus())) {
            throw new BusinessException("该订单不可删除");
        }
        // 目标状态不变: 只写用户删除时间, 但 WHERE 仍由同一前驱集合约束
        int affected = OrderStatusTransition.apply(
                orderMapper, orderId, DELETE_FROM, null,
                wrapper -> wrapper.set(Order::getUserDeleteTime, System.currentTimeMillis()));
        requireAffected(affected);
    }

    private int completeReceive(Long orderId) {
        return OrderStatusTransition.apply(
                orderMapper, orderId, CONFIRM_RECEIVE_FROM, StatusConstants.ORDER_COMPLETED,
                wrapper -> wrapper.set(Order::getReceiveTime, LocalDateTime.now()));
    }

    private Order requireOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    private Order requireOwnedOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    private void requireAffected(int affected) {
        if (affected == 0) {
            throw new BusinessException(STATUS_CHANGED_MESSAGE);
        }
    }
}
