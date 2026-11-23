package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.WechatConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.event.StockCacheEvent;
import com.example.mystore.mapper.LogisticsMapper;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.OrderMapper;
import com.example.mystore.service.OrderLifecycleService;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.WechatOrderShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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

    /** 取消/自动取消: 仅待付款可取消 */
    static final Set<Integer> CANCEL_FROM = Set.of(StatusConstants.ORDER_PENDING_PAYMENT);

    /** 确认收货: 仅已发货可推进（自动确认收货复用同一前驱集合） */
    static final Set<Integer> CONFIRM_RECEIVE_FROM = Set.of(StatusConstants.ORDER_SHIPPED);

    /** 发货: 仅待发货可发货 */
    static final Set<Integer> SHIP_FROM = Set.of(StatusConstants.ORDER_PENDING_SHIPMENT);

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
    private final OrderItemMapper orderItemMapper;
    private final LogisticsMapper logisticsMapper;
    private final SkuService skuService;
    private final WechatOrderShippingService wechatOrderShippingService;
    private final DingTalkAlertService dingTalkAlertService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public void cancelOrder(Long userId, Long orderId, String reason) {
        Order order = requireOwnedOrder(userId, orderId);
        if (!CANCEL_FROM.contains(order.getStatus())) {
            throw new BusinessException("只能取消待付款的订单");
        }

        // 先还库存; CAS 未命中时抛异常, 由事务回滚已还库存
        List<OrderItem> items = selectOrderItems(orderId);
        restoreStock(items, false);

        // CAS 最终确认: 仅当状态仍为待付款时置为已取消, 防与并发支付回调(1→2)互相覆盖
        requireAffected(claimCancelled(orderId, reason));

        publishStockCacheEvent(items);
    }

    @Override
    @Transactional
    public boolean autoCancelOrder(Long orderId, String cancelReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在, orderId={}", orderId);
            return false;
        }
        // 幂等性检查：只有待付款状态的订单才需要取消
        if (!CANCEL_FROM.contains(order.getStatus())) {
            log.info("订单已处理，无需取消, orderId={}, status={}", orderId, order.getStatus());
            return false;
        }

        // 先还库存; CAS 未命中时抛异常触发事务回滚, 撤销上方已执行的恢复库存
        List<OrderItem> items = selectOrderItems(orderId);
        restoreStock(items, true);

        // CAS 最终确认：仅当状态仍为待付款时置为已取消，防与并发支付回调(1→2)互相覆盖
        int affected = claimCancelled(orderId, cancelReason);
        if (affected == 0) {
            // 定时任务方向的 CAS 未命中仍需回滚已还库存, 因此抛异常而非返回 false;
            // OrderCancelStaleJob 把 BusinessException 计为“跳过”, 失败语义与改造前一致
            log.info("自动取消跳过（CAS 未命中，订单状态已变更）, orderId={}", orderId);
            throw new BusinessException("订单状态已变更，跳过取消");
        }

        log.info("订单自动取消成功, orderId={}", orderId);
        publishStockCacheEvent(items);
        return true;
    }

    @Override
    public void confirmReceive(Long userId, Long orderId) {
        confirmReceiveLocally(userId, orderId);
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

    @Override
    public void shipOrder(Long orderId, Integer deliveryType, Long logisticsId) {
        Order order = requireOrder(orderId);
        if (!SHIP_FROM.contains(order.getStatus())) {
            throw new BusinessException("只能发货待发货的订单");
        }

        Long finalLogisticsId;
        String logisticsNo;
        if (deliveryType == StatusConstants.DELIVERY_TYPE_PICKUP) {
            // 用户自提: 无物流、无单号
            finalLogisticsId = null;
            logisticsNo = null;
        } else if (deliveryType == StatusConstants.DELIVERY_TYPE_LOCAL) {
            // 同城配送: 物流取自 admin 物流管理(仅校验存在且启用), 配送单号后端自动生成
            Logistics logistics = logisticsMapper.selectById(logisticsId);
            if (logistics == null || logistics.getStatus() != StatusConstants.LOGISTICS_STATUS_ENABLED) {
                throw new BusinessException("物流公司不存在或已停用");
            }
            finalLogisticsId = logisticsId;
            logisticsNo = generateDeliveryNo();
        } else {
            throw new BusinessException("不支持的发货方式");
        }

        // CAS 条件更新: 仅当状态仍为待发货时置为已发货, 防与并发申请退款(2→8)/直退(2→6)/重复发货互相覆盖
        int affected = OrderStatusTransition.apply(
                orderMapper, orderId, SHIP_FROM, StatusConstants.ORDER_SHIPPED,
                wrapper -> wrapper
                        .set(Order::getDeliveryType, deliveryType)
                        .set(Order::getLogisticsId, finalLogisticsId)
                        .set(Order::getLogisticsNo, logisticsNo)
                        .set(Order::getShipTime, LocalDateTime.now()));
        requireAffected(affected);

        // 发货成功后上报微信发货信息; 失败不影响发货结果, 由 WechatShippingRetryJob 兜底
        try {
            wechatOrderShippingService.uploadShippingInfo(orderId);
        } catch (Exception e) {
            log.warn("发货后微信上报失败, 待重试任务兜底, orderId={}: {}", orderId, e.getMessage());
        }
    }

    @Override
    public void confirmReceiveByWechat(Long userId, Long orderId) {
        Order order = requireOwnedOrder(userId, orderId);
        if (order.getStatus() == StatusConstants.ORDER_COMPLETED) {
            return; // 已完成, 幂等
        }
        if (!CONFIRM_RECEIVE_FROM.contains(order.getStatus())) {
            throw new BusinessException("当前订单状态不支持确认收货");
        }
        // 以微信 order_state 为准: 仅 确认收货/交易完成 才算数
        Integer state = wechatOrderShippingService.queryOrderState(orderId);
        if (state == null
                || (state != WechatConstants.OrderState.CONFIRMED
                && state != WechatConstants.OrderState.COMPLETED)) {
            throw new BusinessException("微信未确认收货，请稍后重试");
        }
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .set(Order::getWechatOrderState, state)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        // 复用同一确认收货命令, 避免公共方法自调用导致未来事务注解失效
        confirmReceiveLocally(userId, orderId);
    }

    /** 同城配送单号: 后端自动生成, 前缀 PS + 雪花ID */
    private String generateDeliveryNo() {
        return "PS" + IdWorker.getIdStr();
    }

    private List<OrderItem> selectOrderItems(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
    }

    /** 取消类命令共用的条件更新: 待付款 → 已取消, 记录原因与时间 */
    private int claimCancelled(Long orderId, String reason) {
        return OrderStatusTransition.apply(
                orderMapper, orderId, CANCEL_FROM, StatusConstants.ORDER_CANCELLED,
                wrapper -> wrapper
                        .set(Order::getCancelReason, reason)
                        .set(Order::getCancelTime, LocalDateTime.now()));
    }

    /**
     * 取消前还库存。
     *
     * @param logEach 是否逐项记录恢复日志（定时任务排障用）
     */
    private void restoreStock(List<OrderItem> items, boolean logEach) {
        for (OrderItem item : items) {
            skuService.restoreStock(item.getSkuId(), item.getQuantity());
            if (logEach) {
                log.info("恢复DB库存, orderId={}, skuId={}, quantity={}",
                        item.getOrderId(), item.getSkuId(), item.getQuantity());
            }
        }
    }

    /** 库存缓存事件在事务内发布, 监听器 AFTER_COMMIT 后执行, 缓存一致性行为不变 */
    private void publishStockCacheEvent(List<OrderItem> items) {
        List<Long> skuIds = items.stream().map(OrderItem::getSkuId).toList();
        applicationEventPublisher.publishEvent(new StockCacheEvent(skuIds));
    }

    /** 确认收货公共步骤: 所有权/前驱预检 + 单语句 3→4; 供用户确认与微信确认共用 */
    private void confirmReceiveLocally(Long userId, Long orderId) {
        Order order = requireOwnedOrder(userId, orderId);
        if (!CONFIRM_RECEIVE_FROM.contains(order.getStatus())) {
            throw new BusinessException("只能确认收货已发货的订单");
        }
        requireAffected(completeReceive(orderId));
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
