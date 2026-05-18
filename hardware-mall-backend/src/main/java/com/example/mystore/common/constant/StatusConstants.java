package com.example.mystore.common.constant;

/**
 * 商城系统状态常量
 * 集中管理所有状态值，避免魔数散落各处
 */
public class StatusConstants {
    
    private StatusConstants() {
    }
    
    // ==================== 订单状态 ====================
    /** 待付款 */
    public static final int ORDER_PENDING_PAYMENT = 1;
    /** 待发货 */
    public static final int ORDER_PENDING_SHIPMENT = 2;
    /** 已发货 */
    public static final int ORDER_SHIPPED = 3;
    /** 已完成 */
    public static final int ORDER_COMPLETED = 4;
    /** 已取消 */
    public static final int ORDER_CANCELLED = 5;
    /** 退款中 */
    public static final int ORDER_REFUNDING = 6;
    /** 已退款 */
    public static final int ORDER_REFUNDED = 7;
    
    // ==================== 用户角色 ====================
    /** 普通用户 */
    public static final int USER_ROLE_REGULAR = 1;
    /** 管理员 */
    public static final int USER_ROLE_ADMIN = 2;
    
    // ==================== 用户状态 ====================
    /** 用户正常 */
    public static final int USER_STATUS_NORMAL = 1;
    /** 用户禁用 */
    public static final int USER_STATUS_DISABLED = 0;
    
    // ==================== 商品状态 ====================
    /** 商品上架 */
    public static final int PRODUCT_STATUS_ON_SHELF = 1;
    /** 商品下架 */
    public static final int PRODUCT_STATUS_OFF_SHELF = 0;
    
    // ==================== 分类状态 ====================
    /** 分类启用 */
    public static final int CATEGORY_STATUS_ENABLED = 1;
    /** 分类禁用 */
    public static final int CATEGORY_STATUS_DISABLED = 0;
    
    // ==================== 物流状态 ====================
    /** 物流启用 */
    public static final int LOGISTICS_STATUS_ENABLED = 1;
    /** 物流禁用 */
    public static final int LOGISTICS_STATUS_DISABLED = 0;
    
    // ==================== 地址相关 ====================
    /** 默认地址 */
    public static final int ADDRESS_DEFAULT = 1;
    /** 非默认地址 */
    public static final int ADDRESS_NOT_DEFAULT = 0;

    // ==================== MQ消息状态 ====================
    /** 待发送 */
    public static final int MQ_STATUS_PENDING = 0;
    /** 已发送 */
    public static final int MQ_STATUS_SENT = 1;
    /** 发送失败 */
    public static final int MQ_STATUS_FAILED = 2;
    /** 已消费 */
    public static final int MQ_STATUS_CONSUMED = 3;
    /** 发送中 */
    public static final int MQ_STATUS_SENDING = 4;
}
