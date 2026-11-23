package com.example.mystore.service;

/**
 * 订单生命周期：订单写操作与状态迁移的唯一入口。
 *
 * <p>合法前驱集合集中声明, 条件更新的 WHERE 由同一集合生成; 用户命令失败抛业务异常,
 * 定时任务命令失败返回 false。多写用例自己持有事务, 单语句迁移明确无事务。
 */
public interface OrderLifecycleService {

    /**
     * 用户确认收货: 已发货(3) → 已完成(4)。前驱不合法或条件更新未命中时抛业务异常。
     */
    void confirmReceive(Long userId, Long orderId);

    /**
     * 定时任务自动确认收货: 已发货(3) → 已完成(4), 幂等。
     *
     * @return true-本次完成收货; false-订单不存在/已非已发货/CAS 未命中
     */
    boolean autoConfirmReceive(Long orderId);

    /**
     * 用户申请退款: 待发货(2)/已发货(3) → 退款申请中(8), 成功后通知管理员审核。
     * 前驱不合法或条件更新未命中时抛业务异常。
     */
    void applyRefund(Long userId, Long orderId, String reason);

    /**
     * 管理员拒绝退款申请: 退款申请中(8) → 按 shipTime 回退待发货(2) 或已发货(3)。
     */
    void rejectRefund(Long orderId, String rejectReason);

    /**
     * 用户删除订单: 已完成(4)/已取消(5) 才可删除, 只写用户删除时间, 不改状态。
     */
    void deleteOrder(Long userId, Long orderId);
}
